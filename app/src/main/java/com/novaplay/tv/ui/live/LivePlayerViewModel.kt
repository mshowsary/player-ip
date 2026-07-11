package com.novaplay.tv.ui.live

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.SubtitleStyle
import com.novaplay.tv.data.repo.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LivePlayerUiState(
    val channel: LiveChannel? = null,
    val categoryName: String? = null,
    val overlayVisible: Boolean = false,
    val buffering: Boolean = true,
    val error: String? = null,
)

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class LivePlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
    prefs: AppPreferences,
) : ViewModel() {

    private val initialChannelId: Long = checkNotNull(savedStateHandle["channelId"])

    // -1 encodes "All channels" — zapping then moves across the full list.
    private val categoryId: Long? =
        savedStateHandle.get<Long>("categoryId")?.takeIf { it >= 0 }

    private val _uiState = MutableStateFlow(LivePlayerUiState())
    val uiState: StateFlow<LivePlayerUiState> = _uiState.asStateFlow()

    val subtitleStyle: StateFlow<SubtitleStyle> = prefs.subtitleStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleStyle())

    private val liveFormat = prefs.liveFormat

    // Low-latency load control: start playback after ~1.2 s of buffer so
    // zapping feels instant on live streams.
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 30_000, 1_200, 2_400)
                .build(),
        )
        .build()

    private var candidateUrls: List<String> = emptyList()
    private var attempt = 0
    private var overlayJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Live fallback: retry once with the alternate container
                // (HLS ↔ TS) before surfacing an error overlay.
                if (attempt + 1 < candidateUrls.size) {
                    attempt++
                    prepareCurrent()
                } else {
                    _uiState.update { it.copy(error = playbackErrorMessage(error), buffering = false) }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _uiState.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
            }
        })

        viewModelScope.launch {
            contentRepository.channelById(initialChannelId)?.let { playChannel(it) }
        }
    }

    fun zapNext() = zap { channel -> contentRepository.nextChannel(channel, categoryId) }

    fun zapPrev() = zap { channel -> contentRepository.prevChannel(channel, categoryId) }

    private fun zap(pick: suspend (LiveChannel) -> LiveChannel?) {
        val current = _uiState.value.channel ?: return
        viewModelScope.launch {
            pick(current)?.let { next ->
                if (next.id != current.id) playChannel(next)
            }
        }
    }

    fun toggleOverlay() {
        if (_uiState.value.overlayVisible) {
            overlayJob?.cancel()
            _uiState.update { it.copy(overlayVisible = false) }
        } else {
            showOverlay()
        }
    }

    fun retry() {
        _uiState.update { it.copy(error = null) }
        attempt = 0
        prepareCurrent()
    }

    // Stop on activity stop (kills the stream), re-prepare when it returns.
    fun onLifecycleStop() {
        player.stop()
    }

    fun onLifecycleStart() {
        if (_uiState.value.channel != null && _uiState.value.error == null) {
            prepareCurrent()
        }
    }

    private suspend fun playChannel(channel: LiveChannel) {
        contentRepository.recordRecentView(
            playlistId = channel.playlistId,
            mediaType = com.novaplay.tv.data.db.Bookmark.MEDIA_LIVE,
            remoteId = channel.streamId,
        )
        candidateUrls = contentRepository.liveStreamUrls(channel, liveFormat.first())
        attempt = 0
        val categoryName = channel.categoryId?.let { contentRepository.liveCategoryById(it)?.name }
        _uiState.update {
            it.copy(channel = channel, categoryName = categoryName, error = null, buffering = true)
        }
        showOverlay()
        prepareCurrent()
    }

    private fun prepareCurrent() {
        val url = candidateUrls.getOrNull(attempt) ?: run {
            _uiState.update { it.copy(error = "No stream available for this channel") }
            return
        }
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (url.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    private fun showOverlay() {
        overlayJob?.cancel()
        _uiState.update { it.copy(overlayVisible = true) }
        overlayJob = viewModelScope.launch {
            delay(OVERLAY_HIDE_MS)
            _uiState.update { it.copy(overlayVisible = false) }
        }
    }

    private fun playbackErrorMessage(error: PlaybackException): String =
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> "Network error — check your connection"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Stream unavailable from provider"
            else -> "Playback failed"
        }

    override fun onCleared() {
        overlayJob?.cancel()
        player.release()
    }

    private companion object {
        const val OVERLAY_HIDE_MS = 4_000L
    }
}
