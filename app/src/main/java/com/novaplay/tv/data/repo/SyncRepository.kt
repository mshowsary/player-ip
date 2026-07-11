package com.novaplay.tv.data.repo

import android.util.Log
import androidx.room.withTransaction
import com.novaplay.tv.core.StableContentId
import com.novaplay.tv.data.db.LiveCategory
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.db.Movie
import com.novaplay.tv.data.db.NovaDatabase
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.db.Series
import com.novaplay.tv.data.db.SeriesCategory
import com.novaplay.tv.data.db.VodCategory
import com.novaplay.tv.data.m3u.M3uParser
import com.novaplay.tv.data.remote.XtreamCategoryDto
import com.novaplay.tv.data.remote.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Syncing(val step: String) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

@Singleton
class SyncRepository @Inject constructor(
    private val db: NovaDatabase,
    private val xtream: XtreamClient,
    private val m3uParser: M3uParser,
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val syncMutex = Mutex()

    suspend fun syncIfStale(playlist: Playlist, maxAgeMs: Long = STALE_AFTER_MS) {
        if (System.currentTimeMillis() - playlist.lastSyncEpochMs > maxAgeMs) {
            sync(playlist)
        }
    }

    suspend fun sync(playlist: Playlist): Result<Unit> = syncMutex.withLock {
        runCatching {
            when (playlist.type) {
                Playlist.TYPE_XTREAM -> syncXtream(playlist)
                Playlist.TYPE_M3U -> syncM3u(playlist)
                else -> error("Unknown playlist type: ${playlist.type}")
            }
            optimizeFts()
            db.playlistDao().updateLastSync(playlist.id, System.currentTimeMillis())
            _status.value = SyncStatus.Idle
        }.onFailure { e ->
            Log.w(TAG, "Sync failed for playlist ${playlist.id}", e)
            _status.value = SyncStatus.Failed(e.message ?: "Sync failed")
        }
    }

    private suspend fun syncXtream(playlist: Playlist) {
        _status.value = SyncStatus.Syncing("Checking account")
        runCatching { xtream.userInfo(playlist) }.getOrNull()?.userInfo?.let { info ->
            db.playlistDao().updateAccountInfo(
                id = playlist.id,
                expiryEpochSec = info.expDate,
                maxConnections = info.maxConnections?.toInt(),
            )
        }

        _status.value = SyncStatus.Syncing("Live TV")
        syncLive(playlist)

        _status.value = SyncStatus.Syncing("Movies")
        syncMovies(playlist)

        _status.value = SyncStatus.Syncing("Series")
        syncSeries(playlist)
    }

    // Each content type is wiped + re-inserted inside one transaction: readers keep
    // the old catalog until the new one lands atomically, and a mid-sync failure
    // rolls back instead of leaving a half-empty list.
    private suspend fun syncLive(playlist: Playlist) {
        val categories = xtream.liveCategories(playlist)
        db.withTransaction {
            val dao = db.liveDao()
            dao.wipeChannels(playlist.id)
            dao.wipeCategories(playlist.id)
            val categoryIds = insertLiveCategories(playlist.id, categories)

            val buffer = ArrayList<LiveChannel>(CHUNK)
            var fallbackNum = 0
            xtream.liveStreams(playlist) { streams ->
                for (dto in streams) {
                    fallbackNum++
                    val name = dto.name ?: continue // malformed entry: skip, never fatal
                    buffer += LiveChannel(
                        playlistId = playlist.id,
                        categoryId = dto.categoryId?.let(categoryIds::get),
                        streamId = dto.streamId,
                        num = if (dto.num > 0) dto.num else fallbackNum,
                        name = name,
                        logoUrl = dto.streamIcon,
                    )
                    if (buffer.size >= CHUNK) {
                        dao.insertChannels(buffer)
                        buffer.clear()
                    }
                }
                if (buffer.isNotEmpty()) dao.insertChannels(buffer)
            }
        }
    }

    private suspend fun syncMovies(playlist: Playlist) {
        val categories = xtream.vodCategories(playlist)
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
            xtream.vodStreams(playlist) { streams ->
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
    }

    private suspend fun syncSeries(playlist: Playlist) {
        val categories = xtream.seriesCategories(playlist)
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
            xtream.series(playlist) { streams ->
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
    }

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

    private inline fun <T> XtreamCategoryDto.toEntityOrNull(build: (String, String) -> T): T? {
        val id = categoryId ?: return null
        return build(id, categoryName ?: "Category $id")
    }

    private suspend fun syncM3u(playlist: Playlist) {
        val url = playlist.url ?: error("M3U playlist has no URL")
        _status.value = SyncStatus.Syncing("Downloading playlist")
        db.withTransaction {
            val dao = db.liveDao()
            dao.wipeChannels(playlist.id)
            dao.wipeCategories(playlist.id)

            val categoryIds = HashMap<String, Long>()
            val buffer = ArrayList<LiveChannel>(CHUNK)
            var num = 0
            m3uParser.parse(url) { entry ->
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
                )
                if (buffer.size >= CHUNK) {
                    dao.insertChannels(buffer)
                    buffer.clear()
                }
            }
            if (buffer.isNotEmpty()) dao.insertChannels(buffer)
        }
    }

    // FTS4 external-content maintenance: fold the incremental index segments
    // produced by a full re-sync back into one for fast MATCH queries.
    private suspend fun optimizeFts() = withContext(Dispatchers.IO) {
        val sqlDb = db.openHelper.writableDatabase
        for (table in listOf("live_channel_fts", "movie_fts", "series_fts")) {
            sqlDb.execSQL("INSERT INTO $table($table) VALUES('optimize')")
        }
    }

    private companion object {
        const val TAG = "SyncRepository"
        const val CHUNK = 1000
        const val STALE_AFTER_MS = 12L * 60 * 60 * 1000
    }
}
