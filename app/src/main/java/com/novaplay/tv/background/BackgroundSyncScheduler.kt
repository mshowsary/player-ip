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

data class BackgroundSyncPlan(
    val intervalHours: Long,
    val flexHours: Long,
    val initialDelayMinutes: Long,
)

object BackgroundSyncPolicy {
    fun plan(mode: BackgroundSyncMode): BackgroundSyncPlan? {
        val interval = mode.intervalHours ?: return null
        return BackgroundSyncPlan(
            intervalHours = interval,
            flexHours = if (interval <= 12L) 2L else 4L,
            initialDelayMinutes = 45L,
        )
    }
}

@Singleton
class BackgroundSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
) {
    suspend fun reconcile() {
        apply(preferences.backgroundSyncMode.first())
    }

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
