package com.novaplay.tv.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import com.novaplay.tv.data.prefs.SubtitleEdge
import com.novaplay.tv.data.prefs.SubtitleStyle

// Video surface shared by the live and VOD players. Controller is disabled —
// all controls are Compose overlays. Subtitle appearance is applied globally
// from settings via CaptionStyleCompat.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(
    player: Player,
    subtitleStyle: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setBackgroundColor(android.graphics.Color.BLACK)
                // Hold the screen awake while a player is on screen — without
                // this the display dims and the device locks mid-playback.
                keepScreenOn = true
            }
        },
        update = { view ->
            view.player = player
            view.subtitleView?.apply {
                // Ignore styles baked into the stream so user settings always win.
                setApplyEmbeddedStyles(false)
                setFractionalTextSize(subtitleStyle.size.fraction)
                setStyle(subtitleStyle.toCaptionStyle())
            }
        },
        modifier = modifier,
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun SubtitleStyle.toCaptionStyle(): CaptionStyleCompat =
    CaptionStyleCompat(
        color.argb,
        background.argb,
        android.graphics.Color.TRANSPARENT, // window color
        when (edge) {
            SubtitleEdge.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
            SubtitleEdge.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            SubtitleEdge.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        },
        android.graphics.Color.BLACK,
        null, // default typeface
    )

// Minimal rotating-arc spinner (tv-material ships no progress indicator).
@Composable
fun BufferingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "buffering")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "bufferingRotation",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 4.dp.toPx()),
            size = Size(size.width, size.height),
        )
    }
}
