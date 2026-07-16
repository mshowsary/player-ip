package com.novaplay.tv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.novaplay.tv.BuildConfig

// Deep space-blue base — darker and cooler than plain black, so the accent
// blooms and glass panels have something to glow against.
val NovaBackground = Color(0xFF060913)
val NovaSurface = Color(0xFF0E1524)
val NovaSurfaceBright = Color(0xFF1B2540)
val NovaOnSurface = Color(0xFFE9EFFB)
val NovaOnSurfaceMuted = Color(0xFF8C99B5)

// Parses a brand pack's #RRGGBB; the build validates the format, the fallback
// only guards against a corrupted BuildConfig ever producing an unthemed UI.
private fun brandColor(hex: String, fallback: Long): Color = runCatching {
    Color(0xFF000000 or hex.removePrefix("#").toLong(16))
}.getOrElse { Color(fallback) }

// Dual accent, supplied by the white-label brand pack. Default brand: electric
// cyan for focus/interaction, violet as its gradient partner — the signature sweep.
val NovaAccent = brandColor(BuildConfig.BRAND_ACCENT, 0xFF22D3EE)
val NovaOnAccent = Color(0xFF00252B)
val NovaAccentAlt = brandColor(BuildConfig.BRAND_ACCENT_ALT, 0xFF8B5CF6)

val NovaError = Color(0xFFF87171)
val NovaOnError = Color(0xFF2B0505)
val NovaBorder = Color(0xFF243354)

// Shared brushes — build once, reuse everywhere.
val NovaAccentGradient = Brush.horizontalGradient(listOf(NovaAccent, NovaAccentAlt))
val NovaGlassHighlight = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
)
