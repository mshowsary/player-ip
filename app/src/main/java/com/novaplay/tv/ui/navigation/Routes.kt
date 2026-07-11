package com.novaplay.tv.ui.navigation

object Routes {
    const val GATE = "gate"
    const val ACTIVATION = "activation"
    const val HOME = "home"

    const val LIVE = "live"
    const val LIVE_PLAYER = "live_player/{channelId}?categoryId={categoryId}"
    fun livePlayer(channelId: Long, categoryId: Long): String =
        "live_player/$channelId?categoryId=$categoryId"

    const val MOVIES = "movies"
    const val MOVIE_DETAILS = "movie/{movieId}"
    fun movieDetails(movieId: Long): String = "movie/$movieId"

    const val SERIES = "series"
    const val SERIES_DETAILS = "series/{seriesId}"
    fun seriesDetails(seriesId: Long): String = "series/$seriesId"

    // mediaType: "movie" | "episode"; mediaId is the local Room row id.
    const val VOD_PLAYER = "vod_player/{mediaType}/{mediaId}?resume={resume}"
    fun vodPlayer(mediaType: String, mediaId: Long, resume: Boolean = false): String =
        "vod_player/$mediaType/$mediaId?resume=$resume"

    const val MEDIA_TYPE_MOVIE = "movie"
    const val MEDIA_TYPE_EPISODE = "episode"

    const val PLAYLISTS = "playlists"
    const val PLAYLISTS_SETUP = "playlists/setup"
    const val SETTINGS = "settings"
}
