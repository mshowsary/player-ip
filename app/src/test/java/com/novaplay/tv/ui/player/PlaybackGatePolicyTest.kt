package com.novaplay.tv.ui.player

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class PlaybackGatePolicyTest {

    private val now = 1_800_000_000_000L
    private val hour = TimeUnit.HOURS.toMillis(1)
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun gate(
        personal: Boolean = true,
        status: String? = null,
        daysLeft: Int = 0,
        verifiedAt: Long? = null,
        firstAttemptAt: Long? = null,
        at: Long = now,
    ) = PlaybackGatePolicy.blockMessage(
        isPersonalPlaylist = personal,
        licenseStatus = status,
        daysLeftAtVerification = daysLeft,
        verifiedAtMs = verifiedAt,
        firstAttemptAtMs = firstAttemptAt,
        nowMs = at,
        deviceCode = "NP-TEST-CODE",
    )

    @Test
    fun `managed playlists never gate`() {
        assertNull(gate(personal = false, status = "trial_expired", verifiedAt = now - 100 * day))
        assertNull(gate(personal = false, status = "revoked", verifiedAt = now))
    }

    @Test
    fun `builds where licensing never engaged do not gate`() {
        assertNull(gate(status = null, verifiedAt = null, firstAttemptAt = null))
    }

    @Test
    fun `never verified installs play through the bootstrap grace then require a check-in`() {
        assertNull(gate(verifiedAt = null, firstAttemptAt = now - 2 * hour))
        val expired = gate(verifiedAt = null, firstAttemptAt = now - 80 * hour)
        assertNotNull(expired)
        assertTrue(expired!!.contains("Connect this player"))
    }

    @Test
    fun `cached expired states block even without a fresh portal answer`() {
        // The old policy let staleness suppress these — that was the bypass.
        assertNotNull(gate(status = "trial_expired", verifiedAt = now - 30 * day))
        assertNotNull(gate(status = "expired", verifiedAt = now - 30 * day))
        assertNotNull(gate(status = "revoked", verifiedAt = now - hour))
    }

    @Test
    fun `trial plays offline within its remaining days plus grace and blocks after`() {
        assertNull(gate(status = "trial", daysLeft = 7, verifiedAt = now - 5 * day))
        // 7 days + 72 h grace after verification = 10 days; at 11 it blocks.
        val blocked = gate(status = "trial", daysLeft = 7, verifiedAt = now - 11 * day)
        assertNotNull(blocked)
        assertTrue(blocked!!.contains("free trial has ended"))
    }

    @Test
    fun `lifetime license verified once is valid offline forever`() {
        assertNull(gate(status = "licensed", daysLeft = 0, verifiedAt = now - 400 * day))
    }

    @Test
    fun `yearly license rides out an outage inside remaining days plus grace`() {
        assertNull(gate(status = "licensed", daysLeft = 200, verifiedAt = now - 30 * day))
        val blocked = gate(status = "licensed", daysLeft = 10, verifiedAt = now - 14 * day)
        assertNotNull(blocked)
        assertTrue(blocked!!.contains("activation has expired"))
    }

    @Test
    fun `unknown statuses fail open`() {
        assertNull(gate(status = "some_future_state", verifiedAt = now - 100 * day))
    }

    @Test
    fun `block messages carry the device code for the support call`() {
        val message = gate(status = "trial_expired", verifiedAt = now)
        assertTrue(message!!.contains("NP-TEST-CODE"))
    }
}
