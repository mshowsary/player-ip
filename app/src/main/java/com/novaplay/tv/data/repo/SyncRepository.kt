package com.novaplay.tv.data.repo

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.novaplay.tv.core.SafeErrorMessage
import com.novaplay.tv.core.StableContentId
import com.novaplay.tv.data.db.LiveCategory
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.db.Movie
import com.novaplay.tv.data.db.NovaDatabase
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.db.Series
import com.novaplay.tv.data.db.SeriesCategory
import com.novaplay.tv.data.db.VodCategory
import com.novaplay.tv.data.epg.EpgChannelKey
import com.novaplay.tv.data.m3u.M3uParser
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.LastSyncSummary
import com.novaplay.tv.data.remote.XtreamCategoryDto
import com.novaplay.tv.data.remote.XtreamClient
import com.novaplay.tv.data.security.PlaylistSecrets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Observable sync state for the UI; Failed carries a sanitized, user-safe message. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Syncing(val step: String) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/** What initiated a sync; the label is recorded in the last-sync summary for diagnostics. */
enum class SyncTrigger(val label: String) {
    FOREGROUND("Manual"),
    STARTUP("Startup"),
    BACKGROUND("Background"),
}

// Per-type row counts for one playlist, recorded in the sync summary.
private data class CatalogCounts(
    val live: Int,
    val movies: Int,
    val series: Int,
)

/**
 * Replaces the local catalogue from an Xtream or M3U provider. Downloads are
 * staged to bounded cache files, then swapped into Room in local-only
 * transactions so browsing keeps working mid-sync. Sync never crashes the app:
 * every error is captured and surfaced via [status] and the returned Result.
 */
@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NovaDatabase,
    private val xtream: XtreamClient,
    private val m3uParser: M3uParser,
    private val playlistSecrets: PlaylistSecrets,
    private val preferences: AppPreferences,
    private val epgRepository: EpgRepository,
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val syncMutex = Mutex()

    /** Runs a startup-triggered sync only when the last one is older than [maxAgeMs] (12 h default). */
    suspend fun syncIfStale(playlist: Playlist, maxAgeMs: Long = STALE_AFTER_MS) {
        if (System.currentTimeMillis() - playlist.lastSyncEpochMs > maxAgeMs) {
            sync(playlist, SyncTrigger.STARTUP)
        }
    }

    /**
     * Full catalogue sync, serialized by a mutex so concurrent triggers cannot
     * interleave. Never throws: failures are logged with sanitized messages
     * (never provider URLs, which can embed credentials), recorded in the
     * last-sync summary and emitted as [SyncStatus.Failed].
     */
    suspend fun sync(
        playlist: Playlist,
        trigger: SyncTrigger = SyncTrigger.FOREGROUND,
    ): Result<Unit> = syncMutex.withLock {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            when (playlist.type) {
                Playlist.TYPE_XTREAM -> syncXtream(playlist)
                Playlist.TYPE_M3U -> syncM3u(playlist)
                else -> error("Unknown playlist type: ${playlist.type}")
            }
            val epgProgrammes = refreshGuide(playlist)
            optimizeDatabase()
            val completedAt = System.currentTimeMillis()
            db.playlistDao().updateLastSync(playlist.id, completedAt)
            val counts = catalogCounts(playlist.id)
            preferences.recordSyncSummary(
                LastSyncSummary(
                    completedAtEpochMs = completedAt,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    successful = true,
                    trigger = trigger.label,
                    playlistType = playlist.type,
                    liveChannels = counts.live,
                    movies = counts.movies,
                    series = counts.series,
                    epgProgrammes = epgProgrammes,
                ),
            )
            _status.value = SyncStatus.Idle
            Result.success(Unit)
        } catch (error: Throwable) {
            val message = SafeErrorMessage.from(error, "Synchronization failed")
            // Do not attach the throwable: provider URLs can contain credentials.
            Log.w(TAG, "Sync failed for playlist ${playlist.id}: ${error::class.java.simpleName}: $message")
            val counts = runCatching { catalogCounts(playlist.id) }.getOrDefault(CatalogCounts(0, 0, 0))
            val epgCount = runCatching { db.epgDao().countForPlaylist(playlist.id) }.getOrDefault(0)
            preferences.recordSyncSummary(
                LastSyncSummary(
                    completedAtEpochMs = System.currentTimeMillis(),
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    successful = false,
                    trigger = trigger.label,
                    playlistType = playlist.type,
                    liveChannels = counts.live,
                    movies = counts.movies,
                    series = counts.series,
                    epgProgrammes = epgCount,
                    error = message,
                ),
            )
            _status.value = SyncStatus.Failed(message)
            Result.failure(IllegalStateException(message))
        }
    }

    // Refreshes account info (best-effort, failures ignored) and then the three
    // catalogue sections in sequence, updating the visible status per step.
    private suspend fun syncXtream(playlist: Playlist) {
        _status.value = SyncStatus.Syncing("Checking account")
        runCatching { xtream.userInfo(playlist) }.getOrNull()?.userInfo?.let { info ->
            db.playlistDao().updateAccountInfo(
                id = playlist.id,
                expiryEpochSec = info.expDate,
                maxConnections = info.maxConnections?.toInt(),
            )
        }

        _status.value = SyncStatus.Syncing("Downloading Live TV")
        syncLive(playlist)

        _status.value = SyncStatus.Syncing("Downloading Movies")
        syncMovies(playlist)

        _status.value = SyncStatus.Syncing("Downloading Series")
        syncSeries(playlist)
    }

    // Network and provider parsing are staged to a bounded cache file first.
    // The old catalogue stays readable until a local-only replacement
    // transaction completes successfully.
    private suspend fun syncLive(playlist: Playlist) {
        val categories = xtream.liveCategories(playlist)
        val snapshot = newSnapshot("live", ".json")
        try {
            xtream.stageLiveStreams(playlist, snapshot)
            _status.value = SyncStatus.Syncing("Installing Live TV")
            db.withTransaction {
                val dao = db.liveDao()
                dao.wipeChannels(playlist.id)
                dao.wipeCategories(playlist.id)
                val categoryIds = insertLiveCategories(playlist.id, categories)

                val buffer = ArrayList<LiveChannel>(CHUNK)
                var fallbackNum = 0
                xtream.consumeStagedLive(snapshot) { streams ->
                    for (dto in streams) {
                        fallbackNum++
                        val name = dto.name ?: continue
                        buffer += LiveChannel(
                            playlistId = playlist.id,
                            categoryId = dto.categoryId?.let(categoryIds::get),
                            streamId = dto.streamId,
                            num = if (dto.num > 0) dto.num else fallbackNum,
                            name = name,
                            logoUrl = dto.streamIcon,
                            epgChannelId = EpgChannelKey.normalize(dto.epgChannelId),
                        )
                        if (buffer.size >= CHUNK) {
                            dao.insertChannels(buffer)
                            buffer.clear()
                        }
                    }
                    if (buffer.isNotEmpty()) dao.insertChannels(buffer)
                }
            }
        } finally {
            snapshot.delete()
        }
    }

    // Same stage-then-replace pattern as syncLive: movies stream from the
    // snapshot into chunked inserts inside a single wipe-and-fill transaction.
    private suspend fun syncMovies(playlist: Playlist) {
        val categories = xtream.vodCategories(playlist)
        val snapshot = newSnapshot("movies", ".json")
        try {
            xtream.stageVodStreams(playlist, snapshot)
            _status.value = SyncStatus.Syncing("Installing Movies")
            db.withTransaction {
                val dao = db.movieDao()
                dao.wipeMovies(playlist.id)
                dao.wipeCategories(playlist.id)
                val categoryIds = dao.insertCategories(
                    categories.mapIndexedNotNull { index, dto ->
                        dto.toEntityOrNull { id, name ->
                            VodCategory(playlistId = playlist.id, remoteId = id, name = name, sortOrder = index)
                        }
                    },
                ).let { ids -> categories.mapNotNull { it.categoryId }.zip(ids).toMap() }

                val buffer = ArrayList<Movie>(CHUNK)
                xtream.consumeStagedVod(snapshot) { streams ->
                    for (dto in streams) {
                        val name = dto.name ?: continue
                        buffer += Movie(
                            playlistId = playlist.id,
                            categoryId = dto.categoryId?.let(categoryIds::get),
                            streamId = dto.streamId,
                            name = name,
                            posterUrl = dto.streamIcon,
                            rating = dto.rating,
                            year = dto.year,
                            containerExtension = dto.containerExtension,
                        )
                        if (buffer.size >= CHUNK) {
                            dao.insertMovies(buffer)
                            buffer.clear()
                        }
                    }
                    if (buffer.isNotEmpty()) dao.insertMovies(buffer)
                }
            }
        } finally {
            snapshot.delete()
        }
    }

    // Same stage-then-replace pattern for series; episodes are fetched later,
    // on demand, from the details screen rather than during sync.
    private suspend fun syncSeries(playlist: Playlist) {
        val categories = xtream.seriesCategories(playlist)
        val snapshot = newSnapshot("series", ".json")
        try {
            xtream.stageSeries(playlist, snapshot)
            _status.value = SyncStatus.Syncing("Installing Series")
            db.withTransaction {
                val dao = db.seriesDao()
                dao.wipeSeries(playlist.id)
                dao.wipeCategories(playlist.id)
                val categoryIds = dao.insertCategories(
                    categories.mapIndexedNotNull { index, dto ->
                        dto.toEntityOrNull { id, name ->
                            SeriesCategory(playlistId = playlist.id, remoteId = id, name = name, sortOrder = index)
                        }
                    },
                ).let { ids -> categories.mapNotNull { it.categoryId }.zip(ids).toMap() }

                val buffer = ArrayList<Series>(CHUNK)
                xtream.consumeStagedSeries(snapshot) { streams ->
                    for (dto in streams) {
                        val name = dto.name ?: continue
                        buffer += Series(
                            playlistId = playlist.id,
                            categoryId = dto.categoryId?.let(categoryIds::get),
                            seriesId = dto.seriesId,
                            name = name,
                            posterUrl = dto.cover,
                            backdropUrl = dto.backdropPath,
                            plot = dto.plot,
                            rating = dto.rating,
                            year = dto.releaseDate?.take(4),
                        )
                        if (buffer.size >= CHUNK) {
                            dao.insertSeries(buffer)
                            buffer.clear()
                        }
                    }
                    if (buffer.isNotEmpty()) dao.insertSeries(buffer)
                }
            }
        } finally {
            snapshot.delete()
        }
    }

    // Guide refresh is best-effort: a provider without EPG (or a temporary guide
    // failure) must never fail the catalogue sync. On failure the previous guide
    // stays readable and the summary reports its (still installed) row count.
    private suspend fun refreshGuide(playlist: Playlist): Int {
        _status.value = SyncStatus.Syncing("Updating TV guide")
        // M3U syncs may have just discovered/updated the guide URL — reload the row.
        val fresh = db.playlistDao().getById(playlist.id) ?: playlist
        return runCatching { epgRepository.refresh(fresh, System.currentTimeMillis()) }
            .onFailure { error ->
                // Never log the error itself: guide URLs can embed credentials.
                Log.w(TAG, "Guide refresh failed for playlist ${playlist.id}: ${error::class.java.simpleName}")
            }
            .getOrElse { runCatching { db.epgDao().countForPlaylist(playlist.id) }.getOrDefault(0) }
    }

    // Inserts live categories and returns remote id -> local row id, used to
    // resolve each channel's category while streaming the snapshot.
    private suspend fun insertLiveCategories(
        playlistId: Long,
        categories: List<XtreamCategoryDto>,
    ): Map<String, Long> {
        val entities = categories.mapIndexedNotNull { index, dto ->
            dto.toEntityOrNull { id, name ->
                LiveCategory(playlistId = playlistId, remoteId = id, name = name, sortOrder = index)
            }
        }
        val ids = db.liveDao().insertCategories(entities)
        return entities.map { it.remoteId }.zip(ids).toMap()
    }

    // Skips categories without an id; a missing name gets a readable fallback.
    private inline fun <T> XtreamCategoryDto.toEntityOrNull(build: (String, String) -> T): T? {
        val id = categoryId ?: return null
        return build(id, categoryName ?: "Category $id")
    }

    // M3U sources map onto the live tables only. The playlist is snapshotted to
    // disk first, then parsed into channels with categories created lazily per
    // group; stream ids are derived so bookmarks survive re-syncs.
    private suspend fun syncM3u(playlist: Playlist) {
        val opened = playlistSecrets.open(playlist)
        val url = opened.url ?: error("M3U playlist has no URL")
        val snapshot = newSnapshot("m3u", ".m3u")
        try {
            _status.value = SyncStatus.Syncing("Downloading playlist")
            m3uParser.snapshot(url, snapshot)
            _status.value = SyncStatus.Syncing("Installing playlist")
            var discoveredGuideUrl: String? = null
            db.withTransaction {
                val dao = db.liveDao()
                dao.wipeChannels(playlist.id)
                dao.wipeCategories(playlist.id)

                val categoryIds = HashMap<String, Long>()
                val buffer = ArrayList<LiveChannel>(CHUNK)
                var num = 0
                m3uParser.parseSnapshot(
                    snapshot,
                    onHeader = { header -> discoveredGuideUrl = header.tvgUrl },
                ) { entry ->
                    num++
                    val categoryId = entry.group?.let { group ->
                        categoryIds.getOrPut(group) {
                            dao.insertCategories(
                                listOf(
                                    LiveCategory(
                                        playlistId = playlist.id,
                                        remoteId = group,
                                        name = group,
                                        sortOrder = categoryIds.size,
                                    ),
                                ),
                            ).first()
                        }
                    }
                    buffer += LiveChannel(
                        playlistId = playlist.id,
                        categoryId = categoryId,
                        streamId = StableContentId.forM3u(entry.tvgId, entry.url),
                        num = num,
                        name = entry.name,
                        logoUrl = entry.logoUrl,
                        urlOverride = entry.url,
                        epgChannelId = EpgChannelKey.normalize(entry.tvgId),
                    )
                    if (buffer.size >= CHUNK) {
                        dao.insertChannels(buffer)
                        buffer.clear()
                    }
                }
                if (buffer.isNotEmpty()) dao.insertChannels(buffer)
            }
            // The guide source lives in the playlist header, so each sync refreshes
            // it (sealed — guide URLs can embed tokens). Cleared when it disappears.
            db.playlistDao().updateEpgUrl(playlist.id, playlistSecrets.sealValue(discoveredGuideUrl))
        } finally {
            snapshot.delete()
        }
    }

    // Creates a staging temp file in the snapshot cache, first purging leftovers
    // older than the TTL (e.g. from a crash mid-sync) so the cache stays bounded.
    private fun newSnapshot(prefix: String, suffix: String): File {
        val directory = File(context.cacheDir, "catalog_snapshots").apply { mkdirs() }
        directory.listFiles()
            ?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - SNAPSHOT_TTL_MS }
            ?.forEach(File::delete)
        return File.createTempFile("$prefix-", suffix, directory)
    }

    // FTS compaction and SQLite maintenance are local-only and run after a
    // successful replacement. PASSIVE checkpointing keeps the WAL bounded
    // without blocking readers that are currently browsing the old/new data.
    private suspend fun optimizeDatabase() = withContext(Dispatchers.IO) {
        val sqlDb = db.openHelper.writableDatabase
        for (table in listOf("live_channel_fts", "movie_fts", "series_fts")) {
            sqlDb.execSQL("INSERT INTO $table($table) VALUES('optimize')")
        }
        sqlDb.query("PRAGMA optimize").close()
        sqlDb.query("PRAGMA wal_checkpoint(PASSIVE)").close()
    }

    // Counts the freshly installed rows on Dispatchers.IO for the sync summary.
    private suspend fun catalogCounts(playlistId: Long): CatalogCounts = withContext(Dispatchers.IO) {
        CatalogCounts(
            live = count("live_channels", playlistId),
            movies = count("movies", playlistId),
            series = count("series", playlistId),
        )
    }

    // Raw COUNT(*) shared across the catalogue tables; blocking, so callers
    // must already be on an IO dispatcher.
    private fun count(table: String, playlistId: Long): Int {
        val query = SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM $table WHERE playlistId = ?",
            arrayOf(playlistId),
        )
        return db.openHelper.readableDatabase.query(query).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private companion object {
        const val TAG = "SyncRepository"
        const val CHUNK = 1000
        const val STALE_AFTER_MS = 12L * 60 * 60 * 1000
        const val SNAPSHOT_TTL_MS = 24L * 60 * 60 * 1000
    }
}
