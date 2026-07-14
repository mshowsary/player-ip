package com.novaplay.tv.data.update

import com.novaplay.tv.core.LenientLong
import com.novaplay.tv.core.LenientStringOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

/**
 * The update manifest served from a brand's update URL for the sideloaded
 * distribution channel (boxes without a store):
 *
 * ```json
 * {"version_code": 1000002, "version_name": "1.0.1",
 *  "apk_url": "https://updates.example.com/player-1.0.1.apk",
 *  "notes": "What changed"}
 * ```
 */
@Serializable
data class UpdateManifestDto(
    @Serializable(with = LenientLong::class) @SerialName("version_code") val versionCode: Long = 0,
    @Serializable(with = LenientStringOrNull::class) @SerialName("version_name") val versionName: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("apk_url") val apkUrl: String? = null,
    @Serializable(with = LenientStringOrNull::class) val notes: String? = null,
)

/** Outcome of comparing the manifest against the running build. */
sealed interface UpdateDecision {
    /** The running build is current (or newer). */
    data object UpToDate : UpdateDecision

    /** A newer build exists at [apkUrl]. */
    data class Available(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val notes: String?,
    ) : UpdateDecision

    /** The manifest is unusable — fail closed, never a false update prompt. */
    data object Invalid : UpdateDecision
}

/**
 * Pure decision rules for the update channel. Everything fails closed: a
 * malformed manifest, a non-HTTPS download link or a nonsensical version can
 * only ever produce [UpdateDecision.Invalid], never a prompt.
 */
object UpdateCheckPolicy {

    /** Transport rule shared by manifest and APK URLs: HTTPS, or local HTTP in debug. */
    fun isAllowedUrl(raw: String, allowLocalHttp: Boolean): Boolean {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        if (host.isBlank() || uri.userInfo != null) return false
        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> allowLocalHttp && host in LOCAL_HOSTS
            else -> false
        }
    }

    fun evaluate(
        currentVersionCode: Long,
        manifest: UpdateManifestDto?,
        allowLocalHttp: Boolean,
    ): UpdateDecision {
        val dto = manifest ?: return UpdateDecision.Invalid
        val versionName = dto.versionName?.trim().orEmpty()
        val apkUrl = dto.apkUrl?.trim().orEmpty()
        if (dto.versionCode <= 0 || versionName.isEmpty() || apkUrl.isEmpty()) {
            return UpdateDecision.Invalid
        }
        if (!isAllowedUrl(apkUrl, allowLocalHttp)) return UpdateDecision.Invalid
        return if (dto.versionCode > currentVersionCode) {
            UpdateDecision.Available(
                versionCode = dto.versionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                notes = dto.notes?.trim()?.takeIf { it.isNotEmpty() },
            )
        } else {
            UpdateDecision.UpToDate
        }
    }

    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]")
}
