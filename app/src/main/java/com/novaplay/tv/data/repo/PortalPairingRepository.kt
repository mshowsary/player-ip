package com.novaplay.tv.data.repo

import android.os.Build
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.core.DeviceIdentity
import com.novaplay.tv.core.PortalEndpointPolicy
import com.novaplay.tv.data.remote.CreatePairingSessionRequest
import com.novaplay.tv.data.remote.MockPortal
import com.novaplay.tv.data.remote.PortalApi
import com.novaplay.tv.data.remote.PortalPlaylistDto
import com.novaplay.tv.data.remote.PortalPlaylistsResponse
import com.novaplay.tv.data.remote.RefreshDeviceTokenRequest
import com.novaplay.tv.data.security.PortalTokenStore
import com.novaplay.tv.data.security.PortalTokens
import retrofit2.Response
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
     * Starts a pairing session and validates the response defensively. Production
     * calls fail before network I/O unless the portal is configured securely.
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
        requirePortalConfigured()

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
     * Performs one status poll. Network and HTTP errors map to explicit poll
     * states; approved tokens and policy are persisted before returning.
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
        val endpoint = PortalEndpointPolicy.assess(BuildConfig.PORTAL_BASE_URL, BuildConfig.DEBUG)
        if (!endpoint.configured || !endpoint.transportAllowed) {
            return PortalPairingPoll.Failure(endpoint.issue ?: "Provider portal is unavailable")
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
     * Fetches assigned playlists using the stored bearer session, proactively
     * refreshing the access token and retrying once after a 401.
     */
    suspend fun authorizedPlaylists(): Result<List<PortalPlaylistDto>> = runCatching {
        if (BuildConfig.MOCK_ACTIVATION) {
            managedAccessRepository.applyPortalPolicy(MockPortal.policy)
            return@runCatching MockPortal.playlists
        }
        requirePortalConfigured()

        val identity = deviceIdentity.get()
        var tokens = tokenStore.load() ?: run {
            managedAccessRepository.markSessionRevoked(
                "The stored managed session could not be opened. Pair this device again.",
            )
            error("Portal session is unreadable; pair this device again")
        }
        if (tokens.deviceId != identity.deviceId) {
            revokeSession("The managed session belongs to another app installation. Pair this device again.")
            error("Portal session belongs to another installation")
        }

        if (tokens.accessTokenExpiresAtEpochSec <= nowEpochSec() + REFRESH_WINDOW_SECONDS) {
            tokens = refresh(tokens)
        }

        val response = api.getAuthorizedPlaylists("Bearer ${tokens.accessToken}")
        if (response.code() == 401) {
            tokens = refresh(tokens)
            return@runCatching consumeAuthorizedResponse(
                api.getAuthorizedPlaylists("Bearer ${tokens.accessToken}"),
            )
        }
        consumeAuthorizedResponse(response)
    }

    /** True when a paired-session envelope exists, including an unreadable one. */
    fun hasStoredSession(): Boolean = tokenStore.hasStoredEnvelope()

    /** Explicit user disconnect returns to personal mode; server failures never call this path. */
    fun disconnect() {
        tokenStore.clear()
        managedAccessRepository.disconnectToPersonal()
    }

    private fun consumeAuthorizedResponse(
        response: Response<PortalPlaylistsResponse>,
    ): List<PortalPlaylistDto> {
        if (response.code() == 401 || response.code() == 403) {
            revokeSession("Managed access was revoked or is no longer authorized. Pair this device again.")
            error("Portal authorization was revoked")
        }
        check(response.isSuccessful) { "Portal request failed: HTTP ${response.code()}" }
        val body = response.body() ?: error("Portal returned an empty playlist response")
        managedAccessRepository.applyPortalPolicy(body.policy)
        return body.playlists
    }

    // Rotates the access token. Missing/revoked refresh credentials block the
    // managed device durably instead of returning it to personal access.
    private suspend fun refresh(current: PortalTokens): PortalTokens {
        requirePortalConfigured()
        val refreshToken = current.refreshToken ?: run {
            revokeSession("The managed session expired and cannot be refreshed. Pair this device again.")
            error("Portal session expired; pair this device again")
        }
        val response = api.refreshDeviceToken(
            RefreshDeviceTokenRequest(deviceId = current.deviceId, refreshToken = refreshToken),
        )
        if (response.code() == 401 || response.code() == 403) {
            revokeSession("Managed access was revoked by the provider. Pair this device again or contact support.")
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

    private fun revokeSession(message: String) {
        tokenStore.clear()
        managedAccessRepository.markSessionRevoked(message)
    }

    private fun requirePortalConfigured() {
        PortalEndpointPolicy.requireConfigured(BuildConfig.PORTAL_BASE_URL, BuildConfig.DEBUG)
    }

    private fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Android device" }
        .take(80)

    private fun nowEpochSec(): Long = System.currentTimeMillis() / 1_000

    private companion object {
        const val REFRESH_WINDOW_SECONDS = 120L
    }
}
