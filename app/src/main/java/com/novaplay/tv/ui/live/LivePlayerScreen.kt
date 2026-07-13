package com.novaplay.tv.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.R
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.epg.EpgNowNext
import com.novaplay.tv.data.prefs.VideoScale
import com.novaplay.tv.ui.components.ErrorState
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.player.BufferingIndicator
import com.novaplay.tv.ui.player.PlayerSurface
import com.novaplay.tv.ui.theme.NovaAccentGradient
import com.novaplay.tv.ui.theme.isTvDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen live playback surface. D-pad UP/DOWN zap to the next/previous
 * channel in the current category and OK toggles the channel info overlay;
 * touch devices get tap-to-toggle and vertical-swipe zapping instead. The
 * stream is stopped when the app leaves the foreground and restored on return.
 */
@Composable
fun LivePlayerScreen(
    viewModel: LivePlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subtitleStyle by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val nowNext by viewModel.nowNext.collectAsStateWithLifecycle()
    val videoScale by viewModel.videoScale.collectAsStateWithLifecycle()
    val digitBuffer by viewModel.digitBuffer.collectAsStateWithLifecycle()
    val channelListVisible by viewModel.channelListVisible.collectAsStateWithLifecycle()
    val rootFocus = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTv = isTvDevice()

    // Closing the picker restores playback key handling to the root surface.
    BackHandler(enabled = channelListVisible) { viewModel.closeChannelList() }
    LaunchedEffect(channelListVisible) {
        if (!channelListVisible && state.error == null) rootFocus.requestFocus()
    }

    // Brief on-screen confirmation when the scaling mode changes.
    var scaleFlash by remember { mutableStateOf<VideoScale?>(null) }
    var scaleSeen by remember { mutableStateOf(false) }
    LaunchedEffect(videoScale) {
        if (scaleSeen) {
            scaleFlash = videoScale
            delay(1_400)
            scaleFlash = null
        } else {
            scaleSeen = true
        }
    }

    // Stop the stream when the app leaves the foreground; re-prepare on return.
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

    // Root grabs D-pad input: UP/DOWN zap, OK toggles the info overlay.
    LaunchedEffect(state.error) {
        if (state.error == null) rootFocus.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (state.error != null) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // While the picker is open, only LEFT closes it; everything else
                // (UP/DOWN/OK) drives the panel's own focus navigation.
                if (channelListVisible) {
                    return@onPreviewKeyEvent if (event.key == Key.DirectionLeft) {
                        viewModel.closeChannelList()
                        true
                    } else {
                        false
                    }
                }
                val digit = event.key.liveDigitOrNull()
                if (digit != null) {
                    viewModel.onDigit(digit)
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.DirectionUp -> {
                        viewModel.zapNext()
                        true
                    }
                    Key.DirectionDown -> {
                        viewModel.zapPrev()
                        true
                    }
                    Key.DirectionLeft -> {
                        viewModel.toggleChannelList()
                        true
                    }
                    Key.DirectionRight -> {
                        viewModel.cycleVideoScale()
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        viewModel.toggleOverlay()
                        true
                    }
                    else -> false
                }
            }
            // Touch: tap shows/hides the channel info, vertical swipe zaps.
            .pointerInput(Unit) {
                detectTapGestures {
                    if (state.error == null) viewModel.toggleOverlay()
                }
            }
            .pointerInput(Unit) {
                val threshold = 60.dp.toPx()
                var dragTotal = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        dragTotal += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        if (state.error == null) {
                            when {
                                dragTotal <= -threshold -> viewModel.zapNext()
                                dragTotal >= threshold -> viewModel.zapPrev()
                            }
                        }
                    },
                )
            },
    ) {
        PlayerSurface(
            player = viewModel.player,
            subtitleStyle = subtitleStyle,
            modifier = Modifier.fillMaxSize(),
            videoScale = videoScale,
        )

        if (state.buffering && state.error == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center),
            ) {
                BufferingIndicator(modifier = Modifier.size(44.dp))
                if (state.reconnecting) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = state.reconnectMessage ?: "Reconnecting…",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
            }
        }

        // Channel info overlay — shown on zap and OK, auto-hides after 4 s.
        AnimatedVisibility(
            visible = state.overlayVisible && state.error == null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ChannelInfoBar(
                number = state.channel?.num,
                name = state.channel?.name.orEmpty(),
                categoryName = state.categoryName,
                nowNext = nowNext,
                // Touch has no CH+/CH- or color keys: those actions live in the
                // overlay. TV drives them from the remote (LEFT/RIGHT/digits).
                onZapNext = viewModel::zapNext.takeUnless { isTv },
                onZapPrev = viewModel::zapPrev.takeUnless { isTv },
                onOpenChannelList = viewModel::toggleChannelList.takeUnless { isTv },
                onRecall = viewModel::zapRecall.takeUnless { isTv },
                onCycleScale = viewModel::cycleVideoScale.takeUnless { isTv },
            )
        }

        // Buffered channel digits, mirroring the Live browser's indicator.
        if (digitBuffer.isNotEmpty()) {
            Text(
                text = digitBuffer,
                style = TextStyle(
                    brush = NovaAccentGradient,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(28.dp),
            )
        }

        // Transient confirmation after cycling the video scale.
        scaleFlash?.let { scale ->
            Text(
                text = videoScaleLabel(scale),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 26.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 7.dp),
            )
        }

        ChannelListPanel(
            visible = channelListVisible && state.error == null,
            channels = viewModel.channelList,
            currentChannelId = state.channel?.id,
            onSelect = viewModel::selectFromList,
            onDismiss = viewModel::closeChannelList,
        )

        state.error?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
            ) {
                ErrorState(
                    message = message,
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}

/**
 * Bottom overlay showing channel number, name, category and — when the guide
 * knows it — the airing programme with its time range, over a scrim gradient.
 * On touch form factors it also hosts up/down zap buttons; TV remotes zap via
 * D-pad, so the buttons are omitted there.
 */
@Composable
private fun ChannelInfoBar(
    number: Int?,
    name: String,
    categoryName: String?,
    nowNext: EpgNowNext = EpgNowNext.EMPTY,
    onZapNext: (() -> Unit)? = null,
    onZapPrev: (() -> Unit)? = null,
    onOpenChannelList: (() -> Unit)? = null,
    onRecall: (() -> Unit)? = null,
    onCycleScale: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                ),
            )
            .padding(horizontal = 48.dp, vertical = 30.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Channel number in the signature gradient — the zap focal point.
            Text(
                text = number?.toString().orEmpty(),
                style = TextStyle(
                    brush = NovaAccentGradient,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                ),
            )
            Spacer(Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                categoryName?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Locale-aware short times, e.g. "18:30 – 20:00  Evening News".
                val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
                nowNext.now?.let { airing ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${timeFormat.format(Date(airing.startMs))} – " +
                            "${timeFormat.format(Date(airing.endMs))}  ${airing.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                nowNext.next?.let { upcoming ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.epg_next, upcoming.title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            onOpenChannelList?.let {
                ZapButton(
                    icon = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.player_channel_list),
                    onClick = it,
                )
                Spacer(Modifier.width(12.dp))
            }
            onRecall?.let {
                ZapButton(
                    icon = Icons.Default.SwapHoriz,
                    contentDescription = stringResource(R.string.player_recall),
                    onClick = it,
                )
                Spacer(Modifier.width(12.dp))
            }
            onCycleScale?.let {
                ZapButton(
                    icon = Icons.Default.AspectRatio,
                    contentDescription = stringResource(R.string.player_video_scale),
                    onClick = it,
                )
                Spacer(Modifier.width(12.dp))
            }
            if (onZapNext != null && onZapPrev != null) {
                ZapButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Next channel",
                    onClick = onZapNext,
                )
                Spacer(Modifier.width(12.dp))
                ZapButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Previous channel",
                    onClick = onZapPrev,
                )
            }
        }
    }
}

/** Localized display name of a video scaling mode. */
@Composable
private fun videoScaleLabel(scale: VideoScale): String = stringResource(
    when (scale) {
        VideoScale.FIT -> R.string.video_scale_fit
        VideoScale.FILL -> R.string.video_scale_fill
        VideoScale.ZOOM -> R.string.video_scale_zoom
    },
)

/**
 * In-player channel picker: a left-side panel over a scrim listing the current
 * category's channels (paged). On TV the first row takes focus on open and
 * LEFT/BACK closes; on touch, tapping the scrim dismisses. Selecting a channel
 * zaps immediately.
 */
@Composable
private fun ChannelListPanel(
    visible: Boolean,
    channels: Flow<PagingData<LiveChannel>>,
    currentChannelId: Long?,
    onSelect: (LiveChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = channels.collectAsLazyPagingItems()
    val firstRowFocus = remember { FocusRequester() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        // Scrim: tap anywhere outside the panel to dismiss (touch).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { -it / 3 },
        exit = fadeOut() + slideOutHorizontally { -it / 3 },
    ) {
        LaunchedEffect(visible, items.itemCount) {
            if (visible && items.itemCount > 0) runCatching { firstRowFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(330.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.94f), Color.Black.copy(alpha = 0.82f)),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 22.dp),
        ) {
            Text(
                text = stringResource(R.string.player_channel_list),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    count = items.itemCount,
                    key = items.itemKey { it.id },
                ) { index ->
                    items[index]?.let { channel ->
                        val current = channel.id == currentChannelId
                        NovaClickable(
                            onClick = { onSelect(channel) },
                            containerColor = if (current) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                Color.Transparent
                            },
                            focusedScale = 1.0f,
                            accessibilityLabel = channel.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .then(if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                            ) {
                                Text(
                                    text = channel.num.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(46.dp),
                                )
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (current) MaterialTheme.colorScheme.primary else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Circular translucent icon button used for touch zapping in the info overlay. */
@Composable
private fun ZapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    NovaClickable(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
        shape = CircleShape,
        containerColor = Color.White.copy(alpha = 0.1f),
        restingBorder = false,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(32.dp),
        )
    }
}
