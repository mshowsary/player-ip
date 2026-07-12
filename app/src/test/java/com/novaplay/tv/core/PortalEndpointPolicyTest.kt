package com.novaplay.tv.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalEndpointPolicyTest {

    @Test
    fun configuredHttpsPortalIsAllowed() {
        val result = PortalEndpointPolicy.assess("https://portal.provider.net", debug = false)

        assertTrue(result.transportAllowed)
        assertTrue(result.configured)
    }

    @Test
    fun documentationAndReservedHostsAreSafeButNotConfigured() {
        val candidates = listOf(
            "https://portal.example.com",
            "https://portal.example.net",
            "https://portal.example.org",
            "https://portal.ci.invalid",
            "https://portal.provider.test",
            "https://portal.provider.example",
        )

        candidates.forEach { address ->
            val result = PortalEndpointPolicy.assess(address, debug = false)
            assertTrue(address, result.transportAllowed)
            assertFalse(address, result.configured)
        }
    }

    @Test
    fun productionHttpPortalIsBlocked() {
        val result = PortalEndpointPolicy.assess("http://portal.provider.net", debug = false)

        assertFalse(result.transportAllowed)
        assertFalse(result.configured)
    }

    @Test
    fun localHttpPortalIsAllowedOnlyForDebugDevelopment() {
        val debug = PortalEndpointPolicy.assess("http://10.0.2.2:8080", debug = true)
        val release = PortalEndpointPolicy.assess("http://10.0.2.2:8080", debug = false)

        assertTrue(debug.transportAllowed)
        assertTrue(debug.configured)
        assertFalse(release.transportAllowed)
    }

    @Test
    fun credentialsQueriesAndFragmentsAreRejected() {
        assertFalse(
            PortalEndpointPolicy.assess(
                "https://user:secret@portal.provider.net",
                debug = false,
            ).transportAllowed,
        )
        assertFalse(
            PortalEndpointPolicy.assess(
                "https://portal.provider.net?token=value",
                debug = false,
            ).transportAllowed,
        )
        assertFalse(
            PortalEndpointPolicy.assess(
                "https://portal.provider.net#fragment",
                debug = false,
            ).transportAllowed,
        )
    }
}
