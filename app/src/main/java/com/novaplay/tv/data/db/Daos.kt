package com.novaplay.tv.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Playlist sources (Xtream accounts / M3U urls) and the single-active selection. */
@Dao
interface PlaylistDao {
    /** Emits the active playlist (at most one row has isActive = 1), or null when none is selected. */
    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<Playlist?>

    /** One-shot read of the active playlist; null when none is selected. */
    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Playlist?

    /** All playlists ordered by name, re-emitted on any change. */
    @Query("SELECT * FROM playlists ORDER BY name")
    fun observeAll(): Flow<List<Playlist>>

    /** One-shot read of all playlists ordered by name. */
    @Query("SELECT * FROM playlists ORDER BY name")
    suspend fun getAll(): List<Playlist>

    /** Looks up one playlist by local row id. */
    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): Playlist?

    /** Finds the playlist provisioned from a given portal entry (portalId is unique). */
    @Query("SELECT * FROM playlists WHERE portalId = :portalId LIMIT 1")
    suspend fun getByPortalId(portalId: Long): Playlist?

    /** Inserts a new playlist and returns its generated row id. */
    @Insert
    suspend fun insert(playlist: Playlist): Long

    /** Rewrites all columns of an existing playlist row. */
    @Update
    suspend fun update(playlist: Playlist)

    /** Activates one playlist and deactivates every other in a single statement, so exactly one stays active. */
    @Query("UPDATE playlists SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActive(id: Long)

    /** Deletes a playlist; foreign keys cascade the whole catalogue away with it. */
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    /** Number of stored playlists; drives the first-run / empty-state flow. */
    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int

    /** Persists the account limits reported by the provider's auth handshake. */
    @Query("UPDATE playlists SET expiryEpochSec = :expiryEpochSec, maxConnections = :maxConnections WHERE id = :id")
    suspend fun updateAccountInfo(id: Long, expiryEpochSec: Long?, maxConnections: Int?)

    /** Records when the catalogue was last synced, for staleness checks. */
    @Query("UPDATE playlists SET lastSyncEpochMs = :at WHERE id = :id")
    suspend fun updateLastSync(id: Long, at: Long)

    /** Stores the guide source discovered from the M3U header; the value must already be sealed. */
    @Query("UPDATE playlists SET epgUrl = :epgUrl WHERE id = :id")
    suspend fun updateEpgUrl(id: Long, epgUrl: String?)
}

/**
 * Live TV catalogue: category lists, paged channel windows, zapping lookups and
 * FTS search. Channel queries follow the indexed (playlistId, categoryId, num)
 * browse order so Paging 3 windows never scan.
 *
 * Every content query takes a :hidden set of parental-locked category ids and
 * excludes their rows (uncategorized rows are never locked). Callers pass an
 * empty set when nothing is locked or the session is unlocked — SQLite treats
 * NOT IN () as always true.
 */
@Dao
interface LiveDao {
    /** Live categories of one playlist in the provider's sort order. */
    @Query("SELECT * FROM live_categories WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun categories(playlistId: Long): Flow<List<LiveCategory>>

    /** Paged channels of one category in (num, id) browse order; a locked category pages empty. */
    @Query(
        """SELECT * FROM live_channels WHERE playlistId = :playlistId AND categoryId = :categoryId
           AND categoryId NOT IN (:hidden) ORDER BY num, id""",
    )
    fun channelsByCategory(playlistId: Long, categoryId: Long, hidden: Set<Long>): PagingSource<Int, LiveChannel>

    /** Paged channels across every category ("All"), same (num, id) order. */
    @Query(
        """SELECT * FROM live_channels WHERE playlistId = :playlistId
           AND (categoryId IS NULL OR categoryId NOT IN (:hidden)) ORDER BY num, id""",
    )
    fun allChannels(playlistId: Long, hidden: Set<Long>): PagingSource<Int, LiveChannel>

    /** Paged bookmarked channels, newest bookmark first. Joins on the stable streamId so bookmarks survive re-syncs. */
    @Query(
        """SELECT c.* FROM live_channels c
           JOIN bookmarks b ON b.playlistId = c.playlistId AND b.mediaType = 'live' AND b.remoteId = c.streamId
           WHERE c.playlistId = :playlistId AND (c.categoryId IS NULL OR c.categoryId NOT IN (:hidden))
           ORDER BY b.createdAt DESC, c.id""",
    )
    fun bookmarkedChannels(playlistId: Long, hidden: Set<Long>): PagingSource<Int, LiveChannel>

    /** Paged recently-watched channels, most recently viewed first. */
    @Query(
        """SELECT c.* FROM live_channels c
           JOIN recent_views r ON r.playlistId = c.playlistId AND r.mediaType = 'live' AND r.remoteId = c.streamId
           WHERE c.playlistId = :playlistId AND (c.categoryId IS NULL OR c.categoryId NOT IN (:hidden))
           ORDER BY r.viewedAt DESC, c.id""",
    )
    fun recentChannels(playlistId: Long, hidden: Set<Long>): PagingSource<Int, LiveChannel>

    /** Paged FTS search over channel names in browse order. ftsQuery must be a MATCH expression with prefix tokens ("spo*"), never a LIKE pattern. */
    @Query(
        """SELECT c.* FROM live_channels c
           JOIN live_channel_fts ON c.id = live_channel_fts.rowid
           WHERE live_channel_fts MATCH :ftsQuery AND c.playlistId = :playlistId
             AND (c.categoryId IS NULL OR c.categoryId NOT IN (:hidden))
           ORDER BY c.num, c.id""",
    )
    fun search(playlistId: Long, ftsQuery: String, hidden: Set<Long>): PagingSource<Int, LiveChannel>

    /** Loads one channel by local row id. */
    @Query("SELECT * FROM live_channels WHERE id = :id")
    suspend fun byId(id: Long): LiveChannel?

    /** Loads one live category by local row id. */
    @Query("SELECT * FROM live_categories WHERE id = :id")
    suspend fun categoryById(id: Long): LiveCategory?

    // Zapping: (num, id) is the browse order; the OR filter keeps one query for
    // both "All" (:categoryId null) and a specific category. Locked categories
    // are skipped so channel up/down never lands inside them.
    @Query(
        """SELECT * FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND (categoryId IS NULL OR categoryId NOT IN (:hidden))
             AND (num > :num OR (num = :num AND id > :id))
           ORDER BY num, id LIMIT 1""",
    )
    suspend fun nextChannel(playlistId: Long, categoryId: Long?, num: Int, id: Long, hidden: Set<Long>): LiveChannel?

    /** Zapping counterpart of [nextChannel]: the channel just before (num, id) in browse order, or null at the start. */
    @Query(
        """SELECT * FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND (categoryId IS NULL OR categoryId NOT IN (:hidden))
             AND (num < :num OR (num = :num AND id < :id))
           ORDER BY num DESC, id DESC LIMIT 1""",
    )
    suspend fun prevChannel(playlistId: Long, categoryId: Long?, num: Int, id: Long, hidden: Set<Long>): LiveChannel?

    /** First channel in browse order — the wrap-around target when zapping past the last channel. */
    @Query(
        """SELECT * FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND (categoryId IS NULL OR categoryId NOT IN (:hidden))
           ORDER BY num, id LIMIT 1""",
    )
    suspend fun firstChannel(playlistId: Long, categoryId: Long?, hidden: Set<Long>): LiveChannel?

    /** Last channel in browse order — the wrap-around target when zapping back from the first channel. */
    @Query(
        """SELECT * FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND (categoryId IS NULL OR categoryId NOT IN (:hidden))
           ORDER BY num DESC, id DESC LIMIT 1""",
    )
    suspend fun lastChannel(playlistId: Long, categoryId: Long?, hidden: Set<Long>): LiveChannel?

    // In-player digit zap: the first channel at or after :num in browse order,
    // falling back to the last channel when the number is beyond the lineup.
    @Query(
        """SELECT * FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND (categoryId IS NULL OR categoryId NOT IN (:hidden))
             AND num >= :num
           ORDER BY num, id LIMIT 1""",
    )
    suspend fun channelAtOrAfterNum(playlistId: Long, categoryId: Long?, num: Int, hidden: Set<Long>): LiveChannel?

    // Digit-jump: list index of the first channel whose num >= :num.
    @Query(
        """SELECT COUNT(*) FROM live_channels
           WHERE playlistId = :playlistId AND (:categoryId IS NULL OR categoryId = :categoryId)
             AND (categoryId IS NULL OR categoryId NOT IN (:hidden))
             AND num < :num""",
    )
    suspend fun positionOfNum(playlistId: Long, categoryId: Long?, num: Int, hidden: Set<Long>): Int

    /** Total channel count for one playlist (sync stats and empty-state checks). */
    @Query("SELECT COUNT(*) FROM live_channels WHERE playlistId = :playlistId")
    suspend fun channelCount(playlistId: Long): Int

    // Home-hub rails: bounded, non-paged lists (a rail shows at most a dozen
    // entries, so windowed paging would be overhead).
    @Query(
        """SELECT c.* FROM live_channels c
           JOIN recent_views r ON r.playlistId = c.playlistId AND r.mediaType = 'live' AND r.remoteId = c.streamId
           WHERE c.playlistId = :playlistId AND (c.categoryId IS NULL OR c.categoryId NOT IN (:hidden))
           ORDER BY r.viewedAt DESC, c.id LIMIT :limit""",
    )
    fun recentChannelsRail(playlistId: Long, limit: Int, hidden: Set<Long>): Flow<List<LiveChannel>>

    /** Bookmarked channels for the Home rail, newest bookmark first. */
    @Query(
        """SELECT c.* FROM live_channels c
           JOIN bookmarks b ON b.playlistId = c.playlistId AND b.mediaType = 'live' AND b.remoteId = c.streamId
           WHERE c.playlistId = :playlistId AND (c.categoryId IS NULL OR c.categoryId NOT IN (:hidden))
           ORDER BY b.createdAt DESC, c.id LIMIT :limit""",
    )
    fun bookmarkedChannelsRail(playlistId: Long, limit: Int, hidden: Set<Long>): Flow<List<LiveChannel>>

    /** Distinct guide keys of one playlist; guide installs skip programmes for channels not in this set. */
    @Query(
        """SELECT DISTINCT epgChannelId FROM live_channels
           WHERE playlistId = :playlistId AND epgChannelId IS NOT NULL""",
    )
    suspend fun epgChannelKeys(playlistId: Long): List<String>

    /** Bulk-inserts categories, returning generated row ids so sync can link channels to them. */
    @Insert
    suspend fun insertCategories(items: List<LiveCategory>): List<Long>

    /** Bulk-inserts one batch of channels during sync. */
    @Insert
    suspend fun insertChannels(items: List<LiveChannel>)

    /** Clears a playlist's live categories ahead of a fresh sync. */
    @Query("DELETE FROM live_categories WHERE playlistId = :playlistId")
    suspend fun wipeCategories(playlistId: Long)

    /** Clears a playlist's channels ahead of a fresh sync; FTS rows follow via Room's content-sync triggers. */
    @Query("DELETE FROM live_channels WHERE playlistId = :playlistId")
    suspend fun wipeChannels(playlistId: Long)
}

/** VOD catalogue: category lists, paged movie windows, FTS search and lazily fetched details. */
@Dao
interface MovieDao {
    /** VOD categories of one playlist in the provider's sort order. */
    @Query("SELECT * FROM vod_categories WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun categories(playlistId: Long): Flow<List<VodCategory>>

    /** Paged movies of one category, alphabetical; a locked category pages empty. */
    @Query(
        """SELECT * FROM movies WHERE playlistId = :playlistId AND categoryId = :categoryId
           AND categoryId NOT IN (:hidden) ORDER BY name, id""",
    )
    fun moviesByCategory(playlistId: Long, categoryId: Long, hidden: Set<Long>): PagingSource<Int, Movie>

    /** Paged movies across every category ("All"), alphabetical. */
    @Query(
        """SELECT * FROM movies WHERE playlistId = :playlistId
           AND (categoryId IS NULL OR categoryId NOT IN (:hidden)) ORDER BY name, id""",
    )
    fun allMovies(playlistId: Long, hidden: Set<Long>): PagingSource<Int, Movie>

    /** Paged bookmarked movies, newest bookmark first; joined on the stable streamId so re-syncs keep them. */
    @Query(
        """SELECT m.* FROM movies m
           JOIN bookmarks b ON b.playlistId = m.playlistId AND b.mediaType = 'movie' AND b.remoteId = m.streamId
           WHERE m.playlistId = :playlistId AND (m.categoryId IS NULL OR m.categoryId NOT IN (:hidden))
           ORDER BY b.createdAt DESC, m.id""",
    )
    fun bookmarkedMovies(playlistId: Long, hidden: Set<Long>): PagingSource<Int, Movie>

    /** Paged recently-watched movies, most recently viewed first. */
    @Query(
        """SELECT m.* FROM movies m
           JOIN recent_views r ON r.playlistId = m.playlistId AND r.mediaType = 'movie' AND r.remoteId = m.streamId
           WHERE m.playlistId = :playlistId AND (m.categoryId IS NULL OR m.categoryId NOT IN (:hidden))
           ORDER BY r.viewedAt DESC, m.id""",
    )
    fun recentMovies(playlistId: Long, hidden: Set<Long>): PagingSource<Int, Movie>

    /** Paged FTS search over movie names, alphabetical. ftsQuery is a prefix-token MATCH expression, never LIKE. */
    @Query(
        """SELECT m.* FROM movies m
           JOIN movie_fts ON m.id = movie_fts.rowid
           WHERE movie_fts MATCH :ftsQuery AND m.playlistId = :playlistId
             AND (m.categoryId IS NULL OR m.categoryId NOT IN (:hidden))
           ORDER BY m.name, m.id""",
    )
    fun search(playlistId: Long, ftsQuery: String, hidden: Set<Long>): PagingSource<Int, Movie>

    /** Loads one movie by local row id. */
    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun byId(id: Long): Movie?

    /** Emits the movie whenever its row changes, so the details screen refreshes after [updateDetails]. */
    @Query("SELECT * FROM movies WHERE id = :id")
    fun observeById(id: Long): Flow<Movie?>

    /** Merges lazily fetched get_vod_info details into the row; coalesce keeps the list-sync year/rating when the info call omits them. */
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

    /** Bulk-inserts VOD categories, returning generated row ids so sync can link movies to them. */
    @Insert
    suspend fun insertCategories(items: List<VodCategory>): List<Long>

    /** Bulk-inserts one batch of movies during sync. */
    @Insert
    suspend fun insertMovies(items: List<Movie>)

    /** Clears a playlist's VOD categories ahead of a fresh sync. */
    @Query("DELETE FROM vod_categories WHERE playlistId = :playlistId")
    suspend fun wipeCategories(playlistId: Long)

    /** Clears a playlist's movies ahead of a fresh sync; FTS rows follow via triggers. */
    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun wipeMovies(playlistId: Long)
}

/** Series catalogue: category lists, paged series windows, FTS search and lazily fetched details. */
@Dao
interface SeriesDao {
    /** Series categories of one playlist in the provider's sort order. */
    @Query("SELECT * FROM series_categories WHERE playlistId = :playlistId ORDER BY sortOrder")
    fun categories(playlistId: Long): Flow<List<SeriesCategory>>

    /** Paged series of one category, alphabetical; a locked category pages empty. */
    @Query(
        """SELECT * FROM series WHERE playlistId = :playlistId AND categoryId = :categoryId
           AND categoryId NOT IN (:hidden) ORDER BY name, id""",
    )
    fun seriesByCategory(playlistId: Long, categoryId: Long, hidden: Set<Long>): PagingSource<Int, Series>

    /** Paged series across every category ("All"), alphabetical. */
    @Query(
        """SELECT * FROM series WHERE playlistId = :playlistId
           AND (categoryId IS NULL OR categoryId NOT IN (:hidden)) ORDER BY name, id""",
    )
    fun allSeries(playlistId: Long, hidden: Set<Long>): PagingSource<Int, Series>

    /** Paged bookmarked series, newest bookmark first; joined on the stable seriesId so re-syncs keep them. */
    @Query(
        """SELECT s.* FROM series s
           JOIN bookmarks b ON b.playlistId = s.playlistId AND b.mediaType = 'series' AND b.remoteId = s.seriesId
           WHERE s.playlistId = :playlistId AND (s.categoryId IS NULL OR s.categoryId NOT IN (:hidden))
           ORDER BY b.createdAt DESC, s.id""",
    )
    fun bookmarkedSeries(playlistId: Long, hidden: Set<Long>): PagingSource<Int, Series>

    /** Paged recently-watched series, most recently viewed first. */
    @Query(
        """SELECT s.* FROM series s
           JOIN recent_views r ON r.playlistId = s.playlistId AND r.mediaType = 'series' AND r.remoteId = s.seriesId
           WHERE s.playlistId = :playlistId AND (s.categoryId IS NULL OR s.categoryId NOT IN (:hidden))
           ORDER BY r.viewedAt DESC, s.id""",
    )
    fun recentSeries(playlistId: Long, hidden: Set<Long>): PagingSource<Int, Series>

    /** Paged FTS search over series names, alphabetical. ftsQuery is a prefix-token MATCH expression, never LIKE. */
    @Query(
        """SELECT s.* FROM series s
           JOIN series_fts ON s.id = series_fts.rowid
           WHERE series_fts MATCH :ftsQuery AND s.playlistId = :playlistId
             AND (s.categoryId IS NULL OR s.categoryId NOT IN (:hidden))
           ORDER BY s.name, s.id""",
    )
    fun search(playlistId: Long, ftsQuery: String, hidden: Set<Long>): PagingSource<Int, Series>

    /** Loads one series by local row id. */
    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun byId(id: Long): Series?

    /** Emits the series whenever its row changes, so the details screen refreshes after [updateDetails]. */
    @Query("SELECT * FROM series WHERE id = :id")
    fun observeById(id: Long): Flow<Series?>

    /** Merges lazily fetched get_series_info details; coalesce on every column so a partial response never erases list-sync data. */
    @Query(
        """UPDATE series SET plot = coalesce(:plot, plot), backdropUrl = coalesce(:backdropUrl, backdropUrl),
           rating = coalesce(:rating, rating), year = coalesce(:year, year)
           WHERE id = :id""",
    )
    suspend fun updateDetails(id: Long, plot: String?, backdropUrl: String?, rating: Double?, year: String?)

    /** Bulk-inserts series categories, returning generated row ids so sync can link series to them. */
    @Insert
    suspend fun insertCategories(items: List<SeriesCategory>): List<Long>

    /** Bulk-inserts one batch of series during sync. */
    @Insert
    suspend fun insertSeries(items: List<Series>)

    /** Clears a playlist's series categories ahead of a fresh sync. */
    @Query("DELETE FROM series_categories WHERE playlistId = :playlistId")
    suspend fun wipeCategories(playlistId: Long)

    /** Clears a playlist's series ahead of a fresh sync; FTS rows follow via triggers. */
    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun wipeSeries(playlistId: Long)
}

/** Episodes cached per series, filled lazily from get_series_info when a details screen opens. */
@Dao
interface EpisodeDao {
    /** Episodes of one series ordered by season then episode number. */
    @Query("SELECT * FROM episodes WHERE seriesLocalId = :seriesLocalId ORDER BY season, episodeNum")
    fun episodes(seriesLocalId: Long): Flow<List<Episode>>

    /** Loads one episode by local row id. */
    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun byId(id: Long): Episode?

    /** Bulk-inserts the episode list of one series after a get_series_info fetch. */
    @Insert
    suspend fun insertAll(items: List<Episode>)

    /** Drops a series' cached episodes so a fresh get_series_info result can replace them. */
    @Query("DELETE FROM episodes WHERE seriesLocalId = :seriesLocalId")
    suspend fun wipeForSeries(seriesLocalId: Long)
}

/** User bookmarks, keyed by stable provider ids so they survive catalogue re-syncs. */
@Dao
interface BookmarkDao {
    /** Emits the bookmarked remote ids for one media type; list UIs mark badges from this set without per-row joins. */
    @Query("SELECT remoteId FROM bookmarks WHERE playlistId = :playlistId AND mediaType = :mediaType")
    fun observeIds(playlistId: Long, mediaType: String): Flow<List<Long>>

    /** Whether one item is bookmarked, without loading the row. */
    @Query(
        """SELECT EXISTS(SELECT 1 FROM bookmarks
           WHERE playlistId = :playlistId AND mediaType = :mediaType AND remoteId = :remoteId)""",
    )
    suspend fun exists(playlistId: Long, mediaType: String, remoteId: Long): Boolean

    /** Adds a bookmark, or refreshes createdAt when it already exists. */
    @Upsert
    suspend fun upsert(bookmark: Bookmark)

    /** Removes one bookmark by its (playlist, type, remote id) key. */
    @Query("DELETE FROM bookmarks WHERE playlistId = :playlistId AND mediaType = :mediaType AND remoteId = :remoteId")
    suspend fun delete(playlistId: Long, mediaType: String, remoteId: Long)

    /** Removes every bookmark of one playlist (used when the playlist is deleted or reset). */
    @Query("DELETE FROM bookmarks WHERE playlistId = :playlistId")
    suspend fun wipeForPlaylist(playlistId: Long)
}

/** Guide programmes: windowed now/next lookups plus transactional per-playlist replacement. */
@Dao
interface EpgDao {
    // Now/next feed for one channel: the two earliest programmes still running or
    // upcoming, start-ordered. EpgNowNextPolicy decides whether the first is airing.
    @Query(
        """SELECT * FROM epg_programmes
           WHERE playlistId = :playlistId AND epgChannelId = :epgChannelId AND endMs > :nowMs
           ORDER BY startMs LIMIT 2""",
    )
    fun observeUpcoming(playlistId: Long, epgChannelId: String, nowMs: Long): Flow<List<EpgProgramme>>

    // Blocking bulk insert: guide installs stream SAX-parser batches inside one
    // transaction, and SAX callbacks cannot suspend.
    @Insert
    fun insertAllBlocking(items: List<EpgProgramme>)

    /** Clears a playlist's programmes ahead of a fresh guide install. */
    @Query("DELETE FROM epg_programmes WHERE playlistId = :playlistId")
    suspend fun wipeForPlaylist(playlistId: Long)

    /** Programme count for one playlist (sync summary and diagnostics). */
    @Query("SELECT COUNT(*) FROM epg_programmes WHERE playlistId = :playlistId")
    suspend fun countForPlaylist(playlistId: Long): Int
}

/** "Recently viewed" history rows, one per (playlist, type, remote id). */
@Dao
interface RecentViewDao {
    /** Records a playback; re-watching updates viewedAt so the item moves to the front of the row. */
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

    /** Clears one playlist's viewing history. */
    @Query("DELETE FROM recent_views WHERE playlistId = :playlistId")
    suspend fun wipeForPlaylist(playlistId: Long)
}

/** Resume positions, keyed by stable provider ids and resolved back to local rows via joins. */
@Dao
interface WatchProgressDao {
    /** Resume position for one movie, resolved through the local row id -> streamId join. */
    @Query(
        """SELECT wp.* FROM watch_progress wp
           INNER JOIN movies m ON wp.playlistId = m.playlistId AND wp.remoteId = m.streamId
           WHERE wp.mediaType = 'movie' AND m.id = :movieId LIMIT 1""",
    )
    suspend fun getForMovie(movieId: Long): WatchProgress?

    /** Reactive variant of [getForMovie]; keeps the Resume button live while playback saves progress. */
    @Query(
        """SELECT wp.* FROM watch_progress wp
           INNER JOIN movies m ON wp.playlistId = m.playlistId AND wp.remoteId = m.streamId
           WHERE wp.mediaType = 'movie' AND m.id = :movieId LIMIT 1""",
    )
    fun observeForMovie(movieId: Long): Flow<WatchProgress?>

    /** Resume position for one episode, resolved through the local row id -> remoteEpisodeId join. */
    @Query(
        """SELECT wp.* FROM watch_progress wp
           INNER JOIN episodes e ON wp.playlistId = e.playlistId AND wp.remoteId = e.remoteEpisodeId
           WHERE wp.mediaType = 'episode' AND e.id = :episodeId LIMIT 1""",
    )
    suspend fun getForEpisode(episodeId: Long): WatchProgress?

    /** Reactive variant of [getForEpisode]. */
    @Query(
        """SELECT wp.* FROM watch_progress wp
           INNER JOIN episodes e ON wp.playlistId = e.playlistId AND wp.remoteId = e.remoteEpisodeId
           WHERE wp.mediaType = 'episode' AND e.id = :episodeId LIMIT 1""",
    )
    fun observeForEpisode(episodeId: Long): Flow<WatchProgress?>

    /** All episode progress for one series, for progress indicators across the episode list. */
    @Query(
        """SELECT wp.* FROM watch_progress wp
           INNER JOIN episodes e ON wp.playlistId = e.playlistId AND wp.remoteId = e.remoteEpisodeId
           WHERE wp.mediaType = 'episode' AND e.seriesLocalId = :seriesLocalId""",
    )
    fun forSeries(seriesLocalId: Long): Flow<List<WatchProgress>>

    /** Saves or updates a resume position; one row per (playlist, type, remote id). */
    @Upsert
    suspend fun upsert(progress: WatchProgress)

    /** Clears one playlist's watch progress. */
    @Query("DELETE FROM watch_progress WHERE playlistId = :playlistId")
    suspend fun wipeForPlaylist(playlistId: Long)
}
