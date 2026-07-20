package com.novaplay.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalLockoutPolicyTest {

    private val now = 1_000_000L

    @Test
    fun `the first attempts are free`() {
        for (fails in 0 until ParentalLockoutPolicy.FREE_ATTEMPTS) {
            assertEquals(0L, ParentalLockoutPolicy.remainingLockMs(fails, now, now))
        }
    }

    @Test
    fun `the fifth failure starts a 30 second lockout`() {
        assertEquals(30_000L, ParentalLockoutPolicy.remainingLockMs(5, now, now))
        assertEquals(10_000L, ParentalLockoutPolicy.remainingLockMs(5, now, now + 20_000L))
        assertEquals(0L, ParentalLockoutPolicy.remainingLockMs(5, now, now + 30_000L))
    }

    @Test
    fun `lockouts double and cap at eight minutes`() {
        assertEquals(60_000L, ParentalLockoutPolicy.remainingLockMs(6, now, now))
        assertEquals(120_000L, ParentalLockoutPolicy.remainingLockMs(7, now, now))
        assertEquals(480_000L, ParentalLockoutPolicy.remainingLockMs(9, now, now))
        // Far beyond the cap it stays at eight minutes, never overflows.
        assertEquals(480_000L, ParentalLockoutPolicy.remainingLockMs(500, now, now))
    }

    @Test
    fun `an elapsed lockout allows the next attempt`() {
        assertTrue(ParentalLockoutPolicy.remainingLockMs(8, now, now + 300_000L) == 0L)
    }
}
