package com.novaplay.tv

import android.app.ActivityManager
import android.app.Application
import android.os.StrictMode
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.novaplay.tv.background.BackgroundSyncScheduler
import com.novaplay.tv.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point: hosts the Hilt graph, reconciles the background sync
 * schedule at startup, supplies the app-wide Coil image loader, and enables
 * non-fatal leak/network diagnostics in debug builds. WorkManager initializes
 * on demand through [workManagerConfiguration] instead of at process start.
 */
@HiltAndroidApp
class NovaPlayApp : Application(), ImageLoaderFactory, Configuration.Provider {

    /** Default WorkManager configuration, created only when first used. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    @Inject
    lateinit var backgroundSyncScheduler: BackgroundSyncScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    /** Standard startup plus async WorkManager reconciliation, kept off the main thread. */
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableDebugStrictMode()
        // WorkManager reconciliation is not part of the first rendered frame.
        // It runs after Hilt initialization on the process-lifetime scope.
        applicationScope.launch { backgroundSyncScheduler.reconcile() }
    }

    /** Logs development regressions without crashing the user's test build. */
    private fun enableDebugStrictMode() {
        // Disk checks are intentionally omitted because Room/Coil initialize
        // small local resources around startup and would create noisy reports.
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build(),
        )
    }

    // Provider catalogues can contain tens of thousands of logos and posters.
    // Low-memory TV boxes use a smaller memory and disk budget, while stronger
    // phones/tablets retain enough cache for smooth catalogue scrolling.
    override fun newImageLoader(): ImageLoader {
        val activityManager = getSystemService(ActivityManager::class.java)
        val lowRam = activityManager?.isLowRamDevice == true
        val memoryPercent = if (lowRam) 0.07 else 0.12
        val diskBytes = if (lowRam) 80L * 1024 * 1024 else 160L * 1024 * 1024

        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memoryPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(diskBytes)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(!lowRam)
            .build()
    }
}
