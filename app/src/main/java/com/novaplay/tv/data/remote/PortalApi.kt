package com.novaplay.tv.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
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

@Serializable
data class PortalPlaylistsResponse(
    val playlists: List<PortalPlaylistDto> = emptyList(),
)

interface PortalApi {
    // 200 = playlists attached; 404 = device not registered yet; 403 = key mismatch.
    @GET("api/v1/devices/{mac}/playlists")
    suspend fun getPlaylists(
        @Path("mac") mac: String,
        @Query("key") deviceKey: String,
    ): Response<PortalPlaylistsResponse>
}

// Debug-only stand-in until the real portal exists (BuildConfig.MOCK_ACTIVATION).
// Point the credentials at any test Xtream panel to exercise the full app.
// 10.0.2.2 = host loopback from the Android emulator (see scratchpad mock_server.py).
object MockPortal {
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
