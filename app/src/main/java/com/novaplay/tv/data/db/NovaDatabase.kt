package com.novaplay.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Single Room database owning every synced catalogue. All large lists live here
 * and the UI only ever reads Paging 3 windows over them — nothing bulk is held
 * in memory. Version bumps must ship a migration in [DatabaseMigrations];
 * exportSchema keeps the JSON schemas around for migration tests.
 */
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
        EpgProgramme::class,
        LiveChannelFts::class,
        MovieFts::class,
        SeriesFts::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class NovaDatabase : RoomDatabase() {
    /** Playlist sources and the active-playlist selection. */
    abstract fun playlistDao(): PlaylistDao
    /** Live categories, paged channel windows, zapping and FTS search. */
    abstract fun liveDao(): LiveDao
    /** VOD categories, paged movie windows, FTS search and detail updates. */
    abstract fun movieDao(): MovieDao
    /** Series categories, paged series windows, FTS search and detail updates. */
    abstract fun seriesDao(): SeriesDao
    /** Per-series cached episodes. */
    abstract fun episodeDao(): EpisodeDao
    /** Resume positions keyed by stable provider ids. */
    abstract fun watchProgressDao(): WatchProgressDao
    /** User bookmarks keyed by stable provider ids. */
    abstract fun bookmarkDao(): BookmarkDao
    /** "Recently viewed" history rows. */
    abstract fun recentViewDao(): RecentViewDao
    /** Guide programmes: windowed now/next lookups and per-playlist replacement. */
    abstract fun epgDao(): EpgDao
}
