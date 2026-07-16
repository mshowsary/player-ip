package com.novaplay.tv.ui.player

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackGatePolicyTest {

    @Test
    fun `managed playlists never gate`() {
        assertNull(
            PlaybackGatePolicy.blockMessage(
                isPersonalPlaylist = false,
                licenseStatus = "trial_expired",
                stale = false,
                deviceCode = "NP-K4M2-Q9XW",
            ),
        )
    }

    @Test
    fun `stale or unknown license state fails open`() {
        assertNull(PlaybackGatePolicy.blockMessage(true, "trial_expired", stale = true, deviceCode = null))
        assertNull(PlaybackGatePolicy.blockMessage(true, null, stale = false, deviceCode = null))
        assertNull(PlaybackGatePolicy.blockMessage(true, "weird_future_status", false, null))
    }

    @Test
    fun `active states play`() {
        assertNull(PlaybackGatePolicy.blockMessage(true, "trial", false, "NP-K4M2-Q9XW"))
        assertNull(PlaybackGatePolicy.blockMessage(true, "licensed", false, "NP-K4M2-Q9XW"))
    }

    @Test
    fun `expired trial blocks with the device code in the message`() {
        val message = PlaybackGatePolicy.blockMessage(true, "trial_expired", false, "NP-K4M2-Q9XW")
        assertTrue(message!!.contains("trial has ended"))
        assertTrue(message.contains("NP-K4M2-Q9XW"))
    }

    @Test
    fun `revoked license blocks without crashing when no code is known`() {
        val message = PlaybackGatePolicy.blockMessage(true, "revoked", false, null)
        assertTrue(message!!.contains("another device"))
    }
}
