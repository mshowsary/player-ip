package com.novaplay.tv.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogLayoutSpecTest {

    @Test
    fun compactTouchUsesChipsAndLargeEnoughPosters() {
        val spec = calculateCatalogLayoutSpec(
            uiMode = ResolvedUiMode.TOUCH,
            widthClass = WindowWidthClass.COMPACT,
            widthDp = 360,
        )

        assertFalse(spec.showCategoryRail)
        assertTrue(spec.posterMinWidthDp >= 112)
        assertTrue(spec.liveRowHeightDp >= 64)
    }

    @Test
    fun mediumTouchUsesFixedRail() {
        val spec = calculateCatalogLayoutSpec(
            uiMode = ResolvedUiMode.TOUCH,
            widthClass = WindowWidthClass.MEDIUM,
            widthDp = 700,
        )

        assertTrue(spec.showCategoryRail)
        assertEquals(220, spec.categoryRailWidthDp)
        assertEquals(136, spec.posterMinWidthDp)
    }

    @Test
    fun tvAlwaysUsesRemoteFriendlyRailAndSpacing() {
        val spec = calculateCatalogLayoutSpec(
            uiMode = ResolvedUiMode.TV,
            widthClass = WindowWidthClass.COMPACT,
            widthDp = 540,
        )

        assertTrue(spec.showCategoryRail)
        assertEquals(286, spec.categoryRailWidthDp)
        assertTrue(spec.posterMinWidthDp >= 150)
        assertTrue(spec.liveLogoSizeDp >= 42)
    }
}
