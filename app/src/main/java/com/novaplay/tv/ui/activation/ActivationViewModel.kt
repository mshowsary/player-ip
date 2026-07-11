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

enum class ActivationPhase {
    PREPARING,
    WAITING_FOR_APPROVAL,
    WAITING_FOR_PLAYLIST,
    APPROVED,
    DENIED,
    EXPIRED,
    ERROR,
}

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

    fun refreshCode() {
        viewModelScope.launch { createPairingSession(clearStoredSession = true) }
    }

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

    private fun friendlyPairingError(error: Throwable): String = when {
        error.message?.contains("HTTP 404", ignoreCase = true) == true ->
            "Secure pairing is not available on this portal yet. Try again later or add your own playlist."
        else -> "Could not create a pairing code. Check the connection and try again."
    }

    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1_000L

    private companion object {
        const val ASSIGNMENT_POLL_INTERVAL_MS = 15_000L
    }
}
