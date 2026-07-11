package com.novaplay.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * Shared sizing rules for Live, Movies, and Series. Keeping these values in one
 * resolver prevents each screen from inventing different phone/tablet/TV
 * breakpoints and makes the behaviour unit-testable.
 */
@Immutable
data class CatalogLayoutSpec(
    val showCategoryRail: Boolean,
    val categoryRailWidthDp: Int,
    val posterMinWidthDp: Int,
    val gridSpacingDp: Int,
    val liveRowHeightDp: Int,
    val liveLogoSizeDp: Int,
)

fun calculateCatalogLayoutSpec(
    uiMode: ResolvedUiMode,
    widthClass: WindowWidthClass,
    widthDp: Int,
): CatalogLayoutSpec = when {
    uiMode == ResolvedUiMode.TV -> CatalogLayoutSpec(
        showCategoryRail = true,
        categoryRailWidthDp = 286,
        posterMinWidthDp = 150,
        gridSpacingDp = 16,
        liveRowHeightDp = 64,
        liveLogoSizeDp = 42,
    )

    widthClass == WindowWidthClass.COMPACT -> CatalogLayoutSpec(
        showCategoryRail = false,
        categoryRailWidthDp = 0,
        posterMinWidthDp = if (widthDp < 380) 112 else 124,
        gridSpacingDp = 12,
        liveRowHeightDp = 64,
        liveLogoSizeDp = 42,
    )

    widthClass == WindowWidthClass.MEDIUM -> CatalogLayoutSpec(
        showCategoryRail = true,
        categoryRailWidthDp = 220,
        posterMinWidthDp = 136,
        gridSpacingDp = 14,
        liveRowHeightDp = 64,
        liveLogoSizeDp = 42,
    )

    else -> CatalogLayoutSpec(
        showCategoryRail = true,
        categoryRailWidthDp = 260,
        posterMinWidthDp = 148,
        gridSpacingDp = 16,
        liveRowHeightDp = 68,
        liveLogoSizeDp = 44,
    )
}

@Composable
fun catalogLayoutSpec(): CatalogLayoutSpec {
    val info = appLayoutInfo()
    return calculateCatalogLayoutSpec(
        uiMode = info.uiMode,
        widthClass = info.widthClass,
        widthDp = info.widthDp,
    )
}
