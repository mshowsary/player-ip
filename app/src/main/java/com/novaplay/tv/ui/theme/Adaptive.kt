package com.novaplay.tv.ui.theme

import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.novaplay.tv.data.prefs.UiModePreference

/** Material-style window width buckets: <600 dp, 600–839 dp, and 840 dp or wider. */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

/** Input model after applying the user's UiModePreference: finger-driven touch vs D-pad TV. */
enum class ResolvedUiMode {
    TOUCH,
    TV,
}

/**
 * Snapshot of the current app window: resolved input mode, width class, and raw
 * dp dimensions. Published by [ProvideAdaptiveEnvironment] via a composition
 * local and read by every Adaptive/Responsive screen.
 */
@Immutable
data class AppLayoutInfo(
    val uiMode: ResolvedUiMode,
    val widthClass: WindowWidthClass,
    val widthDp: Int,
    val heightDp: Int,
) {
    val isLandscape: Boolean get() = widthDp > heightDp
}

// Phone-sized fallback so previews and tests render without the provider.
private val LocalAppLayoutInfo = staticCompositionLocalOf {
    AppLayoutInfo(
        uiMode = ResolvedUiMode.TOUCH,
        widthClass = WindowWidthClass.COMPACT,
        widthDp = 360,
        heightDp = 640,
    )
}

/**
 * Resolves the UI from the current app window, not the physical display. This
 * means rotation, split-screen, foldables, tablets, and resizable ChromeOS
 * windows update the layout immediately. A manual override handles TV boxes
 * that report their hardware type incorrectly.
 */
@Composable
fun ProvideAdaptiveEnvironment(
    preference: UiModePreference,
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val hardwareLooksLikeTv =
        (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    val resolvedMode = when (preference) {
        UiModePreference.AUTO -> if (hardwareLooksLikeTv) ResolvedUiMode.TV else ResolvedUiMode.TOUCH
        UiModePreference.TOUCH -> ResolvedUiMode.TOUCH
        UiModePreference.TV -> ResolvedUiMode.TV
    }

    val layoutInfo = AppLayoutInfo(
        uiMode = resolvedMode,
        widthClass = calculateWindowWidthClass(configuration.screenWidthDp),
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
    )

    CompositionLocalProvider(LocalAppLayoutInfo provides layoutInfo, content = content)
}

/** Buckets a window width into Material breakpoints: <600 compact, <840 medium, else expanded. */
fun calculateWindowWidthClass(widthDp: Int): WindowWidthClass = when {
    widthDp < 600 -> WindowWidthClass.COMPACT
    widthDp < 840 -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}

/** Current [AppLayoutInfo] from the nearest [ProvideAdaptiveEnvironment]. */
@Composable
fun appLayoutInfo(): AppLayoutInfo = LocalAppLayoutInfo.current

/** True when the resolved UI mode is TV: D-pad navigation, overscan margins, 10-foot sizing. */
@Composable
fun isTvDevice(): Boolean = appLayoutInfo().uiMode == ResolvedUiMode.TV

/** True for windows under 600 dp wide (phones, split-screen slices). */
@Composable
fun isCompactWidth(): Boolean = appLayoutInfo().widthClass == WindowWidthClass.COMPACT

/** True for windows 600–839 dp wide (small tablets, foldables, landscape phones). */
@Composable
fun isMediumWidth(): Boolean = appLayoutInfo().widthClass == WindowWidthClass.MEDIUM

/** True for windows 840 dp and wider (large tablets, desktop-class windows). */
@Composable
fun isExpandedWidth(): Boolean = appLayoutInfo().widthClass == WindowWidthClass.EXPANDED

// TV keeps overscan-safe margins. Touch spacing grows gradually with the
// available window instead of jumping from phone directly to TV dimensions.
@Composable
fun screenPadding(): PaddingValues {
    val info = appLayoutInfo()
    return when {
        info.uiMode == ResolvedUiMode.TV ->
            PaddingValues(horizontal = OverscanHorizontal, vertical = OverscanVertical)
        info.widthClass == WindowWidthClass.COMPACT ->
            PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        info.widthClass == WindowWidthClass.MEDIUM ->
            PaddingValues(horizontal = 24.dp, vertical = 18.dp)
        else ->
            PaddingValues(horizontal = 32.dp, vertical = 24.dp)
    }
}
