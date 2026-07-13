package com.novaplay.tv.ui.live

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

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
    val panelQuery by viewModel.panelQuery.collectAsStateWithLifecycle()
    val currentBookmarked by viewModel.currentBookmarked.collectAsStateWithLifecycle()
    val gesturesEnabled by viewModel.gesturesEnabled.collectAsStateWithLifecycle()
    val gestureHintPending by viewModel.gestureHintPending.collectAsStateWithLifecycle()
    val rootFocus = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTv = isTvDevice()

    // First-time gesture demo on touch: three chips over the zones for a few
    // seconds, then never again (any tap also dismisses it).
    var hintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(gestureHintPending, isTv) {
        if (gestureHintPending && !isTv) {
            hintVisible = true
            delay(5_000)
            if (hintVisible) {
                hintVisible = false
                viewModel.markGestureHintShown()
            }
        }
    }

    // Volume/brightness slide adjustments (touch); HUD text mirrors each change.
    val context = LocalContext.current
    val adjuster = remember {
        AvAdjuster(
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager,
            window = context.findActivity()?.window,
            initialBrightness = { context.currentSystemBrightnessFraction() },
        )
    }
    val adjustHud = adjuster.hud.value
    LaunchedEffect(adjustHud) {
        if (adjustHud != null) {
            delay(900)
            adjuster.clearHud(adjustHud)
        }
    }

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

    // Same pattern for the gestures quick toggle.
    var gestureFlash by remember { mutableStateOf<Boolean?>(null) }
    var gestureSeen by remember { mutableStateOf(false) }
    LaunchedEffect(gesturesEnabled) {
        if (gestureSeen) {
            gestureFlash = gesturesEnabled
            delay(1_400)
            gestureFlash = null
        } else {
            gestureSeen = true
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
            // Touch: tap shows/hides the channel info. Vertical drags split into
            // three zones like the well-known touch players: left edge adjusts
            // brightness, right edge volume, and the middle swipe-zaps.
            .pointerInput(Unit) {
                detectTapGestures {
                    if (hintVisible) {
                        hintVisible = false
                        viewModel.markGestureHintShown()
                    }
                    if (state.error == null) viewModel.toggleOverlay()
                }
            }
            .pointerInput(isTv, gesturesEnabled) {
                val threshold = 60.dp.toPx()
                var dragTotal = 0f
                var zone = DragZone.ZAP
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragTotal = 0f
                        zone = when {
                            // TV/remote mode is key-driven: a stray drag on a
                            // hybrid device only swipe-zaps, never adjusts AV.
                            isTv -> DragZone.ZAP
                            // Gestures switched off in settings: drags do nothing.
                            !gesturesEnabled -> DragZone.NONE
                            offset.x < size.width / 3f -> DragZone.BRIGHTNESS
                            offset.x > size.width * 2f / 3f -> DragZone.VOLUME
                            else -> DragZone.ZAP
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        when (zone) {
                            DragZone.NONE -> Unit
                            DragZone.ZAP -> dragTotal += dragAmount
                            // Dragging the full height sweeps ~150% so a
                            // comfortable half-screen swipe covers most of the range.
                            DragZone.VOLUME ->
                                adjuster.volumeBy(-dragAmount / size.height * 1.5f)
                            DragZone.BRIGHTNESS ->
                                adjuster.brightnessBy(-dragAmount / size.height * 1.5f)
                        }
                    },
                    onDragEnd = {
                        if (zone == DragZone.ZAP && state.error == null) {
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
                bookmarked = currentBookmarked,
                gesturesEnabled = gesturesEnabled,
                // Touch has no CH+/CH- or color keys: those actions live in the
                // overlay. TV drives them from the remote (LEFT/RIGHT/digits/0).
                onZapNext = viewModel::zapNext.takeUnless { isTv },
                onZapPrev = viewModel::zapPrev.takeUnless { isTv },
                onOpenChannelList = viewModel::toggleChannelList.takeUnless { isTv },
                onToggleBookmark = viewModel::toggleBookmark.takeUnless { isTv },
                onCycleScale = viewModel::cycleVideoScale.takeUnless { isTv },
                onToggleGestures = viewModel::togglePlayerGestures.takeUnless { isTv },
            )
        }

        // Once-per-session gesture demo: an animated finger swipe over each of
        // the three drag zones, dimming the video underneath.
        AnimatedVisibility(
            visible = hintVisible && !isTv && gesturesEnabled && state.error == null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            GestureDemo()
        }

        // Volume/brightness HUD while (and briefly after) an edge drag.
        adjustHud?.let { hud ->
            Text(
                text = "${stringResource(hud.labelRes)} ${hud.percent}%",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 7.dp),
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

        // Transient confirmation after flipping the gestures quick toggle.
        gestureFlash?.let { enabled ->
            Text(
                text = stringResource(
                    if (enabled) R.string.player_gestures_on else R.string.player_gestures_off,
                ),
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
            query = panelQuery,
            onQueryChange = viewModel::onPanelQueryChange,
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
    bookmarked: Boolean = false,
    gesturesEnabled: Boolean = true,
    onZapNext: (() -> Unit)? = null,
    onZapPrev: (() -> Unit)? = null,
    onOpenChannelList: (() -> Unit)? = null,
    onToggleBookmark: (() -> Unit)? = null,
    onCycleScale: (() -> Unit)? = null,
    onToggleGestures: (() -> Unit)? = null,
) {
    // Narrow (portrait phone) windows can't fit the info and five buttons on
    // one line — the action row moves under the text so nothing gets clipped.
    val stackActions = isCompactWidth()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                ),
            )
            .padding(
                horizontal = if (stackActions) 20.dp else 48.dp,
                vertical = if (stackActions) 18.dp else 30.dp,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
            if (!stackActions) {
                OverlayActions(
                    bookmarked = bookmarked,
                    gesturesEnabled = gesturesEnabled,
                    onOpenChannelList = onOpenChannelList,
                    onToggleBookmark = onToggleBookmark,
                    onCycleScale = onCycleScale,
                    onToggleGestures = onToggleGestures,
                    onZapNext = onZapNext,
                    onZapPrev = onZapPrev,
                )
            }
        }
        if (stackActions) {
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OverlayActions(
                    bookmarked = bookmarked,
                    gesturesEnabled = gesturesEnabled,
                    onOpenChannelList = onOpenChannelList,
                    onToggleBookmark = onToggleBookmark,
                    onCycleScale = onCycleScale,
                    onToggleGestures = onToggleGestures,
                    onZapNext = onZapNext,
                    onZapPrev = onZapPrev,
                )
            }
        }
        }
    }
}

// The overlay's action buttons, emitted into whichever row hosts them.
@Composable
private fun OverlayActions(
    bookmarked: Boolean,
    gesturesEnabled: Boolean,
    onOpenChannelList: (() -> Unit)?,
    onToggleBookmark: (() -> Unit)?,
    onCycleScale: (() -> Unit)?,
    onToggleGestures: (() -> Unit)?,
    onZapNext: (() -> Unit)?,
    onZapPrev: (() -> Unit)?,
) {
    onOpenChannelList?.let {
        ZapButton(
            icon = Icons.AutoMirrored.Filled.List,
            contentDescription = stringResource(R.string.player_channel_list),
            onClick = it,
        )
        Spacer(Modifier.width(12.dp))
    }
    onToggleBookmark?.let {
        ZapButton(
            icon = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = stringResource(
                if (bookmarked) R.string.action_remove_bookmark else R.string.action_add_bookmark,
            ),
            tint = if (bookmarked) MaterialTheme.colorScheme.primary else Color.White,
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
    onToggleGestures?.let {
        ZapButton(
            icon = Icons.Default.TouchApp,
            contentDescription = stringResource(
                if (gesturesEnabled) R.string.player_gestures_on else R.string.player_gestures_off,
            ),
            tint = if (gesturesEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f),
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

// Compact search input for the picker: no autofocus (scrolling is the primary
// action; the field is one focus step above the list for those who want it).
@Composable
private fun PanelSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Color.White.copy(alpha = 0.08f), MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) {
            Text(
                text = stringResource(R.string.player_search_channels),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// The once-per-session gesture demo: three columns matching the drag zones,
// each with an animated finger dot sweeping its track.
@Composable
private fun GestureDemo() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.40f)),
    ) {
        DemoZone(label = stringResource(R.string.player_brightness), modifier = Modifier.weight(1f))
        DemoZone(label = stringResource(R.string.player_hint_channel), modifier = Modifier.weight(1f))
        DemoZone(label = stringResource(R.string.player_volume), modifier = Modifier.weight(1f))
    }
}

// One demo zone: the swipe animation above a labelled pill.
@Composable
private fun DemoZone(label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        SwipeFinger()
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

// A finger dot sweeping up and down a vertical track between two arrows.
@Composable
private fun SwipeFinger() {
    val transition = rememberInfiniteTransition(label = "swipeDemo")
    val travel by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "swipeTravel",
    )
    Box(
        modifier = Modifier
            .height(96.dp)
            .width(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.18f), CircleShape),
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(18.dp),
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(18.dp),
        )
        Box(
            modifier = Modifier
                .offset(y = (travel * 26).dp)
                .size(16.dp)
                .background(Color.White.copy(alpha = 0.95f), CircleShape),
        )
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
    query: String,
    onQueryChange: (String) -> Unit,
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
        // Focus the first row once per panel opening — never re-steal focus
        // while search results reshuffle the list under the user's typing.
        var focusedThisOpen by remember { mutableStateOf(false) }
        LaunchedEffect(visible) { if (!visible) focusedThisOpen = false }
        LaunchedEffect(visible, items.itemCount) {
            if (visible && !focusedThisOpen && items.itemCount > 0) {
                focusedThisOpen = true
                runCatching { firstRowFocus.requestFocus() }
            }
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
            Spacer(Modifier.height(10.dp))
            // Two typed characters switch the list from the current category to
            // an FTS search across the whole playlist.
            PanelSearchField(query = query, onQueryChange = onQueryChange)
            Spacer(Modifier.height(10.dp))
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

// Which vertical-drag zone a gesture started in; NONE ignores the whole drag.
private enum class DragZone { BRIGHTNESS, ZAP, VOLUME, NONE }

/** The label and percentage shown while sliding volume or brightness. */
private data class AdjustHud(val labelRes: Int, val percent: Int)

/**
 * Applies edge-drag volume/brightness changes. Volume goes through the media
 * stream; brightness overrides this window only (never the system setting).
 * Fractions accumulate across a drag so tiny movements still make progress.
 */
private class AvAdjuster(
    private val audioManager: AudioManager,
    private val window: Window?,
    private val initialBrightness: () -> Float,
) {
    val hud = mutableStateOf<AdjustHud?>(null)

    private var volumeFraction = -1f
    private var brightnessFraction = -1f

    fun volumeBy(delta: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        if (volumeFraction < 0f) {
            volumeFraction = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        }
        volumeFraction = (volumeFraction + delta).coerceIn(0f, 1f)
        runCatching {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (volumeFraction * max).roundToInt(),
                0,
            )
        }
        hud.value = AdjustHud(R.string.player_volume, (volumeFraction * 100).roundToInt())
    }

    fun brightnessBy(delta: Float) {
        val target = window ?: return
        if (brightnessFraction < 0f) {
            val current = target.attributes.screenBrightness
            brightnessFraction = if (current >= 0f) current else initialBrightness()
        }
        brightnessFraction = (brightnessFraction + delta).coerceIn(MIN_BRIGHTNESS, 1f)
        target.attributes = target.attributes.apply { screenBrightness = brightnessFraction }
        hud.value = AdjustHud(R.string.player_brightness, (brightnessFraction * 100).roundToInt())
    }

    /** Clears the HUD only if it still shows [shown] — a newer drag keeps its label. */
    fun clearHud(shown: AdjustHud) {
        if (hud.value == shown) hud.value = null
    }

    private companion object {
        // Never allow a fully black screen — the user could not find the way back.
        const val MIN_BRIGHTNESS = 0.02f
    }
}

// Walks ContextWrappers to the hosting Activity (for window-level brightness).
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Current system brightness as a 0..1 fraction, for the first drag's baseline.
private fun Context.currentSystemBrightnessFraction(): Float = runCatching {
    Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
}.getOrDefault(0.5f).coerceIn(0f, 1f)

/** Circular translucent icon button used for touch actions in the info overlay. */
@Composable
private fun ZapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
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
            tint = tint,
            modifier = Modifier
                .align(Alignment.Center)
                .size(32.dp),
        )
    }
}
