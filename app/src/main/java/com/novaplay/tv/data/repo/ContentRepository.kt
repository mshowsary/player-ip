package com.novaplay.tv.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.novaplay.tv.data.db.Bookmark
import com.novaplay.tv.data.db.Episode
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.db.Movie
import com.novaplay.tv.data.db.NovaDatabase
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.db.RecentView
import com.novaplay.tv.data.db.Series
import com.novaplay.tv.data.db.WatchProgress
import com.novaplay.tv.data.prefs.LiveFormat
import com.novaplay.tv.data.remote.XtreamClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-side facade over the synced catalogue: paged browsing, search, stream
 * URL resolution, bookmarks/recents and watch progress. All reads come from
 * Room; only the on-demand detail refreshes touch the network.
 */
@Singleton
class ContentRepository @Inject constructor(
    private val db: NovaDatabase,
    private val xtream: XtreamClient,
) {
    val activePlaylist: Flow<Playlist?> = db.playlistDao().observeActive()

    /** One-shot lookup of the active playlist; null when none is selected. */
    suspend fun getActivePlaylist(): Playlist? = db.playlistDao().getActive()

    val allPlaylists: Flow<List<Playlist>> = db.playlistDao().observeAll()

    /** Makes [id] the single active playlist; observers of [activePlaylist] re-emit. */
    suspend fun setActivePlaylist(id: Long) = db.playlistDao().setActive(id)

    // Deleting cascades that playlist's catalog; stable user state has no FK so
    // it is cleaned explicitly. Returns how many playlists are left.
    suspend fun deletePlaylist(id: Long): Int {
        val dao = db.playlistDao()
        val wasActive = dao.getActive()?.id == id
        dao.delete(id)
        db.bookmarkDao().wipeForPlaylist(id)
        db.recentViewDao().wipeForPlaylist(id)
        db.watchProgressDao().wipeForPlaylist(id)
        val remaining = dao.getAll()
        if (wasActive) {
            remaining.firstOrNull()?.let { dao.setActive(it.id) }
        }
        return remaining.size
    }

    // ---- Live ----

    /** Live categories for the rail, emitted in provider sort order. */
    fun liveCategories(playlistId: Long) = db.liveDao().categories(playlistId)

    /**
     * Windowed paging stream of channels. A null category means "all"; the
     * negative virtual ids map to the bookmarks and recents rails.
     */
    fun channelsPager(playlistId: Long, categoryId: Long?): Flow<PagingData<LiveChannel>> =
        pager {
            when (categoryId) {
                null -> db.liveDao().allChannels(playlistId)
                CATEGORY_BOOKMARKS -> db.liveDao().bookmarkedChannels(playlistId)
                CATEGORY_RECENT -> db.liveDao().recentChannels(playlistId)
                else -> db.liveDao().channelsByCategory(playlistId, categoryId)
            }
        }

    /** Paged FTS search over channels; [query] must be [ftsPrefixQuery] output. */
    fun searchChannelsPager(playlistId: Long, query: String): Flow<PagingData<LiveChannel>> =
        pager { db.liveDao().search(playlistId, query) }

    /** Channel lookup by local row id; null if the row was replaced by a sync. */
    suspend fun channelById(id: Long): LiveChannel? = db.liveDao().byId(id)

    /** Live category lookup by local row id, for restoring rail state. */
    suspend fun liveCategoryById(id: Long) = db.liveDao().categoryById(id)

    /** Next channel for zapping within the category (or all); wraps to the first at the end. */
    suspend fun nextChannel(channel: LiveChannel, categoryId: Long?): LiveChannel? =
        db.liveDao().nextChannel(channel.playlistId, categoryId, channel.num, channel.id)
            ?: db.liveDao().firstChannel(channel.playlistId, categoryId)

    /** Previous channel for zapping; wraps to the last at the start. */
    suspend fun prevChannel(channel: LiveChannel, categoryId: Long?): LiveChannel? =
        db.liveDao().prevChannel(channel.playlistId, categoryId, channel.num, channel.id)
            ?: db.liveDao().lastChannel(channel.playlistId, categoryId)

    /** Zero-based list position of a channel number, used to scroll the grid to it. */
    suspend fun positionOfChannelNum(playlistId: Long, categoryId: Long?, num: Int): Int =
        db.liveDao().positionOfNum(playlistId, categoryId, num)

    // Candidate URLs in retry order for a live channel, honoring the format setting.
    suspend fun liveStreamUrls(channel: LiveChannel, format: LiveFormat): List<String> {
        channel.urlOverride?.let { return listOf(it) }
        val playlist = db.playlistDao().getById(channel.playlistId) ?: return emptyList()
        return when (format) {
            LiveFormat.AUTO -> listOf(
                xtream.liveUrl(playlist, channel.streamId, "m3u8"),
                xtream.liveUrl(playlist, channel.streamId, "ts"),
            )
            LiveFormat.HLS -> listOf(xtream.liveUrl(playlist, channel.streamId, "m3u8"))
            LiveFormat.TS -> listOf(xtream.liveUrl(playlist, channel.streamId, "ts"))
        }
    }

    // ---- Movies ----

    /** VOD categories for the movie rail, in provider sort order. */
    fun vodCategories(playlistId: Long) = db.movieDao().categories(playlistId)

    /** Paged movie stream; null category is "all", virtual ids select bookmarks/recents. */
    fun moviesPager(playlistId: Long, categoryId: Long?): Flow<PagingData<Movie>> =
        pager {
            when (categoryId) {
                null -> db.movieDao().allMovies(playlistId)
                CATEGORY_BOOKMARKS -> db.movieDao().bookmarkedMovies(playlistId)
                CATEGORY_RECENT -> db.movieDao().recentMovies(playlistId)
                else -> db.movieDao().moviesByCategory(playlistId, categoryId)
            }
        }

    /** Paged FTS search over movies; [query] must be [ftsPrefixQuery] output. */
    fun searchMoviesPager(playlistId: Long, query: String): Flow<PagingData<Movie>> =
        pager { db.movieDao().search(playlistId, query) }

    /** Movie lookup by local row id; null if the row was replaced by a sync. */
    suspend fun movieById(id: Long): Movie? = db.movieDao().byId(id)

    /** Emits the movie row on every change (e.g. after [refreshMovieDetails]), or null if gone. */
    fun observeMovie(id: Long): Flow<Movie?> = db.movieDao().observeById(id)

    /**
     * Fetches plot/genre/backdrop details from Xtream on demand and writes them
     * into the row observers already watch. Silently no-ops for non-Xtream
     * playlists or an empty response; network errors come back as a failed Result.
     */
    suspend fun refreshMovieDetails(movie: Movie): Result<Unit> = runCatching {
        val playlist = db.playlistDao().getById(movie.playlistId) ?: error("Playlist missing")
        if (playlist.type != Playlist.TYPE_XTREAM) return@runCatching
        val info = xtream.vodInfo(playlist, movie.streamId).info ?: return@runCatching
        db.movieDao().updateDetails(
            id = movie.id,
            plot = info.plot,
            genre = info.genre,
            durationSecs = info.durationSecs?.toInt(),
            backdropUrl = info.backdropPath,
            year = info.releaseDate?.take(4),
            rating = info.rating,
        )
    }

    /** Builds the playable URL for a movie; null if its playlist no longer exists. */
    suspend fun movieStreamUrl(movie: Movie): String? {
        val playlist = db.playlistDao().getById(movie.playlistId) ?: return null
        return xtream.movieUrl(playlist, movie.streamId, movie.containerExtension)
    }

    // ---- Series ----

    /** Series categories for the rail, in provider sort order. */
    fun seriesCategories(playlistId: Long) = db.seriesDao().categories(playlistId)

    /** Paged series stream; null category is "all", virtual ids select bookmarks/recents. */
    fun seriesPager(playlistId: Long, categoryId: Long?): Flow<PagingData<Series>> =
        pager {
            when (categoryId) {
                null -> db.seriesDao().allSeries(playlistId)
                CATEGORY_BOOKMARKS -> db.seriesDao().bookmarkedSeries(playlistId)
                CATEGORY_RECENT -> db.seriesDao().recentSeries(playlistId)
                else -> db.seriesDao().seriesByCategory(playlistId, categoryId)
            }
        }

    /** Paged FTS search over series; [query] must be [ftsPrefixQuery] output. */
    fun searchSeriesPager(playlistId: Long, query: String): Flow<PagingData<Series>> =
        pager { db.seriesDao().search(playlistId, query) }

    /** Series lookup by local row id; null if the row was replaced by a sync. */
    suspend fun seriesById(id: Long): Series? = db.seriesDao().byId(id)

    /** Emits the series row on every change (e.g. after [refreshSeriesInfo]), or null if gone. */
    fun observeSeries(id: Long): Flow<Series?> = db.seriesDao().observeById(id)

    /** Emits the cached episode list, updating when [refreshSeriesInfo] replaces it. */
    fun episodes(seriesLocalId: Long): Flow<List<Episode>> = db.episodeDao().episodes(seriesLocalId)

    /** Episode lookup by local row id, used when resuming playback from a route. */
    suspend fun episodeById(id: Long): Episode? = db.episodeDao().byId(id)

    // Episodes are fetched on demand when a details screen opens, replacing any
    // previously cached set for that series.
    suspend fun refreshSeriesInfo(series: Series): Result<Unit> = runCatching {
        val playlist = db.playlistDao().getById(series.playlistId) ?: error("Playlist missing")
        if (playlist.type != Playlist.TYPE_XTREAM) return@runCatching
        val (info, episodeDtos) = xtream.seriesInfo(playlist, series.seriesId)
        if (info != null) {
            db.seriesDao().updateDetails(
                id = series.id,
                plot = info.plot,
                backdropUrl = info.backdropPath,
                rating = info.rating,
                year = info.releaseDate?.take(4),
            )
        }
        val episodes = episodeDtos.mapNotNull { dto ->
            if (dto.id <= 0) return@mapNotNull null
            Episode(
                playlistId = series.playlistId,
                seriesLocalId = series.id,
                remoteEpisodeId = dto.id,
                season = dto.season,
                episodeNum = dto.episodeNum,
                title = dto.title ?: "Episode ${dto.episodeNum}",
                containerExtension = dto.containerExtension,
                durationSecs = dto.info?.durationSecs?.toInt(),
                thumbnailUrl = dto.info?.movieImage,
            )
        }
        if (episodes.isNotEmpty()) {
            db.episodeDao().wipeForSeries(series.id)
            db.episodeDao().insertAll(episodes)
        }
    }

    /** Builds the playable URL for an episode; null if its playlist no longer exists. */
    suspend fun episodeStreamUrl(episode: Episode): String? {
        val playlist = db.playlistDao().getById(episode.playlistId) ?: return null
        return xtream.episodeUrl(playlist, episode.remoteEpisodeId, episode.containerExtension)
    }

    // ---- Bookmarks & recents ----

    // Set of bookmarked remote ids (streamId/seriesId) for fast per-item state.
    fun bookmarkedIds(playlistId: Long, mediaType: String): Flow<Set<Long>> =
        db.bookmarkDao().observeIds(playlistId, mediaType).map { it.toSet() }

    /** Adds or removes a bookmark keyed by remote id, so it survives catalogue replacement. */
    suspend fun toggleBookmark(playlistId: Long, mediaType: String, remoteId: Long) {
        val dao = db.bookmarkDao()
        if (dao.exists(playlistId, mediaType, remoteId)) {
            dao.delete(playlistId, mediaType, remoteId)
        } else {
            dao.upsert(
                Bookmark(
                    playlistId = playlistId,
                    mediaType = mediaType,
                    remoteId = remoteId,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Stamps an item as recently viewed and trims history to the newest [RECENTS_KEPT] per type. */
    suspend fun recordRecentView(playlistId: Long, mediaType: String, remoteId: Long) {
        db.recentViewDao().upsert(
            RecentView(
                playlistId = playlistId,
                mediaType = mediaType,
                remoteId = remoteId,
                viewedAt = System.currentTimeMillis(),
            ),
        )
        db.recentViewDao().trim(playlistId, mediaType, RECENTS_KEPT)
    }

    // ---- Watch progress ----

    // The route still carries a local row id, but DAO joins immediately resolve
    // it to the stable (playlistId, provider remote id) progress key.
    suspend fun watchProgress(mediaType: String, mediaId: Long): WatchProgress? =
        when (mediaType) {
            WatchProgress.MEDIA_MOVIE -> db.watchProgressDao().getForMovie(mediaId)
            WatchProgress.MEDIA_EPISODE -> db.watchProgressDao().getForEpisode(mediaId)
            else -> null
        }

    // Emits the stored progress for the item (null when none, or a constant
    // null flow for unknown media types), updating live as playback saves.
    fun observeWatchProgress(mediaType: String, mediaId: Long): Flow<WatchProgress?> =
        when (mediaType) {
            WatchProgress.MEDIA_MOVIE -> db.watchProgressDao().observeForMovie(mediaId)
            WatchProgress.MEDIA_EPISODE -> db.watchProgressDao().observeForEpisode(mediaId)
            else -> flowOf(null)
        }

    /** Emits progress rows for all episodes of a series, driving the "continue watching" badges. */
    fun watchProgressForSeries(seriesLocalId: Long): Flow<List<WatchProgress>> =
        db.watchProgressDao().forSeries(seriesLocalId)

    /** Upserts playback position keyed by (playlistId, remote id), replacing any earlier record. */
    suspend fun saveWatchProgress(progress: WatchProgress) = db.watchProgressDao().upsert(progress)

    // Wraps a PagingSource factory in a Pager with the shared windowed config.
    private fun <T : Any> pager(source: () -> androidx.paging.PagingSource<Int, T>): Flow<PagingData<T>> =
        Pager(config = PAGING_CONFIG, pagingSourceFactory = source).flow

    companion object {
        // Virtual category ids used by the UI rail alongside real category rows.
        const val CATEGORY_BOOKMARKS = -2L
        const val CATEGORY_RECENT = -3L

        private const val RECENTS_KEPT = 60

        // Windowed paging keeps a 50k-row catalog to a few hundred rows in memory.
        private val PAGING_CONFIG = PagingConfig(
            pageSize = 60,
            prefetchDistance = 60,
            initialLoadSize = 120,
            maxSize = 400,
            enablePlaceholders = false,
        )

        // FTS MATCH input: strip operators, then prefix-match every word —
        // "bat mov" becomes "bat* mov*". Never LIKE '%…%'.
        fun ftsPrefixQuery(raw: String): String? {
            val words = raw.lowercase()
                .replace(FTS_OPERATORS, " ")
                .split(WHITESPACE)
                .filter { it.isNotBlank() }
            if (words.isEmpty()) return null
            return words.joinToString(" ") { "$it*" }
        }

        private val FTS_OPERATORS = Regex("""["*^:()\-~{}]""")
        private val WHITESPACE = Regex("""\s+""")
    }
}
