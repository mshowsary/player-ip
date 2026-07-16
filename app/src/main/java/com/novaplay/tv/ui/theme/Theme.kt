package com.novaplay.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.novaplay.tv.data.prefs.AccentTheme

// Overscan-safe outer margins for every full screen.
val OverscanHorizontal = 48.dp
val OverscanVertical = 27.dp

/**
 * Root theme for every screen: resolves the user's accent choice on top of the
 * brand defaults, then applies the Nova material theme and a readable default
 * content color.
 */
@Composable
fun NovaPlayTheme(
    accentTheme: AccentTheme = AccentTheme.BRAND,
    content: @Composable () -> Unit,
) {
    val accents = remember(accentTheme) { accentTheme.resolveAccents() }
    CompositionLocalProvider(LocalNovaAccents provides accents) {
        NovaMaterialTheme(accents) {
            // Text outside any Surface must still default to a readable color
            // on the dark background.
            CompositionLocalProvider(
                LocalContentColor provides NovaOnSurface,
                content = content,
            )
        }
    }
}

// Maps the Nova palette onto tv-material's dark color scheme; the app is
// dark-only, so no light variant exists.
@Composable
private fun NovaMaterialTheme(
    accents: NovaAccents,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accents.accent,
            onPrimary = NovaOnAccent,
            primaryContainer = accents.accent,
            onPrimaryContainer = NovaOnAccent,
            secondary = accents.accentAlt,
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
