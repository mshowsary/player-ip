package com.novaplay.tv.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DataSizeTest {

    @Test
    fun `negative amounts render as empty`() {
        assertEquals("", DataSize.format(-1))
    }

    @Test
    fun `bytes below one kilobyte keep the byte unit`() {
        assertEquals("0 B", DataSize.format(0))
        assertEquals("1023 B", DataSize.format(1023))
    }

    @Test
    fun `kilobytes are whole numbers`() {
        assertEquals("1 KB", DataSize.format(1024))
        assertEquals("845 KB", DataSize.format(845L * 1024))
        assertEquals("1023 KB", DataSize.format(1024L * 1024 - 1))
    }

    @Test
    fun `megabytes and gigabytes keep one decimal`() {
        assertEquals("1.0 MB", DataSize.format(1024L * 1024))
        assertEquals("12.5 MB", DataSize.format(12L * 1024 * 1024 + 512 * 1024))
        assertEquals("1.0 GB", DataSize.format(1024L * 1024 * 1024))
        assertEquals("2.5 GB", DataSize.format(2L * 1024 * 1024 * 1024 + 512L * 1024 * 1024))
    }
}
