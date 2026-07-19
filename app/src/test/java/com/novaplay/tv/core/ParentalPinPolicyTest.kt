package com.novaplay.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalPinPolicyTest {

    @Test
    fun `only four digit pins are valid`() {
        assertTrue(ParentalPinPolicy.isValidPin("0000"))
        assertTrue(ParentalPinPolicy.isValidPin("4821"))
        assertFalse(ParentalPinPolicy.isValidPin(""))
        assertFalse(ParentalPinPolicy.isValidPin("123"))
        assertFalse(ParentalPinPolicy.isValidPin("12345"))
        assertFalse(ParentalPinPolicy.isValidPin("12a4"))
        assertFalse(ParentalPinPolicy.isValidPin("١٢٣٤")) // non-ASCII digits are refused
    }

    @Test
    fun `encode then verify roundtrip`() {
        val stored = ParentalPinPolicy.encode("4821")
        assertTrue(ParentalPinPolicy.isConfigured(stored))
        assertTrue(ParentalPinPolicy.verify("4821", stored))
        assertFalse(ParentalPinPolicy.verify("4822", stored))
        assertFalse(ParentalPinPolicy.verify("0000", stored))
    }

    @Test
    fun `same pin encodes differently per salt but both verify`() {
        val a = ParentalPinPolicy.encode("1111")
        val b = ParentalPinPolicy.encode("1111")
        assertNotEquals(a, b)
        assertTrue(ParentalPinPolicy.verify("1111", a))
        assertTrue(ParentalPinPolicy.verify("1111", b))
    }

    @Test
    fun `absent or malformed stored values never verify and read as unconfigured`() {
        for (stored in listOf(null, "", "1234", "v1:salt", "v2:salt:hash", "v1::", "v1:salt:short")) {
            assertFalse("verify($stored)", ParentalPinPolicy.verify("1234", stored))
            assertFalse("isConfigured($stored)", ParentalPinPolicy.isConfigured(stored))
        }
    }

    @Test
    fun `tampered digest fails verification`() {
        val stored = ParentalPinPolicy.encode("4821")
        val tampered = stored.dropLast(1) + if (stored.last() == '0') '1' else '0'
        assertFalse(ParentalPinPolicy.verify("4821", tampered))
    }

    @Test
    fun `invalid pin input never verifies even against a real encoding`() {
        val stored = ParentalPinPolicy.encode("4821")
        assertFalse(ParentalPinPolicy.verify("48211", stored))
        assertFalse(ParentalPinPolicy.verify("", stored))
    }

    @Test
    fun `lock keys are scoped by type playlist and provider id`() {
        assertEquals("live:3:17", ParentalPinPolicy.lockKey("live", 3L, "17"))
        assertNotEquals(
            ParentalPinPolicy.lockKey("live", 3L, "17"),
            ParentalPinPolicy.lockKey("vod", 3L, "17"),
        )
        assertNotEquals(
            ParentalPinPolicy.lockKey("live", 3L, "17"),
            ParentalPinPolicy.lockKey("live", 4L, "17"),
        )
    }
}
