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

@Singleton
class ContentRepository @Inject constructor(
    private val db: NovaDatabase,
    private val xtream: XtreamClient,
) {
    val activePlaylist: Flow<Playlist?> = db.playlistDao().observeActive()

    suspend fun getActivePlaylist(): Playlist? = db.playlistDao().getActive()

    val allPlaylists: Flow<List<Playlist>> = db.playlistDao().observeAll()

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

    fun liveCategories(playlistId: Long) = db.liveDao().categories(playlistId)

    fun channelsPager(playlistId: Long, categoryId: Long?): Flow<PagingData<LiveChannel>> =
        pager {
            when (categoryId) {
                null -> db.liveDao().allChannels(playlistId)
                CATEGORY_BOOKMARKS -> db.liveDao().bookmarkedChannels(playlistId)
                CATEGORY_RECENT -> db.liveDao().recentChannels(playlistId)
                else -> db.liveDao().channelsByCategory(playlistId, categoryId)
            }
        }

    fun searchChannelsPager(playlistId: Long, query: String): Flow<PagingData<LiveChannel>> =
        pager { db.liveDao().search(playlistId, query) }

    suspend fun channelById(id: Long): LiveChannel? = db.liveDao().byId(id)

    suspend fun liveCategoryById(id: Long) = db.liveDao().categoryById(id)

    suspend fun nextChannel(channel: LiveChannel, categoryId: Long?): LiveChannel? =
        db.liveDao().nextChannel(channel.playlistId, categoryId, channel.num, channel.id)
            ?: db.liveDao().firstChannel(channel.playlistId, categoryId)

    suspend fun prevChannel(channel: LiveChannel, categoryId: Long?): LiveChannel? =
        db.liveDao().prevChannel(channel.playlistId, categoryId, channel.num, channel.id)
            ?: db.liveDao().lastChannel(channel.playlistId, categoryId)

    suspend fun positionOfChannelNum(playlistId: Long, categoryId: Long?, num: Int): Int =
        db.liveDao().positionOfNum(playlistId, categoryId, num)

    // Candidate URLs in retry order for a live channel, honoring the format setting.
    suspend fun liveStreamUrls(channel: LiveChannel, format: LiveFormat): List<String> {
        channel.urlOverride?.let { return listOf(it) }
        val playlist = db.playlistDao().getById(channel.playlistId) ?: return emptyList()
        return when (format) {
            LiveFormat.AUTO -> listOf(
                XtreamClient.liveUrl(playlist, channel.streamId, "m3u8"),
                XtreamClient.liveUrl(playlist, channel.streamId, "ts"),
            )
            LiveFormat.HLS -> listOf(XtreamClient.liveUrl(playlist, channel.streamId, "m3u8"))
            LiveFormat.TS -> listOf(XtreamClient.liveUrl(playlist, channel.streamId, "ts"))
        }
    }

    // ---- Movies ----

    fun vodCategories(playlistId: Long) = db.movieDao().categories(playlistId)

    fun moviesPager(playlistId: Long, categoryId: Long?): Flow<PagingData<Movie>> =
        pager {
            when (categoryId) {
                null -> db.movieDao().allMovies(playlistId)
                CATEGORY_BOOKMARKS -> db.movieDao().bookmarkedMovies(playlistId)
                CATEGORY_RECENT -> db.movieDao().recentMovies(playlistId)
                else -> db.movieDao().moviesByCategory(playlistId, categoryId)
            }
        }

    fun searchMoviesPager(playlistId: Long, query: String): Flow<PagingData<Movie>> =
        pager { db.movieDao().search(playlistId, query) }

    suspend fun movieById(id: Long): Movie? = db.movieDao().byId(id)

    fun observeMovie(id: Long): Flow<Movie?> = db.movieDao().observeById(id)

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

    suspend fun movieStreamUrl(movie: Movie): String? {
        val playlist = db.playlistDao().getById(movie.playlistId) ?: return null
        return XtreamClient.movieUrl(playlist, movie.streamId, movie.containerExtension)
    }

    // ---- Series ----

    fun seriesCategories(playlistId: Long) = db.seriesDao().categories(playlistId)

    fun seriesPager(playlistId: Long, categoryId: Long?): Flow<PagingData<Series>> =
        pager {
            when (categoryId) {
                null -> db.seriesDao().allSeries(playlistId)
                CATEGORY_BOOKMARKS -> db.seriesDao().bookmarkedSeries(playlistId)
                CATEGORY_RECENT -> db.seriesDao().recentSeries(playlistId)
                else -> db.seriesDao().seriesByCategory(playlistId, categoryId)
            }
        }

    fun searchSeriesPager(playlistId: Long, query: String): Flow<PagingData<Series>> =
        pager { db.seriesDao().search(playlistId, query) }

    suspend fun seriesById(id: Long): Series? = db.seriesDao().byId(id)

    fun observeSeries(id: Long): Flow<Series?> = db.seriesDao().observeById(id)

    fun episodes(seriesLocalId: Long): Flow<List<Episode>> = db.episodeDao().episodes(seriesLocalId)

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

    suspend fun episodeStreamUrl(episode: Episode): String? {
        val playlist = db.playlistDao().getById(episode.playlistId) ?: return null
        return XtreamClient.episodeUrl(playlist, episode.remoteEpisodeId, episode.containerExtension)
    }

    // ---- Bookmarks & recents ----

    // Set of bookmarked remote ids (streamId/seriesId) for fast per-item state.
    fun bookmarkedIds(playlistId: Long, mediaType: String): Flow<Set<Long>> =
        db.bookmarkDao().observeIds(playlistId, mediaType).map { it.toSet() }

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

    fun observeWatchProgress(mediaType: String, mediaId: Long): Flow<WatchProgress?> =
        when (mediaType) {
            WatchProgress.MEDIA_MOVIE -> db.watchProgressDao().observeForMovie(mediaId)
            WatchProgress.MEDIA_EPISODE -> db.watchProgressDao().observeForEpisode(mediaId)
            else -> flowOf(null)
        }

    fun watchProgressForSeries(seriesLocalId: Long): Flow<List<WatchProgress>> =
        db.watchProgressDao().forSeries(seriesLocalId)

    suspend fun saveWatchProgress(progress: WatchProgress) = db.watchProgressDao().upsert(progress)

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
