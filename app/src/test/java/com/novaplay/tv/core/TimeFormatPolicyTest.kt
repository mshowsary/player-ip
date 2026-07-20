package com.novaplay.tv.core

import com.novaplay.tv.data.prefs.TimeFormatPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormatPolicyTest {

    @Test
    fun `auto follows the device setting`() {
        assertTrue(TimeFormatPolicy.use24Hour(TimeFormatPreference.AUTO, deviceUses24 = true))
        assertFalse(TimeFormatPolicy.use24Hour(TimeFormatPreference.AUTO, deviceUses24 = false))
    }

    @Test
    fun `explicit choices override the device`() {
        assertFalse(TimeFormatPolicy.use24Hour(TimeFormatPreference.H12, deviceUses24 = true))
        assertTrue(TimeFormatPolicy.use24Hour(TimeFormatPreference.H24, deviceUses24 = false))
    }

    @Test
    fun `patterns match the chosen style`() {
        assertEquals("HH:mm", TimeFormatPolicy.timePattern(use24 = true))
        assertEquals("h:mm a", TimeFormatPolicy.timePattern(use24 = false))
        assertEquals("MMM d, HH:mm", TimeFormatPolicy.dateTimePattern(use24 = true))
        assertEquals("MMM d, h:mm a", TimeFormatPolicy.dateTimePattern(use24 = false))
    }
}
