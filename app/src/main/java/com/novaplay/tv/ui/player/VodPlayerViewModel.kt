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
    val selected: Boolean,
)

data class VodPlayerUiState(
    val title: String = "",
    val episodeTag: String? = null, // "S01E04"
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val controlsVisible: Boolean = true,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = true,
    val error: String? = null,
)

private data class ProgressIdentity(
    val playlistId: Long,
    val mediaType: String,
    val remoteId: Long,
)

@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
    prefs: AppPreferences,
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
    private var lastSeekAt = 0L
    private var seekStreak = 0
    private var progressIdentity: ProgressIdentity? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _uiState.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
                if (playbackState == Player.STATE_READY) {
                    _uiState.update { it.copy(durationMs = player.duration.coerceAtLeast(0)) }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(playing = isPlaying) }
            }

            override fun onPlayerError(error: PlaybackException) {
                _uiState.update { it.copy(error = "Playback failed — the file may be unavailable") }
            }

            // Tracks arrive after prepare, not at init: button visibility and
            // dialog contents are recomputed here.
            override fun onTracksChanged(tracks: Tracks) {
                rebuildTrackOptions(tracks)
            }
        })

        viewModelScope.launch { load() }

        // UI position ticker + progress persistence every 10 s.
        viewModelScope.launch {
            var lastSaveAt = 0L
            while (isActive) {
                if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                    _uiState.update {
                        it.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0),
                            durationMs = player.duration.coerceAtLeast(0),
                        )
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (player.isPlaying && now - lastSaveAt >= 10_000) {
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
        val loaded: Pair<String, String?>? = when (mediaType) {
            Routes.MEDIA_TYPE_MOVIE -> {
                val movie = contentRepository.movieById(mediaId)
                movie?.let {
                    progressIdentity = ProgressIdentity(
                        playlistId = movie.playlistId,
                        mediaType = WatchProgress.MEDIA_MOVIE,
                        remoteId = movie.streamId,
                    )
                    _uiState.update { s -> s.copy(title = movie.name) }
                    contentRepository.recordRecentView(
                        playlistId = movie.playlistId,
                        mediaType = Bookmark.MEDIA_MOVIE,
                        remoteId = movie.streamId,
                    )
                    contentRepository.movieStreamUrl(movie)?.let { url -> url to null }
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
                    _uiState.update { s ->
                        s.copy(title = series?.name ?: episode.title, episodeTag = tag)
                    }
                    series?.let { s ->
                        contentRepository.recordRecentView(
                            playlistId = s.playlistId,
                            mediaType = Bookmark.MEDIA_SERIES,
                            remoteId = s.seriesId,
                        )
                    }
                    contentRepository.episodeStreamUrl(episode)?.let { url -> url to tag }
                }
            }
            else -> null
        }

        val url = loaded?.first ?: run {
            _uiState.update { it.copy(error = "Media not found") }
            return
        }

        val startPosition = if (resume) {
            contentRepository.watchProgress(mediaType, mediaId)?.positionMs ?: 0L
        } else {
            0L
        }

        player.setMediaItem(MediaItem.fromUri(url), startPosition)
        player.prepare()
        player.playWhenReady = true
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

    // Touch: single tap on the video toggles the overlay.
    fun toggleControls() {
        if (_uiState.value.controlsVisible) hideControls() else showControls()
    }

    private fun scheduleControlsHide() {
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(CONTROLS_HIDE_MS)
            _uiState.update { it.copy(controlsVisible = false) }
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            viewModelScope.launch { persistProgress() }
        } else {
            player.play()
        }
        showControls()
    }

    // ±10 s, accelerating up to 60 s while the key repeats.
    fun seekBy(direction: Int) {
        val now = SystemClock.elapsedRealtime()
        seekStreak = if (now - lastSeekAt < 700) seekStreak + 1 else 0
        lastSeekAt = now
        val multiplier = 1 + (seekStreak / 3).coerceAtMost(5)
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        val target = (player.currentPosition + direction * 10_000L * multiplier)
            .coerceIn(0L, duration)
        player.seekTo(target)
        _uiState.update { it.copy(positionMs = target) }
        showControls()
    }

    // Touch: absolute seek from a tap or scrub on the progress bar.
    fun seekToFraction(fraction: Float) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        player.seekTo(target)
        _uiState.update { it.copy(positionMs = target) }
        showControls()
    }

    // ---- track selection ----

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
                        selected = group.isTrackSelected(trackIndex),
                    )
                    C.TRACK_TYPE_TEXT -> text += TrackOption(
                        groupIndex = groupIndex,
                        trackIndex = trackIndex,
                        label = textLabel(format.language, format.label),
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
        showControls()
    }

    fun selectTextTrack(option: TrackOption?) {
        player.trackSelectionParameters = if (option == null) {
            // "Off" — disable the whole text renderer.
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
        (language.displayLanguageOrNull() ?: label ?: "Subtitle track")

    private fun String?.displayLanguageOrNull(): String? =
        this?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
            ?.let { code ->
                Locale.forLanguageTag(code).displayLanguage
                    .takeIf { it.isNotBlank() && it != code }
                    ?.replaceFirstChar { ch -> ch.uppercase() }
                    ?: code
            }

    // ---- lifecycle ----

    fun onLifecycleStop() {
        player.pause()
        viewModelScope.launch { persistProgress() }
    }

    fun retry() {
        _uiState.update { it.copy(error = null, buffering = true) }
        viewModelScope.launch { load() }
    }

    private suspend fun persistProgress() {
        val identity = progressIdentity ?: return
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        val position = player.currentPosition.coerceIn(0, duration)
        if (position < 10_000) return
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
        // Persist final position synchronously enough: player is still valid here.
        val identity = progressIdentity
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        val position = player.currentPosition
        player.release()
        if (identity != null && duration != null && position >= 10_000) {
            val progress = WatchProgress(
                playlistId = identity.playlistId,
                mediaType = identity.mediaType,
                remoteId = identity.remoteId,
                positionMs = position.coerceAtMost(duration),
                durationMs = duration,
                updatedAt = System.currentTimeMillis(),
            )
            appScope.launch { contentRepository.saveWatchProgress(progress) }
        }
    }

    private companion object {
        const val CONTROLS_HIDE_MS = 5_000L
    }
}
