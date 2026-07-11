package com.novaplay.tv.data.repo

import com.novaplay.tv.data.remote.PortalPolicyDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedAccessPolicyTest {

    @Test
    fun missingPolicyLeavesPersonalInstallUnrestricted() {
        val policy = ManagedAccessPolicy.fromPortal(null, nowEpochSec = 100L)

        assertEquals(ManagedAccessState.UNMANAGED, policy.state)
        assertTrue(policy.allows(ManagedFeature.LIVE))
        assertTrue(policy.allows(ManagedFeature.MOVIES))
        assertTrue(policy.allows(ManagedFeature.SERIES))
    }

    @Test
    fun activePolicyAppliesServiceEntitlements() {
        val policy = ManagedAccessPolicy.fromPortal(
            PortalPolicyDto(
                status = "active",
                allowLive = true,
                allowMovies = false,
                allowSeries = false,
                revision = 12L,
            ),
            nowEpochSec = 200L,
        )

        assertEquals(ManagedAccessState.ACTIVE, policy.state)
        assertTrue(policy.allows(ManagedFeature.LIVE))
        assertFalse(policy.allows(ManagedFeature.MOVIES))
        assertFalse(policy.allows(ManagedFeature.SERIES))
        assertEquals(12L, policy.revision)
    }

    @Test
    fun suspendedAndRevokedPoliciesBlockEveryService() {
        for (status in listOf("suspended", "revoked")) {
            val policy = ManagedAccessPolicy.fromPortal(
                PortalPolicyDto(
                    status = status,
                    allowLive = true,
                    allowMovies = true,
                    allowSeries = true,
                ),
                nowEpochSec = 300L,
            )

            assertFalse(policy.allows(ManagedFeature.LIVE))
            assertFalse(policy.allows(ManagedFeature.MOVIES))
            assertFalse(policy.allows(ManagedFeature.SERIES))
        }
    }

    @Test
    fun unknownPortalStateFailsClosed() {
        val policy = ManagedAccessPolicy.fromPortal(
            PortalPolicyDto(status = "unexpected"),
            nowEpochSec = 400L,
        )

        assertEquals(ManagedAccessState.SUSPENDED, policy.state)
        assertTrue(policy.isBlocked)
    }

    @Test
    fun portalMessageAndSupportCodeAreLengthBounded() {
        val policy = ManagedAccessPolicy.fromPortal(
            PortalPolicyDto(
                message = "m".repeat(500),
                supportCode = "s".repeat(100),
            ),
            nowEpochSec = 500L,
        )

        assertEquals(240, policy.message?.length)
        assertEquals(48, policy.supportCode?.length)
    }
}
