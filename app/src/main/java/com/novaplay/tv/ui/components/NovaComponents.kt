package com.novaplay.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.novaplay.tv.ui.theme.isTvDevice
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

// The app's signature focus treatment: scale + accent border + soft glow,
// identical on every focusable surface. All interactive elements route
// through this composable so the cursor is never ambiguous.
// tv-material's Surface only reacts to D-pad ENTER, so touch (phone/tablet)
// is wired up here: taps and long-presses land on the same callbacks, and
// press interactions are forwarded so the pressed colors show under a finger.
@Composable
fun NovaClickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    focusedContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    focusedScale: Float = 1.08f,
    onLongClick: (() -> Unit)? = null,
    restingBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.pointerInput(onLongClick != null) {
            detectTapGestures(
                onPress = { offset ->
                    val press = PressInteraction.Press(offset)
                    interactionSource.emit(press)
                    if (tryAwaitRelease()) {
                        interactionSource.emit(PressInteraction.Release(press))
                    } else {
                        interactionSource.emit(PressInteraction.Cancel(press))
                    }
                },
                onTap = { currentOnClick() },
                onLongPress = if (onLongClick != null) {
                    { currentOnLongClick?.invoke() }
                } else {
                    null
                },
            )
        },
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = focusedContainerColor,
            pressedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            // Hairline edge at rest gives panels definition on the deep
            // backdrop; focus swaps it for the full accent treatment.
            border = if (restingBorder) {
                Border(
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.border.copy(alpha = 0.55f)),
                    shape = shape,
                )
            } else {
                Border.None
            },
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = shape,
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                elevation = 14.dp,
            ),
        ),
        content = content,
    )
}

// Primary CTA: accent-filled when focused.
@Composable
fun NovaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    NovaClickable(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        containerColor = if (prominent) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        focusedContainerColor = MaterialTheme.colorScheme.primary,
        focusedScale = 1.06f,
    ) {
        FocusAwareButtonLabel(text = text, prominent = prominent)
    }
}

@Composable
private fun BoxScope.FocusAwareButtonLabel(text: String, prominent: Boolean) {
    // Secondary actions use a compact one-line label so paired buttons never
    // break a final word onto its own line on narrow phones or dialogs.
    Text(
        text = text,
        style = if (prominent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = if (prominent) 18.dp else 12.dp, vertical = 12.dp),
    )
}

@Composable
fun PulsingDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    Box(
        modifier = modifier
            .alpha(alpha)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primary),
    )
}

// Loading placeholder with a slow shimmer sweep.
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
        ),
        label = "shimmerProgress",
    )
    val base = MaterialTheme.colorScheme.surface
    val highlight = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(x = progress * 1200f - 600f, y = 0f),
                    end = Offset(x = progress * 1200f, y = 200f),
                ),
            ),
    )
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryFocus = remember { FocusRequester() }
    val isTv = isTvDevice()
    LaunchedEffect(Unit) { if (isTv) retryFocus.requestFocus() }
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        NovaButton(
            text = "Retry",
            onClick = onRetry,
            modifier = Modifier
                .padding(top = 24.dp)
                .focusRequester(retryFocus),
        )
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// Modal panel shared by TV (focusable items) and touch (tappable items,
// outside-tap dismisses). Sized down on narrow phone screens.
@Composable
fun NovaDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                // Slightly translucent panel with a glass edge over the dim.
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                shape = MaterialTheme.shapes.large,
            ),
            modifier = Modifier.width(if (screenWidth < 460.dp) screenWidth - 40.dp else 420.dp),
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
                content()
            }
        }
    }
}
