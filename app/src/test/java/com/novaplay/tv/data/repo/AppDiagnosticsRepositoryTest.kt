package com.novaplay.tv.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDiagnosticsRepositoryTest {

    @Test
    fun byteFormattingUsesReadableUnits() {
        assertEquals("512 B", AppDiagnosticsRepository.formatBytes(512L))
        assertEquals("1.5 KB", AppDiagnosticsRepository.formatBytes(1_536L))
        assertEquals("2.0 MB", AppDiagnosticsRepository.formatBytes(2L * 1_024 * 1_024))
    }

    @Test
    fun durationFormattingRemainsCompact() {
        assertEquals("450 ms", AppDiagnosticsRepository.formatDuration(450L))
        assertEquals("4.2 s", AppDiagnosticsRepository.formatDuration(4_200L))
        assertEquals("2 min 05 s", AppDiagnosticsRepository.formatDuration(125_000L))
    }
}
