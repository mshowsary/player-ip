package com.novaplay.tv.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<Playlist?>

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Playlist?

    @Query("SELECT * FROM playlists ORDER BY name")
    fun observeAll(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY name")
    suspend fun getAll(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): Playlist?

    @Query("SELECT * FROM playlists WHERE portalId = :portalId LIMIT 1")
    suspend fun getByPortalId(portalId: Long): Playlist?

    @Insert
    suspend fun insert(playlist: Playlist): Long

    @Update
    suspend fun update(playlist: Playlist)

    @Query("UPDATE playlists SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActive(id: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    @Query("UPDATE playlists SET expiryEpochSec = :expiryEpochSec, maxConnections = :maxConnections WHERE id = :id")
    suspend fun updateAccountInfo(id: Long, expiryEpochSec: Long?, maxConnections: Int?)

    @Query("UPDATE playlists SET lastSyncEpochMs = :at WHERE id = :id")
    suspend fun updateLastSync(id: Long, at: Long)
}

@Dao
interface LiveDao {
    @Query("SELECT * FROM live_categories WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun categories(playlistId: Long): Flow<List<LiveCategory>>

    @Query("SELECT * FROM live_channels WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY num, id")
    fun channelsByCategory(playlistId: Long, categoryId: Long): PagingSource<Int, LiveChannel>

    @Query("SELECT * FROM live_channels WHERE playlistId = :playlistId ORDER BY num, id")
    fun allChannels(playlistId: Long): PagingSource<Int, LiveChannel>

    @Query(
        """SELECT c.* FROM live_channels c
           JOIN bookmarks b ON b.playlistId = c.playlistId AND b.mediaType = 'live' AND b.remoteId = c.streamId
           WHERE c.playlistId = :playlistId ORDER BY b.createdAt DESC, c.id""",
    )
    fun bookmarkedChannels(playlistId: Long): PagingSource<Int, LiveChannel>

    @Query(
        """SELECT c.* FROM live_channels c
           JOIN recent_views r ON r.playlistId = c.playlistId AND r.mediaType = 'live' AND r.remoteId = c.streamId
           WHERE c.playlistId = :playlistId ORDER BY r.viewedAt DESC, c.id""",
    )
    fun recentChannels(playlistId: Long): PagingSource<Int, LiveChannel>

    @Query(
        """SELECT c.* FROM live_channels c
           JOIN live_channel_fts ON c.id = live_channel_fts.rowid
           WHERE live_channel_fts MATCH :ftsQuery AND c.playlistId = :playlistId
           ORDER BY c.num, c.id""",
    )
    fun search(playlistId: Long, ftsQuery: String): PagingSource<Int, LiveChannel>

    @Query("SELECT * FROM live_channels WHERE id = :id")
    suspend fun byId(id: Long): LiveChannel?

    @Query("SELECT * FROM live_categories WHERE id = :id")
    suspend fun categoryById(id: Long): LiveCategory?

    // Zapping: (num, id) is the browse order; the OR filter keeps one query for
    // both "All" (:categoryId null) and a specific category.
    @Query(
        """SELECT * FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND (num > :num OR (num = :num AND id > :id))
           ORDER BY num, id LIMIT 1""",
    )
    suspend fun nextChannel(playlistId: Long, categoryId: Long?, num: Int, id: Long): LiveChannel?

    @Query(
        """SELECT * FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND (num < :num OR (num = :num AND id < :id))
           ORDER BY num DESC, id DESC LIMIT 1""",
    )
    suspend fun prevChannel(playlistId: Long, categoryId: Long?, num: Int, id: Long): LiveChannel?

    @Query(
        """SELECT * FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
           ORDER BY num, id LIMIT 1""",
    )
    suspend fun firstChannel(playlistId: Long, categoryId: Long?): LiveChannel?

    @Query(
        """SELECT * FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
           ORDER BY num DESC, id DESC LIMIT 1""",
    )
    suspend fun lastChannel(playlistId: Long, categoryId: Long?): LiveChannel?

    // Digit-jump: list index of the first channel whose num >= :num.
    @Query(
        """SELECT COUNT(*) FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND num < :num""",
    )
    suspend fun positionOfNum(playlistId: Long, categoryId: Long?, num: Int): Int

    @Query("SELECT COUNT(*) FROM live_channels WHERE playlistId = :playlistId")
    suspend fun channelCount(playlistId: Long): Int

    @Insert
    suspend fun insertCategories(items: List<LiveCategory>): List<Long>

    @Insert
    suspend fun insertChannels(items: List<LiveChannel>)

    @Query("DELETE FROM live_categories WHERE playlistId = :playlistId")
    suspend fun wipeCategories(playlistId: Long)

    @Query("DELETE FROM live_channels WHERE playlistId = :playlistId")
    suspend fun wipeChannels(playlistId: Long)
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM vod_categories WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun categories(playlistId: Long): Flow<List<VodCategory>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY name, id")
    fun moviesByCategory(playlistId: Long, categoryId: Long): PagingSource<Int, Movie>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY name, id")
    fun allMovies(playlistId: Long): PagingSource<Int, Movie>

    @Query(
        """SELECT m.* FROM movies m
           JOIN bookmarks b ON b.playlistId = m.playlistId AND b.mediaType = 'movie' AND b.remoteId = m.streamId
           WHERE m.playlistId = :playlistId ORDER BY b.createdAt DESC, m.id""",
    )
    fun bookmarkedMovies(playlistId: Long): PagingSource<Int, Movie>

    @Query(
        """SELECT m.* FROM movies m
           JOIN recent_views r ON r.playlistId = m.playlistId AND r.mediaType = 'movie' AND r.remoteId = m.streamId
           WHERE m.playlistId = :playlistId ORDER BY r.viewedAt DESC, m.id""",
    )
    fun recentMovies(playlistId: Long): PagingSource<Int, Movie>

    @Query(
        """SELECT m.* FROM movies m
           JOIN movie_fts ON m.id = movie_fts.rowid
           WHERE movie_fts MATCH :ftsQuery AND m.playlistId = :playlistId
           ORDER BY m.name, m.id""",
    )
    fun search(playlistId: Long, ftsQuery: String): PagingSource<Int, Movie>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun byId(id: Long): Movie?

    @Query("SELECT * FROM movies WHERE id = :id")
    fun observeById(id: Long): Flow<Movie?>

    @Query(
        """UPDATE movies SET plot = :plot, genre = :genre, durationSecs = :durationSecs,
           backdropUrl = :backdropUrl, year = coalesce(:year, year), rating = coalesce(:rating, rating)
           WHERE id = :id""",
    )
    suspend fun updateDetails(
        id: Long,
        plot: String?,
        genre: String?,
        durationSecs: Int?,
        backdropUrl: String?,
        year: String?,
        rating: Double?,
    )

    @Insert
    suspend fun insertCategories(items: List<VodCategory>): List<Long>

    @Insert
    suspend fun insertMovies(items: List<Movie>)

    @Query("DELETE FROM vod_categories WHERE playlistId = :playlistId")
    suspend fun wipeCategories(playlistId: Long)

    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun wipeMovies(playlistId: Long)
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series_categories WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun categories(playlistId: Long): Flow<List<SeriesCategory>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY name, id")
    fun seriesByCategory(playlistId: Long, categoryId: Long): PagingSource<Int, Series>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY name, id")
    fun allSeries(playlistId: Long): PagingSource<Int, Series>

    @Query(
        """SELECT s.* FROM series s
           JOIN bookmarks b ON b.playlistId = s.playlistId AND b.mediaType = 'series' AND b.remoteId = s.seriesId
           WHERE s.playlistId = :playlistId ORDER BY b.createdAt DESC, s.id""",
    )
    fun bookmarkedSeries(playlistId: Long): PagingSource<Int, Series>

    @Query(
        """SELECT s.* FROM series s
           JOIN recent_views r ON r.playlistId = s.playlistId AND r.mediaType = 'series' AND r.remoteId = s.seriesId
           WHERE s.playlistId = :playlistId ORDER BY r.viewedAt DESC, s.id""",
    )
    fun recentSeries(playlistId: Long): PagingSource<Int, Series>

    @Query(
        """SELECT s.* FROM series s
           JOIN series_fts ON s.id = series_fts.rowid
           WHERE series_fts MATCH :ftsQuery AND s.playlistId = :playlistId
           ORDER BY s.name, s.id""",
    )
    fun search(playlistId: Long, ftsQuery: String): PagingSource<Int, Series>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun byId(id: Long): Series?

    @Query("SELECT * FROM series WHERE id = :id")
    fun observeById(id: Long): Flow<Series?>

    @Query(
        """UPDATE series SET plot = coalesce(:plot, plot), backdropUrl = coalesce(:backdropUrl, backdropUrl),
           rating = coalesce(:rating, rating), year = coalesce(:year, year)
           WHERE id = :id""",
    )
    suspend fun updateDetails(id: Long, plot: String?, backdropUrl: String?, rating: Double?, year: String?)

    @Insert
    suspend fun insertCategories(items: List<SeriesCategory>): List<Long>

    @Insert
    suspend fun insertSeries(items: List<Series>)

    @Query("DELETE FROM series_categories WHERE playlistId = :playlistId")
    suspend fun wipeCategories(playlistId: Long)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun wipeSeries(playlistId: Long)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE seriesLocalId = :seriesLocalId ORDER BY season, episodeNum")
    fun episodes(seriesLocalId: Long): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun byId(id: Long): Episode?

    @Insert
    suspend fun insertAll(items: List<Episode>)

    @Query("DELETE FROM episodes WHERE seriesLocalId = :seriesLocalId")
    suspend fun wipeForSeries(seriesLocalId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT remoteId FROM bookmarks WHERE playlistId = :playlistId AND mediaType = :mediaType")
    fun observeIds(playlistId: Long, mediaType: String): Flow<List<Long>>

    @Query(
        """SELECT EXISTS(SELECT 1 FROM bookmarks
           WHERE playlistId = :playlistId AND mediaType = :mediaType AND remoteId = :remoteId)""",
    )
    suspend fun exists(playlistId: Long, mediaType: String, remoteId: Long): Boolean

    @Upsert
    suspend fun upsert(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE playlistId = :playlistId AND mediaType = :mediaType AND remoteId = :remoteId")
    suspend fun delete(playlistId: Long, mediaType: String, remoteId: Long)

    @Query("DELETE FROM bookmarks WHERE playlistId = :playlistId")
    suspend fun wipeForPlaylist(playlistId: Long)
}

@Dao
interface RecentViewDao {
    @Upsert
    suspend fun upsert(view: RecentView)

    // Cap the history per (playlist, type); oldest entries fall off.
    @Query(
        """DELETE FROM recent_views
           WHERE playlistId = :playlistId AND mediaType = :mediaType AND remoteId NOT IN (
               SELECT remoteId FROM recent_views
               WHERE playlistId = :playlistId AND mediaType = :mediaType
               ORDER BY viewedAt DESC LIMIT :keep
           )""",
    )
    suspend fun trim(playlistId: Long, mediaType: String, keep: Int)

    @Query("DELETE FROM recent_views WHERE playlistId = :playlistId")
    suspend fun wipeForPlaylist(playlistId: Long)
}

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE mediaType = :mediaType AND mediaId = :mediaId")
    suspend fun get(mediaType: String, mediaId: Long): WatchProgress?

    @Query("SELECT * FROM watch_progress WHERE mediaType = :mediaType AND mediaId = :mediaId")
    fun observe(mediaType: String, mediaId: Long): Flow<WatchProgress?>

    @Query(
        """SELECT wp.* FROM watch_progress wp
           JOIN episodes e ON wp.mediaId = e.id
           WHERE wp.mediaType = 'episode' AND e.seriesLocalId = :seriesLocalId""",
    )
    fun forSeries(seriesLocalId: Long): Flow<List<WatchProgress>>

    @Upsert
    suspend fun upsert(progress: WatchProgress)
}
