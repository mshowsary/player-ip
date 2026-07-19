package com.novaplay.tv.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncProgressTest {

    @Test
    fun `percent is null when the server sent no content length`() {
        assertNull(SyncProgress(bytesRead = 5_000, totalBytes = -1).percent)
        assertNull(SyncProgress(bytesRead = 5_000, totalBytes = 0).percent)
    }

    @Test
    fun `percent is the whole-number share of the total`() {
        assertEquals(0, SyncProgress(bytesRead = 0, totalBytes = 100).percent)
        assertEquals(50, SyncProgress(bytesRead = 512, totalBytes = 1024).percent)
        assertEquals(100, SyncProgress(bytesRead = 1024, totalBytes = 1024).percent)
    }

    @Test
    fun `percent stays clamped when a server under-reports the total`() {
        // Some panels send a Content-Length smaller than the actual body.
        assertEquals(100, SyncProgress(bytesRead = 2048, totalBytes = 1024).percent)
    }

    @Test
    fun `large catalogues do not overflow the percent computation`() {
        val fourGb = 4L * 1024 * 1024 * 1024
        assertEquals(50, SyncProgress(bytesRead = fourGb / 2, totalBytes = fourGb).percent)
    }
}
