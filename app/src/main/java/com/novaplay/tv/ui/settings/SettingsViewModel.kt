package com.novaplay.tv.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.background.BackgroundSyncScheduler
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.BackgroundSyncMode
import com.novaplay.tv.data.prefs.LastSyncSummary
import com.novaplay.tv.data.prefs.LiveFormat
import com.novaplay.tv.data.prefs.SubtitleBackground
import com.novaplay.tv.data.prefs.SubtitleColor
import com.novaplay.tv.data.prefs.SubtitleEdge
import com.novaplay.tv.data.prefs.SubtitleSize
import com.novaplay.tv.data.prefs.SubtitleStyle
import com.novaplay.tv.data.prefs.UiModePreference
import com.novaplay.tv.data.repo.ActivationCheck
import com.novaplay.tv.data.repo.ActivationRepository
import com.novaplay.tv.data.repo.AppDiagnostics
import com.novaplay.tv.data.repo.AppDiagnosticsRepository
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.DebugManagedPolicyPreset
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedAccessRepository
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.data.repo.SyncStatus
import com.novaplay.tv.data.repo.SyncTrigger
import com.novaplay.tv.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DeviceInfo(
    val mac: String = "",
    val deviceKey: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val syncRepository: SyncRepository,
    private val contentRepository: ContentRepository,
    private val activationRepository: ActivationRepository,
    private val managedAccessRepository: ManagedAccessRepository,
    private val backgroundSyncScheduler: BackgroundSyncScheduler,
    private val diagnosticsRepository: AppDiagnosticsRepository,
    deviceIdentity: DeviceIdentity,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val uiMode: StateFlow<UiModePreference> = prefs.uiMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiModePreference.AUTO)

    val subtitleStyle: StateFlow<SubtitleStyle> = prefs.subtitleStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleStyle())

    val liveFormat: StateFlow<LiveFormat> = prefs.liveFormat
        .stateIn(viewModelScope, SharingStarted.Eagerly, LiveFormat.AUTO)

    val backgroundSyncMode: StateFlow<BackgroundSyncMode> = prefs.backgroundSyncMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, BackgroundSyncMode.DAILY)

    val lastSyncSummary: StateFlow<LastSyncSummary> = prefs.lastSyncSummary
        .stateIn(viewModelScope, SharingStarted.Eagerly, LastSyncSummary())

    val syncStatus: StateFlow<SyncStatus> = syncRepository.status
    val managedAccess: StateFlow<ManagedAccessPolicy> = managedAccessRepository.policy

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _diagnostics = MutableStateFlow(AppDiagnostics())
    val diagnostics: StateFlow<AppDiagnostics> = _diagnostics.asStateFlow()

    private val _cacheCleared = MutableStateFlow(false)
    val cacheCleared: StateFlow<Boolean> = _cacheCleared.asStateFlow()

    private val _diagnosticsMessage = MutableStateFlow<String?>(null)
    val diagnosticsMessage: StateFlow<String?> = _diagnosticsMessage.asStateFlow()

    private val _managedRefreshMessage = MutableStateFlow<String?>(null)
    val managedRefreshMessage: StateFlow<String?> = _managedRefreshMessage.asStateFlow()

    init {
        viewModelScope.launch {
            val identity = deviceIdentity.get()
            _deviceInfo.value = DeviceInfo(mac = identity.mac, deviceKey = identity.deviceKey)
        }
        refreshDiagnostics()
    }

    fun setUiMode(mode: UiModePreference) {
        viewModelScope.launch { prefs.setUiMode(mode) }
    }

    fun setSubtitleSize(size: SubtitleSize) = updateStyle { it.copy(size = size) }
    fun setSubtitleColor(color: SubtitleColor) = updateStyle { it.copy(color = color) }
    fun setSubtitleBackground(background: SubtitleBackground) = updateStyle { it.copy(background = background) }
    fun setSubtitleEdge(edge: SubtitleEdge) = updateStyle { it.copy(edge = edge) }

    private fun updateStyle(transform: (SubtitleStyle) -> SubtitleStyle) {
        viewModelScope.launch { prefs.setSubtitleStyle(transform(subtitleStyle.value)) }
    }

    fun setLiveFormat(format: LiveFormat) {
        viewModelScope.launch { prefs.setLiveFormat(format) }
    }

    fun setBackgroundSyncMode(mode: BackgroundSyncMode) {
        viewModelScope.launch {
            prefs.setBackgroundSyncMode(mode)
            backgroundSyncScheduler.apply(mode)
            _diagnosticsMessage.value = if (mode == BackgroundSyncMode.OFF) {
                "Automatic refresh disabled"
            } else {
                "Automatic refresh set to ${mode.label.lowercase()}"
            }
            delay(2_500)
            _diagnosticsMessage.value = null
        }
    }

    fun clearImageCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
            }
            _cacheCleared.value = true
            refreshDiagnostics()
            delay(2_500)
            _cacheCleared.value = false
        }
    }

    fun resyncNow() {
        appScope.launch {
            contentRepository.getActivePlaylist()?.let {
                syncRepository.sync(it, SyncTrigger.FOREGROUND)
            }
            refreshDiagnosticsInternal()
        }
    }

    fun refreshDiagnostics() {
        viewModelScope.launch { refreshDiagnosticsInternal() }
    }

    private suspend fun refreshDiagnosticsInternal() {
        _diagnostics.value = diagnosticsRepository.snapshot()
    }

    fun copySupportDiagnostics() {
        viewModelScope.launch {
            val current = diagnosticsRepository.snapshot().also { _diagnostics.value = it }
            val text = diagnosticsRepository.supportText(
                diagnostics = current,
                backgroundMode = backgroundSyncMode.value,
                lastSync = lastSyncSummary.value,
            )
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                ClipData.newPlainText("NovaPlay support diagnostics", text),
            )
            _diagnosticsMessage.value = "Support diagnostics copied"
            delay(2_500)
            _diagnosticsMessage.value = null
        }
    }

    fun refreshManagedAccess() {
        viewModelScope.launch {
            _managedRefreshMessage.value = "Checking managed access…"
            _managedRefreshMessage.value = when (val result = activationRepository.checkAndAttach()) {
                is ActivationCheck.Activated -> "Managed access refreshed"
                ActivationCheck.NotRegistered -> "Connected, but no managed playlist is assigned"
                ActivationCheck.KeyMismatch -> "The portal session is no longer valid"
                is ActivationCheck.Failure -> "Could not refresh managed access"
            }
            refreshDiagnosticsInternal()
            delay(2_800)
            _managedRefreshMessage.value = null
        }
    }

    fun setDebugManagedPolicy(preset: DebugManagedPolicyPreset) {
        if (BuildConfig.DEBUG) managedAccessRepository.setDebugPreset(preset)
    }
}
