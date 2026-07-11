package com.novaplay.tv.ui.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.data.repo.ActivationCheck
import com.novaplay.tv.data.repo.ActivationRepository
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivationUiState(
    val mac: String = "",
    val deviceKey: String = "",
    val checking: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val deviceIdentity: DeviceIdentity,
    private val activationRepository: ActivationRepository,
    private val contentRepository: ContentRepository,
    private val syncRepository: SyncRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    private val _activated = MutableSharedFlow<Unit>(replay = 1)
    val activated: SharedFlow<Unit> = _activated.asSharedFlow()

    init {
        viewModelScope.launch {
            val identity = deviceIdentity.get()
            _uiState.value = _uiState.value.copy(mac = identity.mac, deviceKey = identity.deviceKey)
        }
        // Auto-poll while this screen is visible.
        viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                check(silent = true)
            }
        }
    }

    fun checkNow() {
        viewModelScope.launch { check(silent = false) }
    }

    private suspend fun check(silent: Boolean) {
        if (_uiState.value.checking) return
        _uiState.value = _uiState.value.copy(checking = !silent, error = null)
        when (val result = activationRepository.checkAndAttach()) {
            is ActivationCheck.Activated -> {
                // First sync runs in a scope that survives leaving this screen.
                appScope.launch {
                    contentRepository.getActivePlaylist()?.let { syncRepository.sync(it) }
                }
                _activated.tryEmit(Unit)
            }
            ActivationCheck.NotRegistered -> {
                _uiState.value = _uiState.value.copy(checking = false)
            }
            ActivationCheck.KeyMismatch -> {
                _uiState.value = _uiState.value.copy(
                    checking = false,
                    error = "Device key mismatch. Remove this device on the portal and register it again.",
                )
            }
            is ActivationCheck.Failure -> {
                _uiState.value = _uiState.value.copy(
                    checking = false,
                    error = if (silent) null else "Could not reach the portal: ${result.message}",
                )
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 15_000L
    }
}
