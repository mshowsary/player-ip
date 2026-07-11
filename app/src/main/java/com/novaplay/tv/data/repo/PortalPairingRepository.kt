package com.novaplay.tv.data.repo

import android.os.Build
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.data.remote.CreatePairingSessionRequest
import com.novaplay.tv.data.remote.MockPortal
import com.novaplay.tv.data.remote.PortalApi
import com.novaplay.tv.data.remote.PortalPlaylistDto
import com.novaplay.tv.data.remote.RefreshDeviceTokenRequest
import com.novaplay.tv.data.security.PortalTokenStore
import com.novaplay.tv.data.security.PortalTokens
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network foundation for Device ID + one-time-code portal pairing.
 *
 * The visible user code is deliberately separate from the private session
 * secret and the revocable device tokens stored after approval.
 */
@Singleton
class PortalPairingRepository @Inject constructor(
    private val api: PortalApi,
    private val deviceIdentity: DeviceIdentity,
    private val tokenStore: PortalTokenStore,
    private val managedAccessRepository: ManagedAccessRepository,
) {
    /**
     * Starts a pairing session with the portal. The server response is
     * validated defensively — HTTPS verification address, minimum secret
     * length, plausible code shape, unexpired session — with the strict checks
     * relaxed only in DEBUG builds against local test portals.
     */
    suspend fun createSession(): Result<PortalPairingSession> = runCatching {
        val identity = deviceIdentity.get()
        if (BuildConfig.MOCK_ACTIVATION) {
            return@runCatching PortalPairingSession(
                sessionId = "mock-${identity.deviceId}",
                userCode = PortalPairingProtocol.normalizeUserCode(identity.deviceKey),
                verificationUri = BuildConfig.PORTAL_BASE_URL.trimEnd('/') + "/activate",
                expiresAtEpochSec = nowEpochSec() + 600,
                intervalSeconds = 5,
                sessionSecret = "mock-session-secret",
            )
        }

        val response = api.createPairingSession(
            CreatePairingSessionRequest(
                deviceId = identity.deviceId,
                legacyMac = identity.mac,
                deviceName = deviceName(),
                appVersion = BuildConfig.VERSION_NAME,
            ),
        )
        check(response.isSuccessful) { "Portal pairing request failed: HTTP ${response.code()}" }
        val body = response.body() ?: error("Portal returned an empty pairing session")
        require(body.sessionId.isNotBlank()) { "Portal returned an invalid pairing session" }
        require(body.sessionSecret.length >= 16 || BuildConfig.DEBUG) {
            "Portal pairing secret is too short"
        }
        require(body.expiresAtEpochSec > nowEpochSec()) { "Portal pairing session is already expired" }

        val code = PortalPairingProtocol.normalizeUserCode(body.userCode)
        require(code.replace("-", "").length in 4..16) {
            "Portal returned an invalid user code"
        }
        val verificationUri = body.verificationUri.trim()
        require(verificationUri.startsWith("https://") || BuildConfig.DEBUG) {
            "Portal pairing address must use HTTPS"
        }

        PortalPairingSession(
            sessionId = body.sessionId,
            userCode = code,
            verificationUri = verificationUri,
            expiresAtEpochSec = body.expiresAtEpochSec,
            intervalSeconds = PortalPairingProtocol.safePollInterval(body.intervalSeconds),
            sessionSecret = body.sessionSecret,
        )
    }

    /**
     * Performs one status poll for the session. Never throws: network and HTTP
     * errors map to poll states (403 denied, 404/410 expired, 429 slow-down).
     * On approval the tokens and portal policy are persisted before returning.
     */
    suspend fun poll(session: PortalPairingSession): PortalPairingPoll {
        val identity = deviceIdentity.get()
        val now = nowEpochSec()
        if (session.expiresAtEpochSec <= now) return PortalPairingPoll.Expired

        if (BuildConfig.MOCK_ACTIVATION) {
            val tokens = PortalTokens(
                deviceId = identity.deviceId,
                accessToken = "mock-access-token",
                refreshToken = "mock-refresh-token",
                accessTokenExpiresAtEpochSec = now + 3_600,
            )
            tokenStore.save(tokens)
            managedAccessRepository.applyPortalPolicy(MockPortal.policy)
            return PortalPairingPoll.Approved(tokens, MockPortal.playlists, MockPortal.policy)
        }

        val response = try {
            api.getPairingStatus(session.sessionId, session.sessionSecret)
        } catch (error: Exception) {
            return PortalPairingPoll.Failure(error.message ?: "Portal network error")
        }

        if (!response.isSuccessful) {
            return when (response.code()) {
                403 -> PortalPairingPoll.Denied
                404, 410 -> PortalPairingPoll.Expired
                429 -> PortalPairingPoll.SlowDown(
                    PortalPairingProtocol.slowDownInterval(session.intervalSeconds),
                )
                else -> PortalPairingPoll.Failure("Portal error: HTTP ${response.code()}")
            }
        }

        val body = response.body()
            ?: return PortalPairingPoll.Failure("Portal returned an empty pairing status")
        val result = PortalPairingProtocol.mapSuccessfulStatus(
            dto = body,
            deviceId = identity.deviceId,
            nowEpochSec = now,
            currentIntervalSeconds = session.intervalSeconds,
        )
        if (result is PortalPairingPoll.Approved) {
            tokenStore.save(result.tokens)
            managedAccessRepository.applyPortalPolicy(result.policy)
        }
        return result
    }

    /**
     * Fetches this device's assigned playlists with the stored bearer session,
     * refreshing the access token proactively and retrying once after a 401.
     * Tokens belonging to a different installation wipe the session and policy
     * (fail closed). Each success also re-applies the returned access policy.
     */
    suspend fun authorizedPlaylists(): Result<List<PortalPlaylistDto>> = runCatching {
        if (BuildConfig.MOCK_ACTIVATION) {
            managedAccessRepository.applyPortalPolicy(MockPortal.policy)
            return@runCatching MockPortal.playlists
        }

        val identity = deviceIdentity.get()
        var tokens = tokenStore.load() ?: error("This device is not paired")
        require(tokens.deviceId == identity.deviceId) {
            tokenStore.clear()
            managedAccessRepository.clear()
            "Portal session belongs to another installation"
        }

        if (tokens.accessTokenExpiresAtEpochSec <= nowEpochSec() + REFRESH_WINDOW_SECONDS) {
            tokens = refresh(tokens)
        }

        val response = api.getAuthorizedPlaylists("Bearer ${tokens.accessToken}")
        if (response.code() == 401) {
            tokens = refresh(tokens)
            val retry = api.getAuthorizedPlaylists("Bearer ${tokens.accessToken}")
            check(retry.isSuccessful) { "Portal authorization failed: HTTP ${retry.code()}" }
            val body = retry.body()
            managedAccessRepository.applyPortalPolicy(body?.policy)
            return@runCatching body?.playlists.orEmpty()
        }
        check(response.isSuccessful) { "Portal request failed: HTTP ${response.code()}" }
        val body = response.body()
        managedAccessRepository.applyPortalPolicy(body?.policy)
        body?.playlists.orEmpty()
    }

    /** True when pairing tokens exist locally, i.e. this device was approved at some point. */
    fun hasStoredSession(): Boolean = tokenStore.load() != null

    /** Forgets the pairing tokens and managed policy locally; the portal is not notified. */
    fun disconnect() {
        tokenStore.clear()
        managedAccessRepository.clear()
    }

    // Rotates the access token via the refresh token. A missing refresh token
    // or a 401/403 wipes the session and policy so the user must pair again
    // (fail closed); the rotated tokens are persisted before returning.
    private suspend fun refresh(current: PortalTokens): PortalTokens {
        val refreshToken = current.refreshToken ?: run {
            tokenStore.clear()
            managedAccessRepository.clear()
            error("Portal session expired; pair this device again")
        }
        val response = api.refreshDeviceToken(
            RefreshDeviceTokenRequest(deviceId = current.deviceId, refreshToken = refreshToken),
        )
        if (response.code() == 401 || response.code() == 403) {
            tokenStore.clear()
            managedAccessRepository.clear()
            error("Portal session was revoked; pair this device again")
        }
        check(response.isSuccessful) { "Portal token refresh failed: HTTP ${response.code()}" }
        val body = response.body() ?: error("Portal returned an empty token response")
        val refreshed = PortalTokens(
            deviceId = current.deviceId,
            accessToken = body.accessToken,
            refreshToken = body.refreshToken ?: current.refreshToken,
            accessTokenExpiresAtEpochSec = nowEpochSec() + body.expiresInSeconds.coerceAtLeast(60),
        )
        tokenStore.save(refreshed)
        return refreshed
    }

    // Friendly device label for the portal's device list, capped at 80 chars.
    private fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Android device" }
        .take(80)

    // Wall-clock seconds, the unit the pairing contract uses for expiries.
    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1_000

    private companion object {
        const val REFRESH_WINDOW_SECONDS = 120L
    }
}
