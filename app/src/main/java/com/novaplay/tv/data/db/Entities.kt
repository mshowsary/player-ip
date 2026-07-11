package com.novaplay.tv.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["portalId"], unique = true)],
)
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val portalId: Long,
    val name: String,
    val type: String, // TYPE_XTREAM | TYPE_M3U
    val server: String? = null,
    val username: String? = null,
    val password: String? = null,
    val url: String? = null,
    val isActive: Boolean = false,
    val expiryEpochSec: Long? = null,
    val maxConnections: Int? = null,
    val lastSyncEpochMs: Long = 0,
) {
    companion object {
        const val TYPE_XTREAM = "xtream"
        const val TYPE_M3U = "m3u"
    }
}

@Entity(
    tableName = "live_categories",
    indices = [Index("playlistId")],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LiveCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val remoteId: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "vod_categories",
    indices = [Index("playlistId")],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VodCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val remoteId: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "series_categories",
    indices = [Index("playlistId")],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SeriesCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val remoteId: String,
    val name: String,
    val sortOrder: Int,
)

// Browse paths are (playlistId, categoryId, num) and (playlistId, num) — both indexed
// so paged windows never scan. categoryId is a local row id without an FK: categories
// are wiped and re-inserted on every sync and must not cascade into channels mid-sync.
@Entity(
    tableName = "live_channels",
    indices = [
        Index(value = ["playlistId", "categoryId", "num"]),
        Index(value = ["playlistId", "num"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LiveChannel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val categoryId: Long?,
    val streamId: Long,
    val num: Int,
    val name: String,
    val logoUrl: String? = null,
    // Direct URL for M3U playlists; Xtream channels build URLs from credentials.
    val urlOverride: String? = null,
)

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["playlistId", "categoryId", "name"]),
        Index(value = ["playlistId", "name"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Movie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val categoryId: Long?,
    val streamId: Long,
    val name: String,
    val posterUrl: String? = null,
    val rating: Double? = null,
    val year: String? = null,
    val containerExtension: String? = null,
    // Filled lazily from get_vod_info when the details screen opens.
    val plot: String? = null,
    val genre: String? = null,
    val durationSecs: Int? = null,
    val backdropUrl: String? = null,
)

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["playlistId", "categoryId", "name"]),
        Index(value = ["playlistId", "name"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Series(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val categoryId: Long?,
    val seriesId: Long,
    val name: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val plot: String? = null,
    val rating: Double? = null,
    val year: String? = null,
)

// Episodes are fetched lazily per series (get_series_info) when the details
// screen opens — never during bulk sync.
@Entity(
    tableName = "episodes",
    indices = [Index(value = ["seriesLocalId", "season", "episodeNum"])],
    foreignKeys = [
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["seriesLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val seriesLocalId: Long,
    val remoteEpisodeId: Long,
    val season: Int,
    val episodeNum: Int,
    val title: String,
    val containerExtension: String? = null,
    val durationSecs: Int? = null,
    val thumbnailUrl: String? = null,
)

@Entity(tableName = "watch_progress", primaryKeys = ["mediaType", "mediaId"])
data class WatchProgress(
    val mediaType: String, // MEDIA_MOVIE | MEDIA_EPISODE
    val mediaId: Long,     // local row id in movies/episodes
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    companion object {
        const val MEDIA_MOVIE = "movie"
        const val MEDIA_EPISODE = "episode"
    }
}

// User bookmarks, keyed by the provider's stable stream/series id — local row
// ids are regenerated on every sync and must not be referenced here.
@Entity(tableName = "bookmarks", primaryKeys = ["playlistId", "mediaType", "remoteId"])
data class Bookmark(
    val playlistId: Long,
    val mediaType: String, // MEDIA_LIVE | MEDIA_MOVIE | MEDIA_SERIES
    val remoteId: Long,    // streamId for live/movies, seriesId for series
    val createdAt: Long,
) {
    companion object {
        const val MEDIA_LIVE = "live"
        const val MEDIA_MOVIE = "movie"
        const val MEDIA_SERIES = "series"
    }
}

// "Recently Viewed": upserted every time something is played, trimmed to a
// fixed count per (playlist, type). Same stable keying as bookmarks.
@Entity(tableName = "recent_views", primaryKeys = ["playlistId", "mediaType", "remoteId"])
data class RecentView(
    val playlistId: Long,
    val mediaType: String,
    val remoteId: Long,
    val viewedAt: Long,
)

// External-content FTS4: names are indexed, not stored twice. unicode61 gives
// case- and accent-insensitive matching ("tele" finds "Télé"). Room generates
// the content-sync triggers automatically.
@Fts4(contentEntity = LiveChannel::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "live_channel_fts")
data class LiveChannelFts(val name: String)

@Fts4(contentEntity = Movie::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "movie_fts")
data class MovieFts(val name: String)

@Fts4(contentEntity = Series::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "series_fts")
data class SeriesFts(val name: String)
