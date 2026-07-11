package com.novaplay.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Overscan-safe outer margins for every full screen.
val OverscanHorizontal = 48.dp
val OverscanVertical = 27.dp

/** Root theme for every screen: applies the Nova material theme and a readable default content color. */
@Composable
fun NovaPlayTheme(content: @Composable () -> Unit) {
    NovaMaterialTheme {
        // Text outside any Surface must still default to a readable color on
        // the dark background.
        CompositionLocalProvider(
            LocalContentColor provides NovaOnSurface,
            content = content,
        )
    }
}

// Maps the Nova palette onto tv-material's dark color scheme; the app is
// dark-only, so no light variant exists.
@Composable
private fun NovaMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NovaAccent,
            onPrimary = NovaOnAccent,
            primaryContainer = NovaAccent,
            onPrimaryContainer = NovaOnAccent,
            secondary = NovaAccent,
            onSecondary = NovaOnAccent,
            background = NovaBackground,
            onBackground = NovaOnSurface,
            surface = NovaSurface,
            onSurface = NovaOnSurface,
            surfaceVariant = NovaSurfaceBright,
            onSurfaceVariant = NovaOnSurfaceMuted,
            error = NovaError,
            onError = NovaOnError,
            border = NovaBorder,
            borderVariant = NovaBorder,
        ),
        typography = NovaTypography,
        content = content,
    )
}
