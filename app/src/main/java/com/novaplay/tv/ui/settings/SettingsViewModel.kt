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
import com.novaplay.tv.data.prefs.AccentTheme
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.BackgroundSyncMode
import com.novaplay.tv.data.prefs.HomeLayout
import com.novaplay.tv.data.prefs.LastSyncSummary
import com.novaplay.tv.data.prefs.LiveFormat
import com.novaplay.tv.data.prefs.SubtitleBackground
import com.novaplay.tv.data.prefs.SubtitleColor
import com.novaplay.tv.data.prefs.SubtitleEdge
import com.novaplay.tv.data.prefs.SubtitleSize
import com.novaplay.tv.data.prefs.SubtitleStyle
import com.novaplay.tv.data.prefs.TimeFormatPreference
import com.novaplay.tv.data.prefs.UiModePreference
import com.novaplay.tv.data.repo.ActivationCheck
import com.novaplay.tv.data.repo.ActivationRepository
import com.novaplay.tv.data.repo.AppDiagnostics
import com.novaplay.tv.data.repo.AppDiagnosticsRepository
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.DebugManagedPolicyPreset
import com.novaplay.tv.data.repo.LicenseInfo
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.PlayerLicenseRepository
import com.novaplay.tv.ui.player.PlaybackMetrics
import com.novaplay.tv.data.repo.ManagedAccessRepository
import com.novaplay.tv.data.repo.ParentalControlsRepository
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.data.repo.SyncStatus
import com.novaplay.tv.data.repo.SyncTrigger
import com.novaplay.tv.data.repo.UpdateCheckState
import com.novaplay.tv.data.repo.UpdateRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Device identifiers shown in settings; MAC and key are filled in asynchronously from [DeviceIdentity]. */
data class DeviceInfo(
    val mac: String = "",
    val deviceKey: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
)

/**
 * Backs all settings screen variants. Exposes the DataStore-backed preferences
 * (UI mode, subtitle style, live format, background sync) as eagerly shared state
 * flows alongside sync status, managed access policy and device diagnostics, and
 * persists every change the moment it is made.
 */
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
    private val updateRepository: UpdateRepository,
    private val playerLicenseRepository: PlayerLicenseRepository,
    private val parentalRepository: ParentalControlsRepository,
    deviceIdentity: DeviceIdentity,
    playbackMetrics: PlaybackMetrics,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    /** Self-service player identity/trial state for the "This device" panel. */
    val license: StateFlow<LicenseInfo?> = playerLicenseRepository.license

    private val _updateState = MutableStateFlow(
        if (updateRepository.enabled) UpdateCheckState.Idle else UpdateCheckState.Disabled,
    )

    /** Sideload update-channel state for the "This device" panel. */
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    /** Runs one manifest check; re-entry while checking is ignored. */
    fun checkForUpdates() {
        if (_updateState.value == UpdateCheckState.Checking) return
        viewModelScope.launch {
            _updateState.value = UpdateCheckState.Checking
            _updateState.value = updateRepository.check()
        }
    }

    /** Time-to-picture of the most recent channel start, for Sync & health. */
    val lastZapMs: StateFlow<Long?> = playbackMetrics.lastZapMs

    /** Slowest channel start seen this session. */
    val worstZapMs: StateFlow<Long?> = playbackMetrics.worstZapMs

    val uiMode: StateFlow<UiModePreference> = prefs.uiMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiModePreference.AUTO)

    val homeLayout: StateFlow<HomeLayout> = prefs.homeLayout
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayout.HERO)

    val accentTheme: StateFlow<AccentTheme> = prefs.accentTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccentTheme.BRAND)

    val timeFormat: StateFlow<TimeFormatPreference> = prefs.timeFormat
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimeFormatPreference.AUTO)

    val subtitleStyle: StateFlow<SubtitleStyle> = prefs.subtitleStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleStyle())

    val liveFormat: StateFlow<LiveFormat> = prefs.liveFormat
        .stateIn(viewModelScope, SharingStarted.Eagerly, LiveFormat.AUTO)

    val playerGesturesEnabled: StateFlow<Boolean> = prefs.playerGesturesEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
        // Registers this installation on first run and refreshes the trial /
        // license state; a no-op on managed-only or portal-less builds.
        viewModelScope.launch { playerLicenseRepository.refresh() }
    }

    /** Persists the interface mode override (auto, touch or TV). */
    fun setUiMode(mode: UiModePreference) {
        viewModelScope.launch { prefs.setUiMode(mode) }
    }

    /** Persists the Home hub arrangement; Home restyles live. */
    fun setHomeLayout(layout: HomeLayout) {
        viewModelScope.launch { prefs.setHomeLayout(layout) }
    }

    /** Persists the accent choice; the entire app restyles live. */
    fun setAccentTheme(theme: AccentTheme) {
        viewModelScope.launch { prefs.setAccentTheme(theme) }
    }

    /** Persists the 12/24-hour choice; every clock and programme time updates live. */
    fun setTimeFormat(format: TimeFormatPreference) {
        viewModelScope.launch { prefs.setTimeFormat(format) }
    }

    /** Persists the subtitle text size; the preview and VOD playback pick it up immediately. */
    fun setSubtitleSize(size: SubtitleSize) = updateStyle { it.copy(size = size) }
    /** Persists the subtitle text color. */
    fun setSubtitleColor(color: SubtitleColor) = updateStyle { it.copy(color = color) }
    /** Persists the subtitle background style. */
    fun setSubtitleBackground(background: SubtitleBackground) = updateStyle { it.copy(background = background) }
    /** Persists the subtitle edge style (none, outline or drop shadow). */
    fun setSubtitleEdge(edge: SubtitleEdge) = updateStyle { it.copy(edge = edge) }

    // Applies a transform to the current subtitle style and persists the result to DataStore.
    private fun updateStyle(transform: (SubtitleStyle) -> SubtitleStyle) {
        viewModelScope.launch { prefs.setSubtitleStyle(transform(subtitleStyle.value)) }
    }

    /** Persists the preferred live stream format (auto, HLS or MPEG-TS). */
    fun setLiveFormat(format: LiveFormat) {
        viewModelScope.launch { prefs.setLiveFormat(format) }
    }

    /** Enables or disables the touch playback slide gestures. */
    fun setPlayerGesturesEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setPlayerGesturesEnabled(enabled) }
    }

    /**
     * Persists the background sync mode and reschedules (or cancels) the periodic
     * worker to match, then shows a transient confirmation message for 2.5 seconds.
     */
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

    /**
     * Clears Coil's memory and disk image caches on the IO dispatcher, refreshes the
     * storage diagnostics, and flags a "cleared" confirmation for 2.5 seconds.
     */
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

    private val _historyCleared = MutableStateFlow(false)
    val historyCleared: StateFlow<Boolean> = _historyCleared.asStateFlow()

    /**
     * Empties the Recently-viewed rows of the active playlist across Live,
     * Movies and Series. Bookmarks and resume positions stay.
     */
    fun clearViewingHistory() {
        viewModelScope.launch {
            contentRepository.getActivePlaylist()?.let {
                contentRepository.clearRecents(it.id)
            }
            _historyCleared.value = true
            delay(2_500)
            _historyCleared.value = false
        }
    }

    /**
     * Re-syncs the active playlist in the application scope so the sync survives
     * leaving the settings screen; no-op when no playlist is active.
     */
    fun resyncNow() {
        appScope.launch {
            contentRepository.getActivePlaylist()?.let {
                syncRepository.sync(it, SyncTrigger.FOREGROUND)
            }
            refreshDiagnosticsInternal()
        }
    }

    /** Re-captures the diagnostics snapshot (network, memory, storage, catalogue counts). */
    fun refreshDiagnostics() {
        viewModelScope.launch { refreshDiagnosticsInternal() }
    }

    // Suspending core of refreshDiagnostics, reused by callers already inside a coroutine.
    private suspend fun refreshDiagnosticsInternal() {
        _diagnostics.value = diagnosticsRepository.snapshot()
    }

    /**
     * Copies a privacy-safe support summary to the clipboard. The text is built by
     * [AppDiagnosticsRepository.supportText] and must never contain playlist URLs,
     * servers, credentials, tokens, MAC, device key or device ID.
     */
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

    /**
     * Re-checks portal activation to pull the latest managed access policy. In a
     * debug build this is also the deliberate action that exits a temporary policy
     * preview; background mock refreshes do not overwrite the preview silently.
     */
    fun refreshManagedAccess() {
        viewModelScope.launch {
            managedAccessRepository.clearDebugPreset()
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

    /** Applies a debug managed-policy preset for previewing states; ignored entirely on release builds. */
    fun setDebugManagedPolicy(preset: DebugManagedPolicyPreset) {
        if (BuildConfig.DEBUG) managedAccessRepository.setDebugPreset(preset)
    }

    // --- Parental controls ---

    /** Whether a parental PIN exists on this device. */
    val parentalPinConfigured: StateFlow<Boolean> = parentalRepository.pinConfigured
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Whether the parental session is currently unlocked. */
    val parentalSessionUnlocked: StateFlow<Boolean> = parentalRepository.sessionUnlocked

    /** How many categories are locked, across Live, Movies and Series. */
    val parentalLockedCount: StateFlow<Int> = parentalRepository.lockedKeys
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Checks the current PIN without unlocking (change-PIN flow, step one). */
    suspend fun checkParentalPin(pin: String): Boolean = parentalRepository.checkPin(pin)

    /** Sets or replaces the parental PIN; false for non-4-digit input. */
    suspend fun setParentalPin(pin: String): Boolean = parentalRepository.setPin(pin)

    /** Ends the unlocked parental session; locked categories hide again at once. */
    fun relockParental() = parentalRepository.relock()
}
