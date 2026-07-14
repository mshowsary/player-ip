package com.novaplay.tv.data.repo

import com.novaplay.tv.BuildConfig
import com.novaplay.tv.core.SafeErrorMessage
import com.novaplay.tv.data.update.UpdateCheckPolicy
import com.novaplay.tv.data.update.UpdateDecision
import com.novaplay.tv.data.update.UpdateManifestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** One observable outcome of the in-app update check, for the settings panel. */
sealed interface UpdateCheckState {
    /** No update URL is configured for this brand — the panel shows nothing. */
    data object Disabled : UpdateCheckState

    /** Configured but not yet checked this session. */
    data object Idle : UpdateCheckState

    data object Checking : UpdateCheckState

    data object UpToDate : UpdateCheckState

    data class Available(
        val versionName: String,
        val apkUrl: String,
        val notes: String?,
    ) : UpdateCheckState

    /** Sanitized failure — never contains the update URL or provider details. */
    data class Failed(val message: String) : UpdateCheckState
}

/**
 * Sideload-channel update check against the brand's manifest URL. Read-only
 * and side-effect free: it never downloads or installs anything — an
 * available update only surfaces a link the user opens deliberately.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val manifestUrl = BuildConfig.UPDATE_MANIFEST_URL

    /** Whether this brand ships the in-app update channel at all. */
    val enabled: Boolean =
        manifestUrl.isNotBlank() && UpdateCheckPolicy.isAllowedUrl(manifestUrl, BuildConfig.DEBUG)

    /** Fetches and evaluates the manifest; every failure comes back sanitized. */
    suspend fun check(): UpdateCheckState = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext UpdateCheckState.Disabled
        runCatching {
            val body = okHttpClient.newCall(Request.Builder().url(manifestUrl).build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) throw IOException("Update check failed: HTTP ${response.code}")
                    response.peekBody(MAX_MANIFEST_BYTES).string()
                }
            val manifest = json.decodeFromString(UpdateManifestDto.serializer(), body)
            when (
                val decision = UpdateCheckPolicy.evaluate(
                    currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    manifest = manifest,
                    allowLocalHttp = BuildConfig.DEBUG,
                )
            ) {
                UpdateDecision.UpToDate -> UpdateCheckState.UpToDate
                is UpdateDecision.Available -> UpdateCheckState.Available(
                    versionName = decision.versionName,
                    apkUrl = decision.apkUrl,
                    notes = decision.notes,
                )
                UpdateDecision.Invalid ->
                    UpdateCheckState.Failed("The update information could not be read")
            }
        }.getOrElse { error ->
            UpdateCheckState.Failed(SafeErrorMessage.from(error, "Update check failed"))
        }
    }

    private companion object {
        // An update manifest is a handful of fields; anything bigger is wrong.
        const val MAX_MANIFEST_BYTES = 64L * 1024
    }
}
