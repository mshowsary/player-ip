package com.novaplay.tv.ui.activation

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivationPresentationTest {

    @Test
    fun countdownUsesStableMinuteSecondFormatting() {
        assertEquals("0:00", formatCountdown(-1L))
        assertEquals("0:09", formatCountdown(9L))
        assertEquals("1:05", formatCountdown(65L))
        assertEquals("10:00", formatCountdown(600L))
    }

    @Test
    fun portalAddressRemovesSchemeAndTrailingSlashForDisplay() {
        assertEquals(
            "portal.example.com/activate",
            displayPortalAddress("https://portal.example.com/activate/"),
        )
        assertEquals("portal.example.com", displayPortalAddress("http://portal.example.com"))
    }

    @Test
    fun blankPortalAddressHasReadableFallback() {
        assertEquals("Portal address unavailable", displayPortalAddress(""))
    }
}
