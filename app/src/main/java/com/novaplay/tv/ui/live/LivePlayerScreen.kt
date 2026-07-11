package com.novaplay.tv.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.border
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.novaplay.tv.ui.components.ErrorState
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.player.BufferingIndicator
import com.novaplay.tv.ui.player.PlayerSurface
import com.novaplay.tv.ui.theme.NovaAccentGradient
import com.novaplay.tv.ui.theme.isTvDevice

@Composable
fun LivePlayerScreen(
    viewModel: LivePlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subtitleStyle by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val rootFocus = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isTv = isTvDevice()

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
                when (event.key) {
                    Key.DirectionUp -> {
                        viewModel.zapNext()
                        true
                    }
                    Key.DirectionDown -> {
                        viewModel.zapPrev()
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
        )

        if (state.buffering && state.error == null) {
            BufferingIndicator(
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center),
            )
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
                // Touch has no CH+/CH- keys: zap buttons live in the overlay.
                onZapNext = viewModel::zapNext.takeUnless { isTv },
                onZapPrev = viewModel::zapPrev.takeUnless { isTv },
            )
        }

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

@Composable
private fun ChannelInfoBar(
    number: Int?,
    name: String,
    categoryName: String?,
    onZapNext: (() -> Unit)? = null,
    onZapPrev: (() -> Unit)? = null,
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
