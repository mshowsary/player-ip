package com.novaplay.tv.ui.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.data.repo.ActivationCheck
import com.novaplay.tv.data.repo.ActivationRepository
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.PortalPairingPoll
import com.novaplay.tv.data.repo.PortalPairingRepository
import com.novaplay.tv.data.repo.PortalPairingSession
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Lifecycle of a portal pairing attempt, from code creation to a terminal outcome. */
enum class ActivationPhase {
    PREPARING,
    WAITING_FOR_APPROVAL,
    WAITING_FOR_PLAYLIST,
    APPROVED,
    DENIED,
    EXPIRED,
    ERROR,
}

/**
 * Everything ActivationScreen renders. Only the short-lived user code is
 * exposed here — the session secret stays inside the pairing repository and
 * is never shown or logged.
 */
data class ActivationUiState(
    val deviceId: String = "",
    val supportId: String = "",
    val userCode: String = "",
    val verificationUri: String = "",
    val expiresAtEpochSec: Long = 0L,
    val secondsRemaining: Long = 0L,
    val phase: ActivationPhase = ActivationPhase.PREPARING,
    val checking: Boolean = false,
    val error: String? = null,
)

/**
 * Drives portal device pairing: creates a session, exposes the user code,
 * polls for approval on the portal's advertised interval, then waits for a
 * playlist assignment before emitting [activated]. Falls back to the legacy
 * activation check when the pairing endpoint is unavailable.
 */
@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val deviceIdentity: DeviceIdentity,
    private val pairingRepository: PortalPairingRepository,
    private val activationRepository: ActivationRepository,
    private val contentRepository: ContentRepository,
    private val syncRepository: SyncRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    private val _activated = MutableSharedFlow<Unit>(replay = 1)
    val activated: SharedFlow<Unit> = _activated.asSharedFlow()

    private val pollMutex = Mutex()
    private var currentSession: PortalPairingSession? = null
    private var pairingJob: Job? = null
    private var countdownJob: Job? = null
    private var assignmentJob: Job? = null

    init {
        viewModelScope.launch {
            val identity = deviceIdentity.get()
            _uiState.value = _uiState.value.copy(
                deviceId = identity.deviceId,
                supportId = identity.deviceId.takeLast(8).uppercase(),
            )
            createPairingSession(clearStoredSession = false)
        }
    }

    /**
     * Manual "check" from the UI: polls for approval or playlist assignment
     * depending on phase; from terminal states it simply mints a new code.
     */
    fun checkNow() {
        when (_uiState.value.phase) {
            ActivationPhase.WAITING_FOR_PLAYLIST -> checkAssignedPlaylists(manual = true)
            ActivationPhase.WAITING_FOR_APPROVAL -> {
                val session = currentSession ?: return
                viewModelScope.launch { pollSession(session, manual = true) }
            }
            ActivationPhase.EXPIRED,
            ActivationPhase.DENIED,
            ActivationPhase.ERROR,
            -> refreshCode()
            else -> Unit
        }
    }

    /**
     * Identity-screen action: fetches playlists for this device's MAC + key
     * right now, regardless of any pairing-session phase. This is the "I
     * added my playlist on the website - check now" button.
     */
    fun checkPlaylistsNow() {
        checkAssignedPlaylists(manual = true)
    }

    /** Discards the current session (including the stored one) and creates a fresh pairing code. */
    fun refreshCode() {
        viewModelScope.launch { createPairingSession(clearStoredSession = true) }
    }

    /**
     * Cancels all in-flight jobs, requests a new pairing session, and on
     * success starts the expiry countdown and approval polling.
     */
    private suspend fun createPairingSession(clearStoredSession: Boolean) {
        pairingJob?.cancel()
        countdownJob?.cancel()
        assignmentJob?.cancel()
        currentSession = null
        if (clearStoredSession) pairingRepository.disconnect()

        _uiState.value = _uiState.value.copy(
            userCode = "",
            verificationUri = "",
            expiresAtEpochSec = 0L,
            secondsRemaining = 0L,
            phase = ActivationPhase.PREPARING,
            checking = true,
            error = null,
        )

        pairingRepository.createSession().fold(
            onSuccess = { session ->
                currentSession = session
                _uiState.value = _uiState.value.copy(
                    userCode = session.userCode,
                    verificationUri = session.verificationUri,
                    expiresAtEpochSec = session.expiresAtEpochSec,
                    secondsRemaining = (session.expiresAtEpochSec - nowEpochSec()).coerceAtLeast(0L),
                    phase = ActivationPhase.WAITING_FOR_APPROVAL,
                    checking = false,
                    error = null,
                )
                startCountdown(session)
                startApprovalPolling(session)
            },
            onFailure = { error ->
                // During rollout, an already registered legacy portal device can
                // still enter the app even when the new pairing endpoint is not
                // available yet. New devices receive a clean retry state.
                when (activationRepository.checkAndAttach()) {
                    is ActivationCheck.Activated -> completeActivation()
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            phase = ActivationPhase.ERROR,
                            checking = false,
                            error = friendlyPairingError(error),
                        )
                    }
                }
            },
        )
    }

    // Ticks secondsRemaining every second and flips the phase to EXPIRED if the
    // code runs out while still waiting for approval.
    private fun startCountdown(session: PortalPairingSession) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (isActive && currentSession?.sessionId == session.sessionId) {
                val remaining = (session.expiresAtEpochSec - nowEpochSec()).coerceAtLeast(0L)
                _uiState.value = _uiState.value.copy(secondsRemaining = remaining)
                if (remaining == 0L && _uiState.value.phase == ActivationPhase.WAITING_FOR_APPROVAL) {
                    currentSession = null
                    pairingJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        phase = ActivationPhase.EXPIRED,
                        checking = false,
                        error = "This pairing code expired. Create a new code to continue.",
                    )
                    break
                }
                delay(1_000L)
            }
        }
    }

    // Polls the portal for approval, following each poll's suggested interval,
    // until pollSession reports a terminal result (returns null).
    private fun startApprovalPolling(session: PortalPairingSession) {
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            var nextDelaySeconds = session.intervalSeconds
            while (isActive && currentSession?.sessionId == session.sessionId) {
                delay(nextDelaySeconds * 1_000L)
                nextDelaySeconds = pollSession(session, manual = false) ?: break
            }
        }
    }

    /** Returns the next automatic poll interval, or null when polling is terminal. */
    private suspend fun pollSession(session: PortalPairingSession, manual: Boolean): Int? =
        pollMutex.withLock {
            if (currentSession?.sessionId != session.sessionId) return@withLock null
            if (_uiState.value.phase != ActivationPhase.WAITING_FOR_APPROVAL) return@withLock null

            _uiState.value = _uiState.value.copy(checking = true, error = null)
            when (val result = pairingRepository.poll(session)) {
                is PortalPairingPoll.Pending -> {
                    _uiState.value = _uiState.value.copy(checking = false)
                    result.retryAfterSeconds
                }
                is PortalPairingPoll.SlowDown -> {
                    _uiState.value = _uiState.value.copy(
                        checking = false,
                        error = if (manual) "The portal asked the device to wait a little longer." else null,
                    )
                    result.retryAfterSeconds
                }
                is PortalPairingPoll.Approved -> {
                    countdownJob?.cancel()
                    currentSession = null
                    val attached = activationRepository.attachManagedPlaylists(result.playlists)
                    if (attached > 0) {
                        completeActivation()
                    } else {
                        checkAssignedPlaylists(manual = false)
                    }
                    null
                }
                PortalPairingPoll.Denied -> {
                    currentSession = null
                    countdownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        phase = ActivationPhase.DENIED,
                        checking = false,
                        error = "This connection request was declined. Create a new code to try again.",
                    )
                    null
                }
                PortalPairingPoll.Expired -> {
                    currentSession = null
                    countdownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        phase = ActivationPhase.EXPIRED,
                        checking = false,
                        error = "This pairing code expired. Create a new code to continue.",
                    )
                    null
                }
                is PortalPairingPoll.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        checking = false,
                        error = if (manual) {
                            "Could not check the portal right now. The device will keep trying."
                        } else {
                            null
                        },
                    )
                    session.intervalSeconds
                }
            }
        }

    // After approval but before any playlist exists: re-checks the portal every
    // 15 s until one is assigned. A key mismatch invalidates the whole session
    // and sends the user back to a new pairing code.
    private fun checkAssignedPlaylists(manual: Boolean) {
        assignmentJob?.cancel()
        assignmentJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                phase = ActivationPhase.WAITING_FOR_PLAYLIST,
                checking = true,
                error = null,
            )
            while (isActive && _uiState.value.phase == ActivationPhase.WAITING_FOR_PLAYLIST) {
                when (val result = activationRepository.checkAndAttach()) {
                    is ActivationCheck.Activated -> {
                        completeActivation()
                        return@launch
                    }
                    ActivationCheck.NotRegistered -> {
                        _uiState.value = _uiState.value.copy(
                            checking = false,
                            error = if (manual) {
                                "The device is connected, but no playlist has been assigned yet."
                            } else {
                                null
                            },
                        )
                    }
                    ActivationCheck.KeyMismatch -> {
                        pairingRepository.disconnect()
                        _uiState.value = _uiState.value.copy(
                            phase = ActivationPhase.ERROR,
                            checking = false,
                            error = "The portal session is no longer valid. Create a new pairing code.",
                        )
                        return@launch
                    }
                    is ActivationCheck.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            checking = false,
                            error = if (manual) {
                                "The portal could not be reached. The device will try again automatically."
                            } else {
                                null
                            },
                        )
                    }
                }
                delay(ASSIGNMENT_POLL_INTERVAL_MS)
                _uiState.value = _uiState.value.copy(checking = true)
            }
        }
    }

    // Terminal success: stops all polling, kicks off the first catalog sync on
    // the app scope (so it outlives this screen), and tells the UI to navigate.
    private fun completeActivation() {
        pairingJob?.cancel()
        countdownJob?.cancel()
        assignmentJob?.cancel()
        _uiState.value = _uiState.value.copy(
            phase = ActivationPhase.APPROVED,
            checking = false,
            error = null,
        )
        appScope.launch {
            contentRepository.getActivePlaylist()?.let { syncRepository.sync(it) }
        }
        _activated.tryEmit(Unit)
    }

    // Maps transport failures to user-facing copy without leaking any details.
    private fun friendlyPairingError(error: Throwable): String = when {
        error.message?.contains("HTTP 404", ignoreCase = true) == true ->
            "Secure pairing is not available on this portal yet. Try again later or add your own playlist."
        else -> "Could not create a pairing code. Check the connection and try again."
    }

    // Current wall-clock time in epoch seconds, matching the portal's expiry unit.
    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1_000L

    private companion object {
        const val ASSIGNMENT_POLL_INTERVAL_MS = 15_000L
    }
}
