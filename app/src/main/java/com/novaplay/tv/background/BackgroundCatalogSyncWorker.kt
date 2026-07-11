package com.novaplay.tv.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.novaplay.tv.data.repo.ActivationRepository
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.data.repo.SyncTrigger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Manual dependency lookup for [BackgroundCatalogSyncWorker]: WorkManager constructs
 * workers reflectively outside Hilt's graph, so repositories are fetched per run
 * through this entry point instead of constructor injection.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackgroundSyncEntryPoint {
    fun activationRepository(): ActivationRepository
    fun contentRepository(): ContentRepository
    fun syncRepository(): SyncRepository
}

/**
 * Headless periodic refresh of managed access and the active playlist's catalogue.
 * Scheduled by [BackgroundSyncScheduler]; all errors stay in diagnostics, never the UI.
 */
class BackgroundCatalogSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    /**
     * Refreshes activation state (best effort) then syncs the active playlist, if any.
     * Retries up to [MAX_RETRIES] with backoff; after that it still reports success so
     * the periodic chain stays scheduled, with the outcome string recorded for diagnostics.
     */
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            BackgroundSyncEntryPoint::class.java,
        )

        // Managed assignments and service policies are refreshed opportunistically.
        // A portal outage must not block personal playlists or erase existing access.
        runCatching { dependencies.activationRepository().checkAndAttach() }

        val playlist = dependencies.contentRepository().getActivePlaylist()
            ?: return Result.success(workDataOf(KEY_OUTCOME to "no-active-playlist"))

        val result = dependencies.syncRepository().sync(
            playlist = playlist,
            trigger = SyncTrigger.BACKGROUND,
        )
        if (result.isSuccess) {
            return Result.success(workDataOf(KEY_OUTCOME to "synchronized"))
        }

        return if (runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            // Periodic work must remain scheduled for the next interval. Returning
            // success after bounded retries records the failure in diagnostics while
            // avoiding a permanently failed periodic chain.
            Result.success(workDataOf(KEY_OUTCOME to "failed-after-retries"))
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
        const val KEY_OUTCOME = "outcome"
    }
}
