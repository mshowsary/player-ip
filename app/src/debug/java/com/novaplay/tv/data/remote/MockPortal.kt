package com.novaplay.tv.data.remote

/**
 * Debug-only portal stand-in. Real provider credentials must be supplied through
 * personal playlist entry or a connected development portal, never committed.
 */
object MockPortal {
    val policy = PortalPolicyDto(
        status = "active",
        allowLive = true,
        allowMovies = true,
        allowSeries = true,
        message = "Debug pairing approved. Add a personal playlist or connect a development portal.",
        supportCode = "DEBUG-LOCAL",
        revision = 1L,
    )

    val playlists: List<PortalPlaylistDto> = emptyList()
}
