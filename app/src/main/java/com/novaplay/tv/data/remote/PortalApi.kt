package com.novaplay.tv.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * One playlist entitlement granted to this device — either Xtream credentials
 * (server/username/password) or a direct M3U url, mirroring the Playlist entity.
 */
@Serializable
data class PortalPlaylistDto(
    val id: Long,
    val name: String,
    val type: String,
    val server: String? = null,
    val username: String? = null,
    val password: String? = null,
    val url: String? = null,
)

/**
 * Server-resolved device policy. Country, tenant and reseller rules are
 * evaluated by the portal; the Android client only consumes the resulting
 * service entitlements and device lifecycle state.
 */
@Serializable
data class PortalPolicyDto(
    val status: String = "active",
    @SerialName("allow_live") val allowLive: Boolean = true,
    @SerialName("allow_movies") val allowMovies: Boolean = true,
    @SerialName("allow_series") val allowSeries: Boolean = true,
    val message: String? = null,
    @SerialName("support_code") val supportCode: String? = null,
    @SerialName("policy_revision") val revision: Long = 0L,
)

/** Entitlements plus the current device policy in one round trip. */
@Serializable
data class PortalPlaylistsResponse(
    val playlists: List<PortalPlaylistDto> = emptyList(),
    val policy: PortalPolicyDto? = null,
)

/**
 * Device-code pairing contract used by the provider/reseller portal.
 *
 * The TV creates a short-lived session, displays [PairingSessionDto.userCode],
 * and polls with a high-entropy session secret. The human-readable code is not
 * an API credential. After approval the portal issues revocable device tokens.
 */
@Serializable
data class CreatePairingSessionRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("legacy_mac") val legacyMac: String? = null,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
    val capabilities: List<String> = listOf("xtream", "m3u", "live", "vod", "series"),
)

/**
 * A freshly created pairing session: [userCode] is what the human types on the
 * portal, [sessionSecret] is the credential the TV polls with.
 */
@Serializable
data class PairingSessionDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_at") val expiresAtEpochSec: Long,
    @SerialName("interval_seconds") val intervalSeconds: Int = 5,
    @SerialName("session_secret") val sessionSecret: String,
)

/**
 * Poll result for a pairing session. Tokens, playlists and policy are only
 * populated once the portal reports the session approved.
 */
@Serializable
data class PairingStatusDto(
    val status: String,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long? = null,
    val playlists: List<PortalPlaylistDto> = emptyList(),
    val policy: PortalPolicyDto? = null,
)

/** Exchange payload: the long-lived refresh token buys a new access token. */
@Serializable
data class RefreshDeviceTokenRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("refresh_token") val refreshToken: String,
)

/** Issued device tokens; refreshToken is only present when the portal rotates it. */
@Serializable
data class DeviceTokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long,
)

/**
 * Retrofit contract for the provisioning portal. Calls return raw [Response] so
 * callers can branch on HTTP status (revoked, expired, pending) instead of
 * treating every non-2xx as an exception.
 */
interface PortalApi {
    /** Starts device-code pairing; the returned userCode is shown on-screen for the user to enter on the portal. */
    @POST("api/v1/pairing/sessions")
    suspend fun createPairingSession(
        @Body request: CreatePairingSessionRequest,
    ): Response<PairingSessionDto>

    /** Polls a pairing session, authenticated by the high-entropy session secret rather than the guessable user code. */
    @GET("api/v1/pairing/sessions/{sessionId}")
    suspend fun getPairingStatus(
        @Path("sessionId") sessionId: String,
        @Header("X-Pairing-Secret") sessionSecret: String,
    ): Response<PairingStatusDto>

    /** Fetches entitlements and policy for an already-paired device; authorization carries the Bearer access token. */
    @GET("api/v1/device/playlists")
    suspend fun getAuthorizedPlaylists(
        @Header("Authorization") authorization: String,
    ): Response<PortalPlaylistsResponse>

    /** Renews the device access token from the refresh token when the current one expires. */
    @POST("api/v1/device/token/refresh")
    suspend fun refreshDeviceToken(
        @Body request: RefreshDeviceTokenRequest,
    ): Response<DeviceTokenDto>

    // Temporary backwards-compatible endpoint. It remains until the real
    // portal and existing test setup have migrated to device-code pairing.
    @GET("api/v1/devices/{mac}/playlists")
    suspend fun getPlaylists(
        @Path("mac") mac: String,
        @Query("key") deviceKey: String,
    ): Response<PortalPlaylistsResponse>
}

// Debug-only stand-in until the real portal exists (BuildConfig.MOCK_ACTIVATION).
object MockPortal {
    val policy = PortalPolicyDto(
        status = "active",
        allowLive = true,
        allowMovies = true,
        allowSeries = true,
        message = "Managed services are active on this device.",
        supportCode = "MOCK-ACTIVE",
        revision = 1L,
    )

    val playlists = listOf(
        PortalPlaylistDto(
            id = 1,
            name = "Kappa",
            type = "xtream",
            server = "http://kappa.cloudf-nexon.xyz:80",
            username = "uhhclvm2rd",
            password = "130lz954yd",
        ),
        PortalPlaylistDto(
            id = 2,
            name = "Kappa M3U",
            type = "m3u",
            url = "http://kappa.cloudf-nexon.xyz:80/get.php?username=uhhclvm2rd&password=130lz954yd&type=m3u_plus&output=mpegts",
        ),
    )
}
