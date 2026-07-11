package com.novaplay.tv.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun compactBelow600Dp() {
        assertEquals(WindowWidthClass.COMPACT, calculateWindowWidthClass(320))
        assertEquals(WindowWidthClass.COMPACT, calculateWindowWidthClass(599))
    }

    @Test
    fun mediumFrom600Until840Dp() {
        assertEquals(WindowWidthClass.MEDIUM, calculateWindowWidthClass(600))
        assertEquals(WindowWidthClass.MEDIUM, calculateWindowWidthClass(839))
    }

    @Test
    fun expandedFrom840Dp() {
        assertEquals(WindowWidthClass.EXPANDED, calculateWindowWidthClass(840))
        assertEquals(WindowWidthClass.EXPANDED, calculateWindowWidthClass(1280))
    }
}
