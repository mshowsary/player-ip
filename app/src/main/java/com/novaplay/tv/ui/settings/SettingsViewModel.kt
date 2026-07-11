package com.novaplay.tv.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.LiveFormat
import com.novaplay.tv.data.prefs.SubtitleBackground
import com.novaplay.tv.data.prefs.SubtitleColor
import com.novaplay.tv.data.prefs.SubtitleEdge
import com.novaplay.tv.data.prefs.SubtitleSize
import com.novaplay.tv.data.prefs.SubtitleStyle
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.data.repo.SyncStatus
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
    deviceIdentity: DeviceIdentity,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val subtitleStyle: StateFlow<SubtitleStyle> = prefs.subtitleStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleStyle())

    val liveFormat: StateFlow<LiveFormat> = prefs.liveFormat
        .stateIn(viewModelScope, SharingStarted.Eagerly, LiveFormat.AUTO)

    val syncStatus: StateFlow<SyncStatus> = syncRepository.status

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _cacheCleared = MutableStateFlow(false)
    val cacheCleared: StateFlow<Boolean> = _cacheCleared.asStateFlow()

    init {
        viewModelScope.launch {
            val identity = deviceIdentity.get()
            _deviceInfo.value = DeviceInfo(mac = identity.mac, deviceKey = identity.deviceKey)
        }
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

    fun clearImageCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
            }
            _cacheCleared.value = true
            delay(2_500)
            _cacheCleared.value = false
        }
    }

    fun resyncNow() {
        appScope.launch {
            contentRepository.getActivePlaylist()?.let { syncRepository.sync(it) }
        }
    }
}
