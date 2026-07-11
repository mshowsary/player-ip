package com.novaplay.tv.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Database migrations kept in one place so release builds never wipe user data. */
object DatabaseMigrations {

    /**
     * v2 -> v3: re-keys watch_progress from local Room row ids to stable provider
     * ids (movie streamId / remoteEpisodeId) so Resume survives catalogue re-syncs.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        // Rebuild-and-copy: SQLite cannot change a composite primary key in place.
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `watch_progress_new` (
                    `playlistId` INTEGER NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `remoteId` INTEGER NOT NULL,
                    `positionMs` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`, `mediaType`, `remoteId`)
                )
                """.trimIndent(),
            )

            // Preserve movie progress by translating the old local Room row id
            // into the provider's stable stream id.
            db.execSQL(
                """
                INSERT OR REPLACE INTO `watch_progress_new`
                    (`playlistId`, `mediaType`, `remoteId`, `positionMs`, `durationMs`, `updatedAt`)
                SELECT m.`playlistId`, 'movie', m.`streamId`,
                       wp.`positionMs`, wp.`durationMs`, wp.`updatedAt`
                FROM `watch_progress` wp
                INNER JOIN `movies` m ON wp.`mediaType` = 'movie' AND wp.`mediaId` = m.`id`
                """.trimIndent(),
            )

            // Episodes use the provider episode id, which survives episode and
            // series rows being refreshed and re-created.
            db.execSQL(
                """
                INSERT OR REPLACE INTO `watch_progress_new`
                    (`playlistId`, `mediaType`, `remoteId`, `positionMs`, `durationMs`, `updatedAt`)
                SELECT e.`playlistId`, 'episode', e.`remoteEpisodeId`,
                       wp.`positionMs`, wp.`durationMs`, wp.`updatedAt`
                FROM `watch_progress` wp
                INNER JOIN `episodes` e ON wp.`mediaType` = 'episode' AND wp.`mediaId` = e.`id`
                """.trimIndent(),
            )

            db.execSQL("DROP TABLE `watch_progress`")
            db.execSQL("ALTER TABLE `watch_progress_new` RENAME TO `watch_progress`")
        }
    }
}
