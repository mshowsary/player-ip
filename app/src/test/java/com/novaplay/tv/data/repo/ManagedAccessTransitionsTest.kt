package com.novaplay.tv.data.repo

import com.novaplay.tv.data.remote.PortalPolicyDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedAccessTransitionsTest {

    @Test
    fun missingPolicyOnNewManagedSessionFailsClosed() {
        val next = ManagedAccessTransitions.fromPortalResponse(
            current = ManagedAccessPolicy(),
            dto = null,
            nowEpochSec = 100L,
        )

        assertEquals(ManagedAccessState.SUSPENDED, next.state)
        assertFalse(next.allows(ManagedFeature.LIVE))
        assertFalse(next.allows(ManagedFeature.MOVIES))
        assertFalse(next.allows(ManagedFeature.SERIES))
        assertTrue(next.message.orEmpty().contains("could not be verified"))
    }

    @Test
    fun missingPolicyPreservesLastKnownManagedDecision() {
        val current = ManagedAccessPolicy(
            state = ManagedAccessState.ACTIVE,
            allowLive = true,
            allowMovies = false,
            allowSeries = true,
            revision = 42L,
            updatedAtEpochSec = 50L,
        )

        val next = ManagedAccessTransitions.fromPortalResponse(
            current = current,
            dto = null,
            nowEpochSec = 100L,
        )

        assertSame(current, next)
    }

    @Test
    fun portalPolicyReplacesCurrentDecision() {
        val next = ManagedAccessTransitions.fromPortalResponse(
            current = ManagedAccessPolicy(state = ManagedAccessState.REVOKED),
            dto = PortalPolicyDto(
                status = "active",
                allowLive = true,
                allowMovies = false,
                allowSeries = false,
                revision = 9L,
            ),
            nowEpochSec = 200L,
        )

        assertEquals(ManagedAccessState.ACTIVE, next.state)
        assertTrue(next.allows(ManagedFeature.LIVE))
        assertFalse(next.allows(ManagedFeature.MOVIES))
        assertFalse(next.allows(ManagedFeature.SERIES))
        assertEquals(9L, next.revision)
    }

    @Test
    fun revokedSessionBlocksEverythingAndKeepsProviderReference() {
        val current = ManagedAccessPolicy(
            state = ManagedAccessState.ACTIVE,
            revision = 77L,
            supportCode = "ACCOUNT-77",
        )

        val next = ManagedAccessTransitions.sessionRevoked(
            current = current,
            nowEpochSec = 300L,
            message = "  Session revoked  ",
        )

        assertEquals(ManagedAccessState.REVOKED, next.state)
        assertFalse(next.allows(ManagedFeature.LIVE))
        assertFalse(next.allows(ManagedFeature.MOVIES))
        assertFalse(next.allows(ManagedFeature.SERIES))
        assertEquals("Session revoked", next.message)
        assertEquals("ACCOUNT-77", next.supportCode)
        assertEquals(77L, next.revision)
        assertEquals(300L, next.updatedAtEpochSec)
    }

    @Test
    fun explicitDisconnectIsTheOnlyOpenPersonalTransition() {
        val next = ManagedAccessTransitions.explicitDisconnect()

        assertEquals(ManagedAccessState.UNMANAGED, next.state)
        assertTrue(next.allows(ManagedFeature.LIVE))
        assertTrue(next.allows(ManagedFeature.MOVIES))
        assertTrue(next.allows(ManagedFeature.SERIES))
    }

    @Test
    fun revocationDisplayFieldsAreLengthBounded() {
        val next = ManagedAccessTransitions.sessionRevoked(
            current = ManagedAccessPolicy(),
            nowEpochSec = 400L,
            message = "m".repeat(500),
            supportCode = "s".repeat(100),
        )

        assertEquals(240, next.message?.length)
        assertEquals(48, next.supportCode?.length)
    }
}
