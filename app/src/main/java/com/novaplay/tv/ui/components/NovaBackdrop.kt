package com.novaplay.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.novaplay.tv.ui.theme.NovaAccent
import com.novaplay.tv.ui.theme.NovaAccentAlt
import com.novaplay.tv.ui.theme.NovaBackground

// The app-wide "aurora" backdrop: deep space base with a faint cyan bloom in
// the top-left and a violet bloom in the bottom-right. Static brushes drawn in
// one node — no recomposition, no animation, cheap on low-end TV boxes.
@Composable
fun NovaBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(NovaBackground)
                val maxDim = maxOf(size.width, size.height)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(NovaAccent.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(size.width * 0.10f, size.height * 0.05f),
                        radius = maxDim * 0.65f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(NovaAccentAlt.copy(alpha = 0.09f), Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.95f),
                        radius = maxDim * 0.55f,
                    ),
                )
            },
        content = content,
    )
}
