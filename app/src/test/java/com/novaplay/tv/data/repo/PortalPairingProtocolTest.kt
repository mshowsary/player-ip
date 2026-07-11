package com.novaplay.tv.data.repo

import com.novaplay.tv.data.remote.PairingStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalPairingProtocolTest {

    @Test
    fun visibleCodesAreNormalizedWithoutAmbiguousSpacing() {
        assertEquals("ABCD-2345", PortalPairingProtocol.normalizeUserCode("ab cd-2345"))
    }

    @Test
    fun pollingIntervalIsBoundedAndSlowDownAddsFiveSeconds() {
        assertEquals(3, PortalPairingProtocol.safePollInterval(1))
        assertEquals(30, PortalPairingProtocol.safePollInterval(60))
        assertEquals(10, PortalPairingProtocol.slowDownInterval(5))
        assertEquals(30, PortalPairingProtocol.slowDownInterval(30))
    }

    @Test
    fun approvedStatusCreatesExpiringDeviceTokens() {
        val result = PortalPairingProtocol.mapSuccessfulStatus(
            dto = PairingStatusDto(
                status = "approved",
                accessToken = "access",
                refreshToken = "refresh",
                expiresInSeconds = 900L,
            ),
            deviceId = "device-1",
            nowEpochSec = 1_000L,
            currentIntervalSeconds = 5,
        )

        assertTrue(result is PortalPairingPoll.Approved)
        val approved = result as PortalPairingPoll.Approved
        assertEquals("device-1", approved.tokens.deviceId)
        assertEquals(1_900L, approved.tokens.accessTokenExpiresAtEpochSec)
    }

    @Test
    fun approvedStatusWithoutAccessTokenFailsClosed() {
        val result = PortalPairingProtocol.mapSuccessfulStatus(
            dto = PairingStatusDto(status = "approved"),
            deviceId = "device-1",
            nowEpochSec = 1_000L,
            currentIntervalSeconds = 5,
        )

        assertTrue(result is PortalPairingPoll.Failure)
    }

    @Test
    fun pendingAndTerminalStatesAreMappedExplicitly() {
        assertTrue(map("pending") is PortalPairingPoll.Pending)
        assertTrue(map("denied") is PortalPairingPoll.Denied)
        assertTrue(map("expired") is PortalPairingPoll.Expired)
        assertTrue(map("unexpected") is PortalPairingPoll.Failure)
    }

    private fun map(status: String): PortalPairingPoll =
        PortalPairingProtocol.mapSuccessfulStatus(
            dto = PairingStatusDto(status = status),
            deviceId = "device",
            nowEpochSec = 0L,
            currentIntervalSeconds = 5,
        )
}
