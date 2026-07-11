package com.novaplay.tv.ui.player

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.novaplay.tv.data.db.Bookmark
import com.novaplay.tv.data.db.WatchProgress
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.PlaybackTrackPreferences
import com.novaplay.tv.data.prefs.SubtitleStyle
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.di.ApplicationScope
import com.novaplay.tv.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val languageTag: String? = null,
    val rawLabel: String? = null,
    val selected: Boolean,
)

data class VodPlayerUiState(
    val title: String = "",
    val episodeTag: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val controlsVisible: Boolean = true,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = true,
    val recoveryMessage: String? = null,
    val completed: Boolean = false,
    val error: String? = null,
)

private data class ProgressIdentity(
    val playlistId: Long,
    val mediaType: String,
    val remoteId: Long,
)

private data class TrackCandidate(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String?,
    val label: String?,
)

@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
    private val prefs: AppPreferences,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val mediaType: String = checkNotNull(savedStateHandle["mediaType"])
    private val mediaId: Long = checkNotNull(savedStateHandle["mediaId"])
    private val resume: Boolean = savedStateHandle.get<Boolean>("resume") ?: false

    private val _uiState = MutableStateFlow(VodPlayerUiState())
    val uiState: StateFlow<VodPlayerUiState> = _uiState.asStateFlow()

    val subtitleStyle: StateFlow<SubtitleStyle> = prefs.subtitleStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, SubtitleStyle())

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private var hideControlsJob: Job? = null
    private var recoveryJob: Job? = null
    private var bufferingWatchdog: Job? = null
    private var stablePlaybackJob: Job? = null
    private var lastSeekAt = 0L
    private var seekStreak = 0
    private var progressIdentity: ProgressIdentity? = null
    private var mediaUrl: String? = null
    private var recoveryAttempts = 0
    private var wasPlayingBeforeStop = false
    private var trackPreferences = PlaybackTrackPreferences()
    private var trackPreferencesApplied = false

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _uiState.update { it.copy(buffering = true) }
                        startBufferingWatchdog()
                    }
                    Player.STATE_READY -> {
                        bufferingWatchdog?.cancel()
                        _uiState.update {
                            it.copy(
                                buffering = false,
                                durationMs = player.duration.coerceAtLeast(0L),
                                completed = false,
                            )
                        }
                    }
                    Player.STATE_ENDED -> {
                        bufferingWatchdog?.cancel()
                        stablePlaybackJob?.cancel()
                        _uiState.update {
                            it.copy(
                                playing = false,
                                buffering = false,
                                controlsVisible = true,
                                recoveryMessage = null,
                                completed = true,
                                positionMs = player.duration.coerceAtLeast(0L),
                            )
                        }
                        viewModelScope.launch { persistProgress(forceComplete = true) }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update {
                    it.copy(
                        playing = isPlaying,
                        recoveryMessage = if (isPlaying) null else it.recoveryMessage,
                    )
                }
                if (isPlaying) scheduleStablePlaybackReset() else stablePlaybackJob?.cancel()
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackFailure(error)
            }

            override fun onTracksChanged(tracks: Tracks) {
                applyStoredTrackPreferences(tracks)
                rebuildTrackOptions(tracks)
            }
        })

        viewModelScope.launch {
            trackPreferences = prefs.playbackTrackPreferences.first()
            load()
        }

        // Frequent persistence keeps Resume accurate without writing on every frame.
        viewModelScope.launch {
            var lastSaveAt = 0L
            while (isActive) {
                if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                    _uiState.update {
                        it.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(0L),
                        )
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (player.isPlaying && now - lastSaveAt >= PROGRESS_SAVE_INTERVAL_MS) {
                        lastSaveAt = now
                        persistProgress()
                    }
                }
                delay(500)
            }
        }

        scheduleControlsHide()
    }

    private suspend fun load() {
        val url = when (mediaType) {
            Routes.MEDIA_TYPE_MOVIE -> {
                val movie = contentRepository.movieById(mediaId)
                movie?.let {
                    progressIdentity = ProgressIdentity(
                        playlistId = movie.playlistId,
                        mediaType = WatchProgress.MEDIA_MOVIE,
                        remoteId = movie.streamId,
                    )
                    _uiState.update { state -> state.copy(title = movie.name) }
                    contentRepository.recordRecentView(
                        playlistId = movie.playlistId,
                        mediaType = Bookmark.MEDIA_MOVIE,
                        remoteId = movie.streamId,
                    )
                    contentRepository.movieStreamUrl(movie)
                }
            }
            Routes.MEDIA_TYPE_EPISODE -> {
                val episode = contentRepository.episodeById(mediaId)
                episode?.let {
                    progressIdentity = ProgressIdentity(
                        playlistId = episode.playlistId,
                        mediaType = WatchProgress.MEDIA_EPISODE,
                        remoteId = episode.remoteEpisodeId,
                    )
                    val series = contentRepository.seriesById(episode.seriesLocalId)
                    val tag = "S%02dE%02d".format(Locale.US, episode.season, episode.episodeNum)
                    _uiState.update { state ->
                        state.copy(title = series?.name ?: episode.title, episodeTag = tag)
                    }
                    series?.let { item ->
                        contentRepository.recordRecentView(
                            playlistId = item.playlistId,
                            mediaType = Bookmark.MEDIA_SERIES,
                            remoteId = item.seriesId,
                        )
                    }
                    contentRepository.episodeStreamUrl(episode)
                }
            }
            else -> null
        }

        mediaUrl = url
        if (url == null) {
            _uiState.update { it.copy(error = "Media not found", buffering = false) }
            return
        }

        val saved = if (resume) contentRepository.watchProgress(mediaType, mediaId) else null
        val startPosition = saved?.let {
            VodResumePolicy.resumeStart(it.positionMs, it.durationMs)
        } ?: 0L

        prepareAt(positionMs = startPosition, recoveryMessage = null, resetRecovery = true)
    }

    private fun prepareAt(
        positionMs: Long,
        recoveryMessage: String?,
        resetRecovery: Boolean = false,
    ) {
        val url = mediaUrl ?: return
        if (resetRecovery) recoveryAttempts = 0
        recoveryJob?.cancel()
        bufferingWatchdog?.cancel()
        stablePlaybackJob?.cancel()
        trackPreferencesApplied = false
        _uiState.update {
            it.copy(
                error = null,
                buffering = true,
                completed = false,
                recoveryMessage = recoveryMessage,
            )
        }
        player.setMediaItem(MediaItem.fromUri(url), positionMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
    }

    // ---- recovery ----

    private fun handlePlaybackFailure(error: PlaybackException) {
        val recoverable = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            -> true
            else -> false
        }
        recoverOrFail(recoverable, playbackErrorMessage(error))
    }

    private fun startBufferingWatchdog() {
        bufferingWatchdog?.cancel()
        bufferingWatchdog = viewModelScope.launch {
            delay(VodRecoveryPolicy.BUFFERING_TIMEOUT_MS)
            if (
                player.playbackState == Player.STATE_BUFFERING &&
                _uiState.value.error == null &&
                recoveryJob?.isActive != true
            ) {
                recoverOrFail(
                    recoverable = true,
                    terminalMessage = "Playback could not continue after repeated buffering",
                )
            }
        }
    }

    private fun recoverOrFail(recoverable: Boolean, terminalMessage: String) {
        bufferingWatchdog?.cancel()
        stablePlaybackJob?.cancel()
        if (recoveryJob?.isActive == true) return

        if (!VodRecoveryPolicy.canRecover(recoveryAttempts, recoverable)) {
            _uiState.update {
                it.copy(
                    error = terminalMessage,
                    buffering = false,
                    recoveryMessage = null,
                    controlsVisible = false,
                )
            }
            return
        }

        recoveryAttempts++
        val attempt = recoveryAttempts
        val resumePosition = player.currentPosition
            .takeIf { it >= 0L }
            ?: _uiState.value.positionMs
        val message = VodRecoveryPolicy.messageForAttempt(attempt)
        player.pause()
        _uiState.update { it.copy(buffering = true, recoveryMessage = message, error = null) }
        recoveryJob = viewModelScope.launch {
            delay(VodRecoveryPolicy.delayForAttempt(attempt))
            prepareAt(resumePosition, recoveryMessage = message)
        }
    }

    private fun scheduleStablePlaybackReset() {
        stablePlaybackJob?.cancel()
        stablePlaybackJob = viewModelScope.launch {
            delay(VodRecoveryPolicy.STABLE_PLAYBACK_RESET_MS)
            if (player.isPlaying) {
                recoveryAttempts = 0
                _uiState.update { it.copy(recoveryMessage = null) }
            }
        }
    }

    private fun playbackErrorMessage(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "Network error — check your connection and try again"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "The provider did not make this video available"
        else -> "Playback failed — this file or codec may not be supported"
    }

    // ---- controls ----

    fun showControls() {
        _uiState.update { it.copy(controlsVisible = true) }
        scheduleControlsHide()
    }

    fun hideControls() {
        hideControlsJob?.cancel()
        _uiState.update { it.copy(controlsVisible = false) }
    }

    fun pokeControls() {
        if (_uiState.value.controlsVisible) scheduleControlsHide()
    }

    fun toggleControls() {
        if (_uiState.value.controlsVisible) hideControls() else showControls()
    }

    private fun scheduleControlsHide() {
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(CONTROLS_HIDE_MS)
            if (!_uiState.value.completed && _uiState.value.error == null) {
                _uiState.update { it.copy(controlsVisible = false) }
            }
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.completed || player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
            player.play()
            _uiState.update { it.copy(completed = false) }
        } else if (player.isPlaying) {
            player.pause()
            viewModelScope.launch { persistProgress() }
        } else {
            player.play()
        }
        showControls()
    }

    // ±10 s, accelerating up to 60 s while a D-pad key repeats.
    fun seekBy(direction: Int) {
        val now = SystemClock.elapsedRealtime()
        seekStreak = if (now - lastSeekAt < 700) seekStreak + 1 else 0
        lastSeekAt = now
        val multiplier = 1 + (seekStreak / 3).coerceAtMost(5)
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val target = (player.currentPosition + direction * 10_000L * multiplier)
            .coerceIn(0L, duration)
        player.seekTo(target)
        _uiState.update { it.copy(positionMs = target, completed = target >= duration) }
        showControls()
    }

    fun seekToFraction(fraction: Float) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        player.seekTo(target)
        _uiState.update { it.copy(positionMs = target, completed = target >= duration) }
        showControls()
    }

    // ---- track selection ----

    private fun applyStoredTrackPreferences(tracks: Tracks) {
        if (trackPreferencesApplied) return
        trackPreferencesApplied = true
        val builder = player.trackSelectionParameters.buildUpon()
        var changed = false

        findPreferredTrack(
            tracks = tracks,
            type = C.TRACK_TYPE_AUDIO,
            language = trackPreferences.audioLanguage,
            label = trackPreferences.audioLabel,
        )?.let { candidate ->
            val group = tracks.groups.getOrNull(candidate.groupIndex) ?: return@let
            builder.setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, candidate.trackIndex),
            )
            changed = true
        }

        if (!trackPreferences.subtitlesEnabled) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            changed = true
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            findPreferredTrack(
                tracks = tracks,
                type = C.TRACK_TYPE_TEXT,
                language = trackPreferences.subtitleLanguage,
                label = trackPreferences.subtitleLabel,
            )?.let { candidate ->
                val group = tracks.groups.getOrNull(candidate.groupIndex) ?: return@let
                builder.setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, candidate.trackIndex),
                )
                changed = true
            }
        }

        if (changed) player.trackSelectionParameters = builder.build()
    }

    private fun findPreferredTrack(
        tracks: Tracks,
        type: Int,
        language: String?,
        label: String?,
    ): TrackCandidate? {
        if (language.isNullOrBlank() && label.isNullOrBlank()) return null
        val candidates = buildList {
            tracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type != type) return@forEachIndexed
                for (trackIndex in 0 until group.length) {
                    if (!group.isTrackSupported(trackIndex)) continue
                    val format = group.getTrackFormat(trackIndex)
                    add(
                        TrackCandidate(
                            groupIndex = groupIndex,
                            trackIndex = trackIndex,
                            language = format.language,
                            label = format.label,
                        ),
                    )
                }
            }
        }
        val normalizedLanguage = language.normalizePreference()
        val normalizedLabel = label.normalizePreference()
        return candidates.firstOrNull {
            normalizedLanguage != null && it.language.normalizePreference() == normalizedLanguage
        } ?: candidates.firstOrNull {
            normalizedLabel != null && it.label.normalizePreference() == normalizedLabel
        }
    }

    private fun rebuildTrackOptions(tracks: Tracks) {
        val audio = mutableListOf<TrackOption>()
        val text = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                when (group.type) {
                    C.TRACK_TYPE_AUDIO -> audio += TrackOption(
                        groupIndex = groupIndex,
                        trackIndex = trackIndex,
                        label = audioLabel(format.language, format.label, format.channelCount),
                        languageTag = format.language,
                        rawLabel = format.label,
                        selected = group.isTrackSelected(trackIndex),
                    )
                    C.TRACK_TYPE_TEXT -> text += TrackOption(
                        groupIndex = groupIndex,
                        trackIndex = trackIndex,
                        label = textLabel(format.language, format.label),
                        languageTag = format.language,
                        rawLabel = format.label,
                        selected = group.isTrackSelected(trackIndex),
                    )
                }
            }
        }
        _uiState.update {
            it.copy(
                audioTracks = audio,
                textTracks = text,
                textEnabled = !player.trackSelectionParameters.disabledTrackTypes
                    .contains(C.TRACK_TYPE_TEXT),
            )
        }
    }

    fun selectAudioTrack(option: TrackOption) {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
        trackPreferences = trackPreferences.copy(
            audioLanguage = option.languageTag,
            audioLabel = option.rawLabel,
        )
        viewModelScope.launch {
            prefs.setAudioTrackPreference(option.languageTag, option.rawLabel)
        }
        showControls()
    }

    fun selectTextTrack(option: TrackOption?) {
        player.trackSelectionParameters = if (option == null) {
            player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
                .build()
        }
        trackPreferences = trackPreferences.copy(
            subtitlesEnabled = option != null,
            subtitleLanguage = option?.languageTag,
            subtitleLabel = option?.rawLabel,
        )
        viewModelScope.launch {
            prefs.setSubtitleTrackPreference(
                enabled = option != null,
                language = option?.languageTag,
                label = option?.rawLabel,
            )
        }
        _uiState.update { it.copy(textEnabled = option != null) }
        showControls()
    }

    private fun audioLabel(language: String?, label: String?, channelCount: Int): String {
        val lang = language.displayLanguageOrNull()
        val channels = when (channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> null
        }
        return listOfNotNull(lang ?: label, label.takeIf { it != lang }, channels)
            .distinct()
            .joinToString(" · ")
            .ifBlank { "Audio track" }
    }

    private fun textLabel(language: String?, label: String?): String =
        language.displayLanguageOrNull() ?: label ?: "Subtitle track"

    private fun String?.displayLanguageOrNull(): String? =
        this?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
            ?.let { code ->
                Locale.forLanguageTag(code).displayLanguage
                    .takeIf { it.isNotBlank() && it != code }
                    ?.replaceFirstChar { char -> char.uppercase() }
                    ?: code
            }

    private fun String?.normalizePreference(): String? =
        this?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    // ---- lifecycle and progress ----

    fun onLifecycleStop() {
        wasPlayingBeforeStop = player.isPlaying
        player.pause()
        viewModelScope.launch { persistProgress() }
    }

    fun onLifecycleStart() {
        if (wasPlayingBeforeStop && _uiState.value.error == null && !_uiState.value.completed) {
            player.play()
        }
        wasPlayingBeforeStop = false
    }

    fun retry() {
        recoveryAttempts = 0
        val resumePosition = _uiState.value.positionMs.coerceAtLeast(0L)
        prepareAt(resumePosition, recoveryMessage = "Restoring playback…", resetRecovery = true)
    }

    private suspend fun persistProgress(forceComplete: Boolean = false) {
        val identity = progressIdentity ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: _uiState.value.durationMs.takeIf { it > 0L }
            ?: return
        val rawPosition = if (forceComplete) duration else player.currentPosition.coerceAtLeast(0L)
        if (!forceComplete && !VodResumePolicy.shouldPersist(rawPosition, duration)) return
        val position = if (forceComplete) {
            duration
        } else {
            VodResumePolicy.normalizedSavedPosition(rawPosition, duration)
        }
        contentRepository.saveWatchProgress(
            WatchProgress(
                playlistId = identity.playlistId,
                mediaType = identity.mediaType,
                remoteId = identity.remoteId,
                positionMs = position,
                durationMs = duration,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override fun onCleared() {
        hideControlsJob?.cancel()
        recoveryJob?.cancel()
        bufferingWatchdog?.cancel()
        stablePlaybackJob?.cancel()
        val identity = progressIdentity
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val rawPosition = player.currentPosition.coerceAtLeast(0L)
        val completed = _uiState.value.completed
        player.release()
        if (identity != null && duration != null && (completed || VodResumePolicy.shouldPersist(rawPosition, duration))) {
            val progress = WatchProgress(
                playlistId = identity.playlistId,
                mediaType = identity.mediaType,
                remoteId = identity.remoteId,
                positionMs = if (completed) {
                    duration
                } else {
                    VodResumePolicy.normalizedSavedPosition(rawPosition, duration)
                },
                durationMs = duration,
                updatedAt = System.currentTimeMillis(),
            )
            appScope.launch { contentRepository.saveWatchProgress(progress) }
        }
    }

    private companion object {
        const val CONTROLS_HIDE_MS = 5_000L
        const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    }
}
