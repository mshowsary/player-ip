package com.novaplay.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBufferPolicyTest {

    @Test
    fun lowRamDevicesGetSmallerCeilingsThanStandardDevices() {
        assertTrue(
            PlaybackBufferPolicy.liveBuffers(lowRamDevice = true).maxBufferMs <
                PlaybackBufferPolicy.liveBuffers(lowRamDevice = false).maxBufferMs,
        )
        assertTrue(
            PlaybackBufferPolicy.vodBuffers(lowRamDevice = true).maxBufferMs <
                PlaybackBufferPolicy.vodBuffers(lowRamDevice = false).maxBufferMs,
        )
    }

    @Test
    fun zapStartupThresholdIsIdenticalAcrossDeviceClasses() {
        assertEquals(
            PlaybackBufferPolicy.liveBuffers(lowRamDevice = false).playbackBufferMs,
            PlaybackBufferPolicy.liveBuffers(lowRamDevice = true).playbackBufferMs,
        )
    }

    @Test
    fun specsAreInternallyConsistent() {
        for (spec in listOf(
            PlaybackBufferPolicy.liveBuffers(true),
            PlaybackBufferPolicy.liveBuffers(false),
            PlaybackBufferPolicy.vodBuffers(true),
            PlaybackBufferPolicy.vodBuffers(false),
        )) {
            // ExoPlayer requires playback thresholds <= min <= max.
            assertTrue(spec.playbackBufferMs <= spec.minBufferMs)
            assertTrue(spec.rebufferMs <= spec.minBufferMs)
            assertTrue(spec.minBufferMs <= spec.maxBufferMs)
        }
    }
}
