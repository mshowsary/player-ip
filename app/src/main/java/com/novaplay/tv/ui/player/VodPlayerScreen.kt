package com.novaplay.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.ui.components.ErrorState
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.NovaDialog
import com.novaplay.tv.ui.movies.formatPosition
import com.novaplay.tv.ui.theme.LocalNovaAccents
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice

/**
 * Full-screen VOD player: video surface, buffering spinner (with recovery message),
 * auto-hiding controls overlay, error state, and audio/subtitle selection dialogs.
 * On TV, focus follows the overlay — play/pause when controls show, an invisible root
 * when hidden; with controls hidden, D-pad LEFT/RIGHT seek directly (accelerating
 * while held) and any other key just reveals the controls. Pauses on lifecycle stop
 * and resumes on start if playback was active.
 */
@Composable
fun VodPlayerScreen(
    viewModel: VodPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subtitleStyle by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val rootFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTv = isTvDevice()

    var showAudioDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    val dialogOpen = showAudioDialog || showTextDialog

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onLifecycleStop()
                Lifecycle.Event.ON_START -> viewModel.onLifecycleStart()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.controlsVisible, state.error, dialogOpen) {
        if (!isTv || state.error != null || dialogOpen) return@LaunchedEffect
        if (state.controlsVisible) {
            runCatching { playPauseFocus.requestFocus() }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (state.error != null || dialogOpen) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Dedicated remote media keys act directly, controls visible or not.
                when (event.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        viewModel.togglePlayPause()
                        viewModel.showControls()
                        return@onPreviewKeyEvent true
                    }
                    Key.MediaFastForward -> {
                        viewModel.seekBy(+1)
                        return@onPreviewKeyEvent true
                    }
                    Key.MediaRewind -> {
                        viewModel.seekBy(-1)
                        return@onPreviewKeyEvent true
                    }
                    else -> Unit
                }
                if (state.controlsVisible) {
                    viewModel.pokeControls()
                    false
                } else {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            viewModel.seekBy(-1)
                            true
                        }
                        Key.DirectionRight -> {
                            viewModel.seekBy(+1)
                            true
                        }
                        Key.Back -> false
                        else -> {
                            viewModel.showControls()
                            true
                        }
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (state.error == null && !showAudioDialog && !showTextDialog) {
                        viewModel.toggleControls()
                    }
                }
            },
    ) {
        PlayerSurface(
            player = viewModel.player,
            subtitleStyle = subtitleStyle,
            modifier = Modifier.fillMaxSize(),
        )

        if (state.buffering && state.error == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                BufferingIndicator(modifier = Modifier.size(44.dp))
                state.recoveryMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.controlsVisible && state.error == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            VodControlsOverlay(
                state = state,
                playPauseFocus = playPauseFocus,
                onTogglePlay = viewModel::togglePlayPause,
                onSeek = viewModel::seekBy,
                onSeekFraction = viewModel::seekToFraction,
                onOpenAudio = { showAudioDialog = true },
                onOpenSubtitles = { showTextDialog = true },
            )
        }

        state.error?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f)),
            ) {
                ErrorState(message = message, onRetry = viewModel::retry)
            }
        }
    }

    if (showAudioDialog) {
        TrackSelectionDialog(
            title = "Audio",
            options = state.audioTracks,
            allowOff = false,
            offSelected = false,
            onSelect = { option ->
                option?.let(viewModel::selectAudioTrack)
                showAudioDialog = false
            },
            onDismiss = { showAudioDialog = false },
        )
    }

    if (showTextDialog) {
        TrackSelectionDialog(
            title = "Subtitles",
            options = state.textTracks,
            allowOff = true,
            offSelected = !state.textEnabled || state.textTracks.none { it.selected },
            onSelect = { option ->
                viewModel.selectTextTrack(option)
                showTextDialog = false
            },
            onDismiss = { showTextDialog = false },
        )
    }
}

/**
 * Gradient-scrimmed controls: title/episode tag on top; seek bar, transport buttons,
 * position readout and track shortcuts on the bottom. The audio button only appears
 * when the stream offers a real choice (> 1 track), subtitles whenever any exist.
 */
@Composable
private fun VodControlsOverlay(
    state: VodPlayerUiState,
    playPauseFocus: FocusRequester,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onSeekFraction: (Float) -> Unit,
    onOpenAudio: () -> Unit,
    onOpenSubtitles: () -> Unit,
) {
    val compact = isCompactWidth()
    val horizontalPadding = if (compact) 20.dp else 48.dp
    val verticalPadding = if (compact) 18.dp else 28.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.84f), Color.Transparent),
                    ),
                )
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                state.episodeTag?.let { tag ->
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                    ),
                )
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = onSeek,
                onSeekFraction = onSeekFraction,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PlayerIconButton(
                    icon = when {
                        state.completed -> Icons.Default.Replay
                        state.playing -> Icons.Default.Pause
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = when {
                        state.completed -> "Play again"
                        state.playing -> "Pause"
                        else -> "Play"
                    },
                    onClick = onTogglePlay,
                    modifier = Modifier.focusRequester(playPauseFocus),
                )
                Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
                PlayerIconButton(
                    icon = Icons.Default.Replay10,
                    contentDescription = "Back 10 seconds",
                    onClick = { onSeek(-1) },
                )
                Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
                PlayerIconButton(
                    icon = Icons.Default.Forward10,
                    contentDescription = "Forward 10 seconds",
                    onClick = { onSeek(+1) },
                )
                Spacer(Modifier.width(if (compact) 12.dp else 20.dp))
                Text(
                    text = "${formatPosition(state.positionMs)}  /  ${formatPosition(state.durationMs)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                if (state.audioTracks.size > 1) {
                    PlayerIconButton(
                        icon = Icons.Default.Audiotrack,
                        contentDescription = "Audio",
                        onClick = onOpenAudio,
                    )
                    Spacer(Modifier.width(if (compact) 8.dp else 14.dp))
                }
                if (state.textTracks.isNotEmpty()) {
                    PlayerIconButton(
                        icon = Icons.Default.Subtitles,
                        contentDescription = "Subtitles",
                        onClick = onOpenSubtitles,
                    )
                }
            }
        }
    }
}

/** Circular focusable icon button for the transport row; fills with the accent colour and scales up on focus. */
@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onClick,
        modifier = modifier
            .size(50.dp)
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
        shape = CircleShape,
        containerColor = Color.White.copy(alpha = 0.1f),
        focusedContainerColor = MaterialTheme.colorScheme.primary,
        focusedScale = 1.08f,
        restingBorder = false,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .align(Alignment.Center)
                .size(27.dp),
        )
    }
}

/**
 * Focusable progress bar. When focused, D-pad LEFT/RIGHT seek ±10 s via [onSeek]
 * (accelerating while the key repeats); on touch, taps and horizontal drags scrub
 * to a fraction of the duration — the drag preview is local and only committed on
 * release. Track and thumb grow while focused or scrubbing.
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Int) -> Unit,
    onSeekFraction: (Float) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val playedFraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val fraction = scrubFraction ?: playedFraction
    val active = focused || scrubFraction != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeek(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        onSeek(+1)
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) onSeekFraction(offset.x / size.width)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (size.width > 0) {
                            scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (size.width > 0) {
                            scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        scrubFraction?.let(onSeekFraction)
                        scrubFraction = null
                    },
                    onDragCancel = { scrubFraction = null },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (active) 7.dp else 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(if (active) 7.dp else 4.dp)
                .clip(CircleShape)
                .background(LocalNovaAccents.current.gradient),
        )
        Box(
            modifier = Modifier.fillMaxWidth(fraction),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .scale(if (active) 1.2f else 0.9f)
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.White),
            )
        }
    }
}

/**
 * Dialog listing audio or subtitle tracks; an "Off" row is prepended when [allowOff]
 * (subtitles only) and selecting it reports null. On TV, initial focus lands on the
 * currently active row so a single OK press confirms the existing choice.
 */
@Composable
private fun TrackSelectionDialog(
    title: String,
    options: List<TrackOption>,
    allowOff: Boolean,
    offSelected: Boolean,
    onSelect: (TrackOption?) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    val isTv = isTvDevice()
    val selectedIndex = options.indexOfFirst { it.selected }
    val focusOff = allowOff && offSelected

    NovaDialog(title = title, onDismiss = onDismiss, maxWidth = 500.dp) {
        LaunchedEffect(Unit) {
            if (isTv) runCatching { selectedFocus.requestFocus() }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            if (allowOff) {
                item(key = "off") {
                    TrackRow(
                        label = "Off",
                        selected = offSelected,
                        onClick = { onSelect(null) },
                        modifier = if (focusOff || selectedIndex < 0) {
                            Modifier.focusRequester(selectedFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
            items(count = options.size, key = { index ->
                "${options[index].groupIndex}:${options[index].trackIndex}"
            }) { index ->
                val option = options[index]
                TrackRow(
                    label = option.label,
                    selected = option.selected && (!allowOff || !offSelected),
                    onClick = { onSelect(option) },
                    modifier = if (!focusOff && index == selectedIndex) {
                        Modifier.focusRequester(selectedFocus)
                    } else if (!allowOff && selectedIndex < 0 && index == 0) {
                        Modifier.focusRequester(selectedFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/** Single focusable dialog row: track label plus a check mark on the active choice. */
@Composable
private fun TrackRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        focusedScale = 1.015f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
