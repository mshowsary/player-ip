package com.novaplay.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Playlist::class,
        LiveCategory::class,
        VodCategory::class,
        SeriesCategory::class,
        LiveChannel::class,
        Movie::class,
        Series::class,
        Episode::class,
        WatchProgress::class,
        Bookmark::class,
        RecentView::class,
        LiveChannelFts::class,
        MovieFts::class,
        SeriesFts::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class NovaDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun liveDao(): LiveDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentViewDao(): RecentViewDao
}
