package com.novaplay.tv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.novaplay.tv.data.prefs.AccentTheme

/**
 * The resolved accent pair for the current composition. Reading it (instead of
 * the static Color.kt defaults) is what lets the user's accent choice restyle
 * gradients and washes live, on top of whatever the brand pack ships.
 */
@Immutable
class NovaAccents(
    val accent: Color,
    val accentAlt: Color,
) {
    /** The signature sweep, rebuilt for whichever pair is active. */
    val gradient: Brush = Brush.horizontalGradient(listOf(accent, accentAlt))
}

/** Provided by [NovaPlayTheme]; defaults to the brand pack's pair. */
val LocalNovaAccents = staticCompositionLocalOf { NovaAccents(NovaAccent, NovaAccentAlt) }

/** Maps a preference choice onto concrete colors; BRAND keeps the pack colors. */
fun AccentTheme.resolveAccents(): NovaAccents {
    val hex = accentHex
    val altHex = accentAltHex
    return if (hex == null || altHex == null) {
        NovaAccents(NovaAccent, NovaAccentAlt)
    } else {
        NovaAccents(
            accent = parseAccentHex(hex, NovaAccent),
            accentAlt = parseAccentHex(altHex, NovaAccentAlt),
        )
    }
}
