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
import com.novaplay.tv.ui.player.PlaybackRetryDecision
import com.novaplay.tv.ui.player.PlaybackRetryPolicy
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class LivePlayerUiState(
    val channel: LiveChannel? = null,
    val categoryName: String? = null,
    val overlayVisible: Boolean = false,
    val buffering: Boolean = true,
    val reconnecting: Boolean = false,
    val reconnectMessage: String? = null,
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
    private var sourceIndex = 0
    private var failuresOnCurrentSource = 0
    private var playbackGeneration = 0L
    private var foregroundActive = true

    private var overlayJob: Job? = null
    private var retryJob: Job? = null
    private var stallWatchdogJob: Job? = null
    private var stabilityJob: Job? = null
    private val zapMutex = Mutex()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                recoverFromFailure(playbackErrorMessage(error))
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _uiState.update { it.copy(buffering = true) }
                        armStallWatchdog(playbackGeneration)
                    }
                    Player.STATE_READY -> {
                        stallWatchdogJob?.cancel()
                        retryJob?.cancel()
                        _uiState.update {
                            it.copy(
                                buffering = false,
                                reconnecting = false,
                                reconnectMessage = null,
                                error = null,
                            )
                        }
                        // Do not reset retry history immediately. Streams that
                        // repeatedly reach READY for only a fraction of a second
                        // must still eventually fail instead of looping forever.
                        stabilityJob?.cancel()
                        val generation = playbackGeneration
                        stabilityJob = viewModelScope.launch {
                            delay(STABLE_PLAYBACK_RESET_MS)
                            if (
                                foregroundActive &&
                                playbackGeneration == generation &&
                                player.playbackState == Player.STATE_READY
                            ) {
                                failuresOnCurrentSource = 0
                            }
                        }
                    }
                    Player.STATE_ENDED -> {
                        recoverFromFailure("The live stream ended unexpectedly")
                    }
                    else -> Unit
                }
            }
        })

        viewModelScope.launch {
            contentRepository.channelById(initialChannelId)?.let { playChannel(it) }
        }
    }

    fun zapNext() = zap { channel -> contentRepository.nextChannel(channel, categoryId) }

    fun zapPrev() = zap { channel -> contentRepository.prevChannel(channel, categoryId) }

    private fun zap(pick: suspend (LiveChannel) -> LiveChannel?) {
        viewModelScope.launch {
            zapMutex.withLock {
                val current = _uiState.value.channel ?: return@withLock
                pick(current)?.let { next ->
                    if (next.id != current.id) playChannel(next)
                }
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
        if (_uiState.value.channel == null) return
        playbackGeneration++
        cancelRecoveryJobs()
        sourceIndex = 0
        failuresOnCurrentSource = 0
        _uiState.update {
            it.copy(
                error = null,
                buffering = true,
                reconnecting = true,
                reconnectMessage = "Connecting…",
            )
        }
        prepareCurrent()
    }

    // Stop on activity stop (kills the stream), re-prepare when it returns.
    fun onLifecycleStop() {
        foregroundActive = false
        playbackGeneration++
        cancelRecoveryJobs()
        player.stop()
        _uiState.update {
            it.copy(buffering = false, reconnecting = false, reconnectMessage = null)
        }
    }

    fun onLifecycleStart() {
        foregroundActive = true
        if (_uiState.value.channel != null && _uiState.value.error == null) {
            playbackGeneration++
            failuresOnCurrentSource = 0
            _uiState.update {
                it.copy(
                    buffering = true,
                    reconnecting = true,
                    reconnectMessage = "Restoring stream…",
                )
            }
            prepareCurrent()
        }
    }

    private suspend fun playChannel(channel: LiveChannel) {
        playbackGeneration++
        cancelRecoveryJobs()
        sourceIndex = 0
        failuresOnCurrentSource = 0

        contentRepository.recordRecentView(
            playlistId = channel.playlistId,
            mediaType = com.novaplay.tv.data.db.Bookmark.MEDIA_LIVE,
            remoteId = channel.streamId,
        )
        candidateUrls = contentRepository.liveStreamUrls(channel, liveFormat.first())
        val categoryName = channel.categoryId?.let { contentRepository.liveCategoryById(it)?.name }
        _uiState.update {
            it.copy(
                channel = channel,
                categoryName = categoryName,
                error = null,
                buffering = true,
                reconnecting = false,
                reconnectMessage = null,
            )
        }
        showOverlay()
        prepareCurrent()
    }

    private fun prepareCurrent() {
        if (!foregroundActive) return
        val url = candidateUrls.getOrNull(sourceIndex) ?: run {
            _uiState.update {
                it.copy(
                    error = "No stream available for this channel",
                    buffering = false,
                    reconnecting = false,
                    reconnectMessage = null,
                )
            }
            return
        }

        stallWatchdogJob?.cancel()
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (url.endsWith(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.stop()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    private fun recoverFromFailure(finalMessage: String) {
        if (!foregroundActive || _uiState.value.channel == null || retryJob?.isActive == true) return

        stallWatchdogJob?.cancel()
        stabilityJob?.cancel()
        failuresOnCurrentSource++

        when (
            val decision = PlaybackRetryPolicy.afterFailure(
                sourceIndex = sourceIndex,
                failuresOnCurrentSource = failuresOnCurrentSource,
                sourceCount = candidateUrls.size,
            )
        ) {
            is PlaybackRetryDecision.RetryCurrent -> {
                scheduleReconnect(
                    delayMs = decision.delayMs,
                    message = "Reconnecting…",
                )
            }
            is PlaybackRetryDecision.TryNextSource -> {
                sourceIndex++
                failuresOnCurrentSource = 0
                scheduleReconnect(
                    delayMs = decision.delayMs,
                    message = "Trying alternate stream…",
                )
            }
            PlaybackRetryDecision.Exhausted -> {
                player.stop()
                _uiState.update {
                    it.copy(
                        error = finalMessage,
                        buffering = false,
                        reconnecting = false,
                        reconnectMessage = null,
                    )
                }
            }
        }
    }

    private fun scheduleReconnect(delayMs: Long, message: String) {
        val generation = playbackGeneration
        player.stop()
        _uiState.update {
            it.copy(
                error = null,
                buffering = true,
                reconnecting = true,
                reconnectMessage = message,
            )
        }
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            delay(delayMs)
            if (foregroundActive && playbackGeneration == generation) {
                prepareCurrent()
            }
        }
    }

    private fun armStallWatchdog(generation: Long) {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = viewModelScope.launch {
            delay(STALL_TIMEOUT_MS)
            if (
                foregroundActive &&
                playbackGeneration == generation &&
                player.playbackState == Player.STATE_BUFFERING &&
                _uiState.value.error == null
            ) {
                recoverFromFailure("The live stream stopped responding")
            }
        }
    }

    private fun showOverlay() {
        overlayJob?.cancel()
        _uiState.update { it.copy(overlayVisible = true) }
        overlayJob = viewModelScope.launch {
            delay(OVERLAY_HIDE_MS)
            _uiState.update { it.copy(overlayVisible = false) }
        }
    }

    private fun cancelRecoveryJobs() {
        retryJob?.cancel()
        stallWatchdogJob?.cancel()
        stabilityJob?.cancel()
        retryJob = null
        stallWatchdogJob = null
        stabilityJob = null
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
        cancelRecoveryJobs()
        player.release()
    }

    private companion object {
        const val OVERLAY_HIDE_MS = 4_000L
        const val STALL_TIMEOUT_MS = 12_000L
        const val STABLE_PLAYBACK_RESET_MS = 5_000L
    }
}
