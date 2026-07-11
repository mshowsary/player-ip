package com.novaplay.tv.data.repo

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.sqlite.db.SimpleSQLiteQuery
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.data.db.NovaDatabase
import com.novaplay.tv.data.prefs.BackgroundSyncMode
import com.novaplay.tv.data.prefs.LastSyncSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Point-in-time device and catalogue health snapshot shown on the support screen. */
data class AppDiagnostics(
    val generatedAtEpochMs: Long = 0L,
    val androidVersion: String = "",
    val deviceModel: String = "",
    val lowRamDevice: Boolean = false,
    val memoryClassMb: Int = 0,
    val databaseBytes: Long = 0L,
    val imageCacheBytes: Long = 0L,
    val snapshotCacheBytes: Long = 0L,
    val freeStorageBytes: Long = 0L,
    val networkLabel: String = "Unknown",
    val activePlaylistType: String = "None",
    val liveChannels: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
)

/**
 * Collects privacy-safe diagnostics for user support. Nothing produced here may
 * contain playlist URLs, credentials, tokens, MAC addresses or device ids —
 * only aggregate counts, sizes and coarse device/network labels.
 */
@Singleton
class AppDiagnosticsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NovaDatabase,
) {
    /**
     * Gathers the current snapshot on Dispatchers.IO: device traits, database
     * and cache footprints, network class, and per-type catalogue counts for
     * the active playlist only.
     */
    suspend fun snapshot(): AppDiagnostics = withContext(Dispatchers.IO) {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val active = db.playlistDao().getActive()
        val playlistId = active?.id

        AppDiagnostics(
            generatedAtEpochMs = System.currentTimeMillis(),
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Android device" },
            lowRamDevice = activityManager?.isLowRamDevice == true,
            memoryClassMb = activityManager?.memoryClass ?: 0,
            databaseBytes = databaseFootprint(),
            imageCacheBytes = directorySize(File(context.cacheDir, "image_cache")),
            snapshotCacheBytes = directorySize(File(context.cacheDir, "catalog_snapshots")),
            freeStorageBytes = context.filesDir.usableSpace.coerceAtLeast(0L),
            networkLabel = networkLabel(),
            activePlaylistType = active?.type?.uppercase(Locale.ROOT) ?: "None",
            liveChannels = playlistId?.let { count("live_channels", it) } ?: 0,
            movies = playlistId?.let { count("movies", it) } ?: 0,
            series = playlistId?.let { count("series", it) } ?: 0,
        )
    }

    /**
     * Renders the snapshot plus sync history as shareable plain text. Keep the
     * output free of identifiers and secrets; the closing line is a promise to
     * the user that must stay true.
     */
    fun supportText(
        diagnostics: AppDiagnostics,
        backgroundMode: BackgroundSyncMode,
        lastSync: LastSyncSummary,
    ): String = buildString {
        appendLine("NovaPlay support diagnostics")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Generated: ${formatDateTime(diagnostics.generatedAtEpochMs)}")
        appendLine("Device: ${diagnostics.deviceModel}")
        appendLine("System: ${diagnostics.androidVersion}")
        appendLine("Low RAM: ${diagnostics.lowRamDevice}")
        appendLine("Memory class: ${diagnostics.memoryClassMb} MB")
        appendLine("Network: ${diagnostics.networkLabel}")
        appendLine("Background refresh: ${backgroundMode.label}")
        appendLine("Active source type: ${diagnostics.activePlaylistType}")
        appendLine(
            "Catalogue: ${diagnostics.liveChannels} live, ${diagnostics.movies} movies, " +
                "${diagnostics.series} series",
        )
        appendLine("Database: ${formatBytes(diagnostics.databaseBytes)}")
        appendLine("Image cache: ${formatBytes(diagnostics.imageCacheBytes)}")
        appendLine("Pending snapshots: ${formatBytes(diagnostics.snapshotCacheBytes)}")
        appendLine("Free storage: ${formatBytes(diagnostics.freeStorageBytes)}")
        if (lastSync.exists) {
            appendLine("Last sync: ${if (lastSync.successful) "success" else "failed"}")
            appendLine("Last sync time: ${formatDateTime(lastSync.completedAtEpochMs)}")
            appendLine("Last sync trigger: ${lastSync.trigger.ifBlank { "unknown" }}")
            appendLine("Last sync duration: ${formatDuration(lastSync.durationMs)}")
            appendLine(
                "Last sync catalogue: ${lastSync.liveChannels} live, ${lastSync.movies} movies, " +
                    "${lastSync.series} series",
            )
            lastSync.error?.let { appendLine("Last sync error: $it") }
        } else {
            appendLine("Last sync: not recorded")
        }
        append("No playlist URLs, usernames, passwords, tokens or device identifiers are included.")
    }

    // Raw COUNT(*) so one helper covers every catalogue table; blocking, so
    // only called from the IO context of snapshot().
    private fun count(table: String, playlistId: Long): Int {
        val query = SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM $table WHERE playlistId = ?",
            arrayOf(playlistId),
        )
        return db.openHelper.readableDatabase.query(query).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    // Includes the -wal/-shm sidecars, which can dwarf the main file mid-sync.
    private fun databaseFootprint(): Long {
        val main = context.getDatabasePath("novaplay.db")
        return listOf(
            main,
            File(main.path + "-wal"),
            File(main.path + "-shm"),
        ).sumOf { if (it.isFile) it.length() else 0L }
    }

    // Recursive on-disk size; missing paths count as zero.
    private fun directorySize(file: File): Long = when {
        !file.exists() -> 0L
        file.isFile -> file.length()
        else -> file.listFiles()?.sumOf(::directorySize) ?: 0L
    }

    // Coarse transport + metered label ("Wi-Fi (unmetered)"); deliberately no
    // SSID or addresses, which would identify the user.
    private fun networkLabel(): String {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "Unknown"
        val network = manager.activeNetwork ?: return "Offline"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "Offline"
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "Offline"
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
        return if (manager.isActiveNetworkMetered) "$transport (metered)" else "$transport (unmetered)"
    }

    companion object {
        /** Human-readable size with one decimal, scaling B through GB. */
        fun formatBytes(bytes: Long): String {
            if (bytes < 1_024L) return "$bytes B"
            val kb = bytes / 1_024.0
            if (kb < 1_024.0) return "%.1f KB".format(Locale.US, kb)
            val mb = kb / 1_024.0
            if (mb < 1_024.0) return "%.1f MB".format(Locale.US, mb)
            return "%.1f GB".format(Locale.US, mb / 1_024.0)
        }

        /** Human-readable duration, switching units at 1 s and 1 min. */
        fun formatDuration(durationMs: Long): String = when {
            durationMs < 1_000L -> "${durationMs} ms"
            durationMs < 60_000L -> "%.1f s".format(Locale.US, durationMs / 1_000.0)
            else -> "%d min %02d s".format(
                Locale.US,
                durationMs / 60_000L,
                (durationMs / 1_000L) % 60L,
            )
        }

        /** Locale-formatted timestamp for display in the support text. */
        fun formatDateTime(epochMs: Long): String =
            SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
}
