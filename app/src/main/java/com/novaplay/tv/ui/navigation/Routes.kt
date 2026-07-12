package com.novaplay.tv.ui.navigation

/**
 * Route patterns for the nav graph plus builders for the parameterized ones.
 * Keep each pattern and its builder in sync when adding arguments.
 */
object Routes {
    const val GATE = "gate"
    const val ACTIVATION = "activation"
    const val HOME = "home"

    const val LIVE = "live"
    const val GUIDE = "guide"
    const val LIVE_PLAYER = "live_player/{channelId}?categoryId={categoryId}"
    /** Concrete live-player route; pass categoryId -1 to match the destination's "no category" default. */
    fun livePlayer(channelId: Long, categoryId: Long): String =
        "live_player/$channelId?categoryId=$categoryId"

    const val MOVIES = "movies"
    const val MOVIE_DETAILS = "movie/{movieId}"
    /** Concrete details route for a movie's local Room row id. */
    fun movieDetails(movieId: Long): String = "movie/$movieId"

    const val SERIES = "series"
    const val SERIES_DETAILS = "series/{seriesId}"
    /** Concrete details route for a series' local Room row id. */
    fun seriesDetails(seriesId: Long): String = "series/$seriesId"

    // mediaType: "movie" | "episode"; mediaId is the local Room row id.
    const val VOD_PLAYER = "vod_player/{mediaType}/{mediaId}?resume={resume}"
    /** Concrete VOD player route; resume=true continues from saved progress instead of the start. */
    fun vodPlayer(mediaType: String, mediaId: Long, resume: Boolean = false): String =
        "vod_player/$mediaType/$mediaId?resume=$resume"

    const val MEDIA_TYPE_MOVIE = "movie"
    const val MEDIA_TYPE_EPISODE = "episode"

    const val PLAYLISTS = "playlists"
    const val PLAYLISTS_SETUP = "playlists/setup"
    const val SETTINGS = "settings"
}
