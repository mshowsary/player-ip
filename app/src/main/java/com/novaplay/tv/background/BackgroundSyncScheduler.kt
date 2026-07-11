package com.novaplay.tv.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.BackgroundSyncMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Concrete WorkManager cadence derived from a [BackgroundSyncMode]. */
data class BackgroundSyncPlan(
    val intervalHours: Long,
    val flexHours: Long,
    val initialDelayMinutes: Long,
)

/** Pure preference-to-schedule mapping, kept free of WorkManager so it is trivially testable. */
object BackgroundSyncPolicy {
    /**
     * Maps a sync mode to a schedule, or null when syncing is off — callers must cancel
     * existing work on null. Flex is tighter for the 12h cadence; the 45-minute initial
     * delay keeps the first run away from install/first-launch foreground syncs.
     */
    fun plan(mode: BackgroundSyncMode): BackgroundSyncPlan? {
        val interval = mode.intervalHours ?: return null
        return BackgroundSyncPlan(
            intervalHours = interval,
            flexHours = if (interval <= 12L) 2L else 4L,
            initialDelayMinutes = 45L,
        )
    }
}

/** Owns the WorkManager schedule for [BackgroundCatalogSyncWorker]. */
@Singleton
class BackgroundSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
) {
    /**
     * Re-applies the persisted sync mode. Runs at every app start so WorkManager state
     * always matches the stored preference, even after upgrades or cleared app data.
     */
    suspend fun reconcile() {
        apply(preferences.backgroundSyncMode.first())
    }

    /**
     * Enqueues or updates the unique periodic sync, or cancels it when the mode is OFF.
     * Runs only on unmetered networks with healthy battery/storage; the UPDATE policy
     * swaps the spec in place without resetting the next-run time.
     */
    fun apply(mode: BackgroundSyncMode) {
        val workManager = WorkManager.getInstance(context)
        val plan = BackgroundSyncPolicy.plan(mode)
        if (plan == null) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<BackgroundCatalogSyncWorker>(
            plan.intervalHours,
            TimeUnit.HOURS,
            plan.flexHours,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setInitialDelay(plan.initialDelayMinutes, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "novaplay-catalog-refresh"
        const val WORK_TAG = "catalog-refresh"
    }
}
