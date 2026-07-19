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
 * (server/username/password) or a direct M3U URL, mirroring the Playlist entity.
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
    @SerialName("epg_url") val epgUrl: String? = null,
    // PIN-protected on the portal: the TV must refuse to remove it.
    val protected: Boolean = false,
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
    /** Starts device-code pairing; the returned user code is shown on-screen. */
    @POST("api/v1/pairing/sessions")
    suspend fun createPairingSession(
        @Body request: CreatePairingSessionRequest,
    ): Response<PairingSessionDto>

    /** Polls with the high-entropy session secret rather than the guessable code. */
    @GET("api/v1/pairing/sessions/{sessionId}")
    suspend fun getPairingStatus(
        @Path("sessionId") sessionId: String,
        @Header("X-Pairing-Secret") sessionSecret: String,
    ): Response<PairingStatusDto>

    /** Fetches entitlements and policy for an already-paired device. */
    @GET("api/v1/device/playlists")
    suspend fun getAuthorizedPlaylists(
        @Header("Authorization") authorization: String,
    ): Response<PortalPlaylistsResponse>

    /** Renews the device access token when the current one expires. */
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

    /** Starts a phone-to-TV playlist delivery; the TV shows the code and QR. */
    @POST("api/v1/playlist-deliveries/sessions")
    suspend fun createDeliverySession(
        @Body request: CreateDeliveryRequest,
    ): Response<PairingSessionDto>

    /** Polls a delivery session; "ready" carries the playlist exactly once. */
    @GET("api/v1/playlist-deliveries/sessions/{sessionId}")
    suspend fun getDeliveryStatus(
        @Path("sessionId") sessionId: String,
        @Header("X-Delivery-Secret") sessionSecret: String,
    ): Response<DeliveryStatusDto>

    /** Registers this installation for the self-service trial and identity. */
    @POST("api/v1/player/register")
    suspend fun registerPlayer(
        @Body request: RegisterPlayerRequest,
    ): Response<PlayerStatusDto>

    /** Fetches the current license state; authenticated by the device secret. */
    @GET("api/v1/player/status")
    suspend fun playerStatus(
        @Query("device_id") deviceId: String,
        @Header("X-Player-Secret") secret: String,
    ): Response<PlayerStatusDto>
}

/** Create payload for a phone-entry playlist delivery session. */
@Serializable
data class CreateDeliveryRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
)

/** One playlist submitted from the viewer's phone; mirrors PortalPlaylistDto minus id. */
@Serializable
data class DeliveredPlaylistDto(
    val name: String,
    val type: String,
    val server: String? = null,
    val username: String? = null,
    val password: String? = null,
    val url: String? = null,
)

/** Poll result for a delivery session; playlist only present on "ready". */
@Serializable
data class DeliveryStatusDto(
    val status: String,
    val playlist: DeliveredPlaylistDto? = null,
)

/** First-contact registration for the self-service player license. */
@Serializable
data class RegisterPlayerRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
    // The identity pair every screen and portal shows: pseudo MAC + key.
    val mac: String? = null,
    @SerialName("device_key") val deviceKey: String? = null,
    // White-label brand slug of this build, for the portal's player dropdown.
    val brand: String = "novaplay",
)

/** License state; device_secret is present only in the registration response. */
@Serializable
data class PlayerStatusDto(
    @SerialName("device_code") val deviceCode: String,
    val status: String,
    @SerialName("trial_days_left") val trialDaysLeft: Int = 0,
    @SerialName("device_secret") val deviceSecret: String? = null,
)
