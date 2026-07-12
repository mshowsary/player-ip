package com.novaplay.tv.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalEndpointPolicyTest {

    @Test
    fun configuredHttpsPortalIsAllowed() {
        val result = PortalEndpointPolicy.assess("https://portal.provider.test", debug = false)

        assertTrue(result.transportAllowed)
        assertTrue(result.configured)
    }

    @Test
    fun placeholderPortalIsSafeButNotConfigured() {
        val result = PortalEndpointPolicy.assess("https://portal.example.com", debug = false)

        assertTrue(result.transportAllowed)
        assertFalse(result.configured)
    }

    @Test
    fun productionHttpPortalIsBlocked() {
        val result = PortalEndpointPolicy.assess("http://portal.provider.test", debug = false)

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
                "https://user:secret@portal.provider.test",
                debug = false,
            ).transportAllowed,
        )
        assertFalse(
            PortalEndpointPolicy.assess(
                "https://portal.provider.test?token=value",
                debug = false,
            ).transportAllowed,
        )
        assertFalse(
            PortalEndpointPolicy.assess(
                "https://portal.provider.test#fragment",
                debug = false,
            ).transportAllowed,
        )
    }
}
