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
import com.novaplay.tv.data.db.Bookmark
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.epg.EpgNowNext
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.SubtitleStyle
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.prefs.VideoScale
import com.novaplay.tv.ui.player.PlaybackRetryDecision
import com.novaplay.tv.ui.player.PlaybackRetryPolicy
import com.novaplay.tv.ui.player.PlayerDigitAction
import com.novaplay.tv.ui.player.PlayerDigitPolicy
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Immutable UI snapshot for the live player: current channel plus overlay, buffering and error flags. */
data class LivePlayerUiState(
    val channel: LiveChannel? = null,
    val categoryName: String? = null,
    val overlayVisible: Boolean = false,
    val buffering: Boolean = true,
    val reconnecting: Boolean = false,
    val reconnectMessage: String? = null,
    val error: String? = null,
)

/**
 * Owns the ExoPlayer instance for live playback plus the zap and recovery
 * state machines. Zapping walks the indexed (playlistId, categoryId, num)
 * ordering in the DB; failures get bounded-backoff retries per source before
 * falling back to the alternate HLS/MPEG-TS URL (see [PlaybackRetryPolicy]).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class LivePlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val initialChannelId: Long = checkNotNull(savedStateHandle["channelId"])

    // -1 encodes "All channels" — zapping then moves across the full list.
    private val categoryId: Long? =
        savedStateHandle.get<Long>("categoryId")?.takeIf { it >= 0 }

    private val _uiState = MutableStateFlow(LivePlayerUiState())
    val uiState: StateFlow<LivePlayerUiState> = _uiState.asStateFlow()

    val subtitleStyle: StateFlow<SubtitleStyle> = prefs.subtitleStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleStyle())

    // Guide now/next for the playing channel, re-resolved on zap and rolled
    // over by a minute tick so a long session never shows a stale programme.
    @OptIn(ExperimentalCoroutinesApi::class)
    val nowNext: StateFlow<EpgNowNext> = combine(
        _uiState.map { it.channel }.distinctUntilChanged { old, new -> old?.id == new?.id },
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(GUIDE_TICK_MS)
            }
        },
    ) { channel, now -> channel to now }
        .flatMapLatest { (channel, now) ->
            if (channel == null) flowOf(EpgNowNext.EMPTY) else contentRepository.nowNext(channel, now)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpgNowNext.EMPTY)

    /** Persisted video scaling mode, applied live to the player surface. */
    val videoScale: StateFlow<VideoScale> = prefs.videoScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, VideoScale.FIT)

    /** Advances FIT → FILL → ZOOM → FIT and persists the choice. */
    fun cycleVideoScale() {
        viewModelScope.launch { prefs.setVideoScale(videoScale.value.next()) }
    }

    // ---- in-player channel list ----

    private val _channelListVisible = MutableStateFlow(false)
    val channelListVisible: StateFlow<Boolean> = _channelListVisible.asStateFlow()

    private val _panelQuery = MutableStateFlow("")
    val panelQuery: StateFlow<String> = _panelQuery.asStateFlow()

    /** Updates the picker's search text; short queries fall back to category browsing. */
    fun onPanelQueryChange(query: String) {
        _panelQuery.value = query
    }

    /**
     * Channels for the in-player picker: the current category by default, or an
     * FTS search across the whole playlist once two characters are typed —
     * essential with multi-thousand-channel lineups.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val channelList: Flow<PagingData<LiveChannel>> = combine(
        _uiState.map { it.channel?.playlistId }.filterNotNull().distinctUntilChanged(),
        _panelQuery.debounce(300).distinctUntilChanged(),
    ) { playlistId, query -> playlistId to query }
        .flatMapLatest { (playlistId, query) ->
            val fts = query.takeIf { it.length >= 2 }
                ?.let { ContentRepository.ftsPrefixQuery(it) }
            if (fts != null) {
                contentRepository.searchChannelsPager(playlistId, fts)
            } else {
                contentRepository.channelsPager(playlistId, categoryId)
            }
        }
        .cachedIn(viewModelScope)

    /** Shows or hides the channel picker panel. */
    fun toggleChannelList() {
        _channelListVisible.value = !_channelListVisible.value
        if (!_channelListVisible.value) _panelQuery.value = ""
    }

    /** Hides the channel picker panel and clears its search. */
    fun closeChannelList() {
        _channelListVisible.value = false
        _panelQuery.value = ""
    }

    /** Whether the playing channel is bookmarked; drives the overlay toggle. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentBookmarked: StateFlow<Boolean> = _uiState
        .map { it.channel }
        .distinctUntilChanged { old, new -> old?.id == new?.id }
        .flatMapLatest { channel ->
            if (channel == null) {
                flowOf(false)
            } else {
                contentRepository.bookmarkedIds(channel.playlistId, Bookmark.MEDIA_LIVE)
                    .map { channel.streamId in it }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Adds or removes the playing channel from bookmarks. */
    fun toggleBookmark() {
        val channel = _uiState.value.channel ?: return
        viewModelScope.launch {
            contentRepository.toggleBookmark(
                playlistId = channel.playlistId,
                mediaType = Bookmark.MEDIA_LIVE,
                remoteId = channel.streamId,
            )
        }
    }

    /** Plays a channel picked from the panel; picking the current one just closes it. */
    fun selectFromList(channel: LiveChannel) {
        closeChannelList()
        viewModelScope.launch {
            zapMutex.withLock {
                if (channel.id != _uiState.value.channel?.id) playChannel(channel)
            }
        }
    }

    // ---- previous-channel recall and in-player digit zap ----

    private var previousChannel: LiveChannel? = null

    /** Swaps back to the previously watched channel, if any. */
    fun zapRecall() {
        viewModelScope.launch {
            zapMutex.withLock {
                val target = previousChannel ?: return@withLock
                if (target.id != _uiState.value.channel?.id) playChannel(target)
            }
        }
    }

    private val _digitBuffer = MutableStateFlow("")
    val digitBuffer: StateFlow<String> = _digitBuffer.asStateFlow()

    private var digitJob: Job? = null

    /**
     * Buffers a remote digit; after the commit delay the buffer resolves via
     * [PlayerDigitPolicy] — a channel number zaps directly, a lone zero recalls
     * the previous channel.
     */
    fun onDigit(digit: Char) {
        _digitBuffer.value = (_digitBuffer.value + digit).takeLast(PlayerDigitPolicy.MAX_DIGITS)
        digitJob?.cancel()
        digitJob = viewModelScope.launch {
            delay(PlayerDigitPolicy.COMMIT_DELAY_MS)
            val action = PlayerDigitPolicy.interpret(_digitBuffer.value)
            _digitBuffer.value = ""
            when (action) {
                is PlayerDigitAction.JumpToNumber -> zapMutex.withLock {
                    val current = _uiState.value.channel ?: return@withLock
                    val target = contentRepository.channelAtOrAfterNum(
                        playlistId = current.playlistId,
                        categoryId = categoryId,
                        num = action.num,
                    )
                    if (target != null && target.id != current.id) playChannel(target)
                }
                PlayerDigitAction.Recall -> zapMutex.withLock {
                    val target = previousChannel ?: return@withLock
                    if (target.id != _uiState.value.channel?.id) playChannel(target)
                }
                PlayerDigitAction.None -> Unit
            }
        }
    }

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
            // Hard player errors feed the retry/fallback state machine.
            override fun onPlayerError(error: PlaybackException) {
                recoverFromFailure(playbackErrorMessage(error))
            }

            // Drives the buffering UI and recovery timers from player state
            // transitions; an unexpected ENDED counts as a failure.
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

    /** Zaps up to the next channel by number within the current category (indexed DB lookup). */
    fun zapNext() = zap { channel -> contentRepository.nextChannel(channel, categoryId) }

    /** Zaps down to the previous channel by number within the current category. */
    fun zapPrev() = zap { channel -> contentRepository.prevChannel(channel, categoryId) }

    // Serialised zap: the mutex keeps rapid D-pad presses from interleaving
    // channel switches; picking the same channel again is a no-op.
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

    /** Shows or hides the channel info overlay (OK press / tap); showing re-arms the auto-hide timer. */
    fun toggleOverlay() {
        if (_uiState.value.overlayVisible) {
            overlayJob?.cancel()
            _uiState.update { it.copy(overlayVisible = false) }
        } else {
            showOverlay()
        }
    }

    /**
     * User-initiated retry from the error screen: resets the source index and
     * failure count, then reconnects starting from the first candidate URL.
     */
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

    /** Restores the stream when the app returns to the foreground, unless the error screen is showing. */
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

    // Switches playback to [channel]: records it as recently viewed, resolves
    // the candidate URLs for the preferred container, and starts on the first
    // source with a fresh retry budget. Also pops the info overlay.
    private suspend fun playChannel(channel: LiveChannel) {
        // Remember where we came from so recall ("0" / the swap button) works.
        _uiState.value.channel?.takeIf { it.id != channel.id }?.let { previousChannel = it }
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

    // Points the player at the current candidate URL; no candidate left means
    // a terminal "no stream" error. No-op while the app is backgrounded.
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

    // Central failure path: asks PlaybackRetryPolicy whether to retry the same
    // source with backoff, fall back to the alternate container URL, or give
    // up and surface [finalMessage] as the terminal error.
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

    // Stops the player and re-prepares after [delayMs] — but only if no zap,
    // retry or lifecycle change bumped the playback generation meanwhile.
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

    // Treats buffering that outlasts STALL_TIMEOUT_MS as a failure so silent
    // stalls enter the same recovery path as hard player errors.
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

    // Shows the channel info overlay and (re)starts the 4 s auto-hide timer.
    private fun showOverlay() {
        overlayJob?.cancel()
        _uiState.update { it.copy(overlayVisible = true) }
        overlayJob = viewModelScope.launch {
            delay(OVERLAY_HIDE_MS)
            _uiState.update { it.copy(overlayVisible = false) }
        }
    }

    // Cancels any pending reconnect, stall-watchdog and stability timers.
    private fun cancelRecoveryJobs() {
        retryJob?.cancel()
        stallWatchdogJob?.cancel()
        stabilityJob?.cancel()
        retryJob = null
        stallWatchdogJob = null
        stabilityJob = null
    }

    // Maps ExoPlayer error codes to short user-facing messages.
    private fun playbackErrorMessage(error: PlaybackException): String =
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> "Network error — check your connection"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Stream unavailable from provider"
            else -> "Playback failed"
        }

    /** Cancels all timers and releases the player when the ViewModel is destroyed. */
    override fun onCleared() {
        overlayJob?.cancel()
        cancelRecoveryJobs()
        player.release()
    }

    private companion object {
        const val OVERLAY_HIDE_MS = 4_000L
        const val STALL_TIMEOUT_MS = 12_000L
        const val STABLE_PLAYBACK_RESET_MS = 5_000L
        const val GUIDE_TICK_MS = 60_000L
    }
}
