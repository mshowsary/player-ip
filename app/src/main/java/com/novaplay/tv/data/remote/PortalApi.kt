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

@Serializable
data class PairingSessionDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_at") val expiresAtEpochSec: Long,
    @SerialName("interval_seconds") val intervalSeconds: Int = 5,
    @SerialName("session_secret") val sessionSecret: String,
)

@Serializable
data class PairingStatusDto(
    val status: String,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long? = null,
    val playlists: List<PortalPlaylistDto> = emptyList(),
    val policy: PortalPolicyDto? = null,
)

@Serializable
data class RefreshDeviceTokenRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class DeviceTokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long,
)

interface PortalApi {
    @POST("api/v1/pairing/sessions")
    suspend fun createPairingSession(
        @Body request: CreatePairingSessionRequest,
    ): Response<PairingSessionDto>

    @GET("api/v1/pairing/sessions/{sessionId}")
    suspend fun getPairingStatus(
        @Path("sessionId") sessionId: String,
        @Header("X-Pairing-Secret") sessionSecret: String,
    ): Response<PairingStatusDto>

    @GET("api/v1/device/playlists")
    suspend fun getAuthorizedPlaylists(
        @Header("Authorization") authorization: String,
    ): Response<PortalPlaylistsResponse>

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
