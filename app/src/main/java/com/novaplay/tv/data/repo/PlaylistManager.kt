package com.novaplay.tv.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.novaplay.tv.data.db.NovaDatabase
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.remote.XtreamClient
import com.novaplay.tv.data.security.PlaylistSecrets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Editable representation used by the personal-playlist UI. */
data class PlaylistDraft(
    val id: Long? = null,
    val name: String = "",
    val type: String = Playlist.TYPE_XTREAM,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
)

/** Result of a connection test: a user-facing message plus optional account limits. */
data class PlaylistProbe(
    val message: String,
    val expiryEpochSec: Long? = null,
    val maxConnections: Int? = null,
)

/**
 * Owns playlists entered directly by the user. Portal-managed playlists keep a
 * non-negative portalId and are intentionally read-only from this repository.
 */
@Singleton
class PlaylistManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NovaDatabase,
    private val xtream: XtreamClient,
    private val okHttpClient: OkHttpClient,
    private val playlistSecrets: PlaylistSecrets,
) {

    /**
     * Validates the draft and probes the provider over the network without
     * persisting anything: Xtream drafts must report an Active account, M3U
     * drafts must look like a real playlist. Failures carry user-facing messages.
     */
    suspend fun test(draft: PlaylistDraft): Result<PlaylistProbe> = runCatching {
        val normalized = normalize(draft)
        validate(normalized)
        val transient = normalized.toPlaylist(portalId = -1L)

        when (normalized.type) {
            Playlist.TYPE_XTREAM -> {
                val info = xtream.userInfo(transient).userInfo
                    ?: error("The server responded, but no Xtream account information was returned")
                val status = info.status?.trim().orEmpty()
                if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
                    error("Xtream account status: $status")
                }
                PlaylistProbe(
                    message = "Xtream connection successful",
                    expiryEpochSec = info.expDate,
                    maxConnections = info.maxConnections?.toInt(),
                )
            }
            Playlist.TYPE_M3U -> {
                inspectM3u(normalized.url)
                PlaylistProbe(message = "M3U playlist looks valid")
            }
            else -> error("Unsupported playlist type")
        }
    }

    /**
     * Inserts or updates a personal playlist, sealing credentials before Room
     * sees them. The first playlist ever saved becomes active automatically;
     * edits refuse portal-managed rows and type changes, and reset lastSync so
     * the next sync does a full refresh.
     */
    suspend fun save(draft: PlaylistDraft): Result<Playlist> = runCatching {
        val normalized = normalize(draft)
        validate(normalized)
        val dao = db.playlistDao()

        if (normalized.id == null) {
            val shouldActivate = dao.count() == 0
            val sealed = playlistSecrets.seal(
                normalized.toPlaylist(
                    portalId = nextLocalPortalId(),
                    isActive = shouldActivate,
                ),
            )
            val insertedId = dao.insert(sealed)
            if (shouldActivate) dao.setActive(insertedId)
            checkNotNull(dao.getById(insertedId))
        } else {
            val existing = requireNotNull(dao.getById(normalized.id)) { "Playlist no longer exists" }
            require(isPersonal(existing)) { "Portal-managed playlists cannot be edited on this device" }
            require(existing.type == normalized.type) {
                "Playlist type cannot be changed. Create a new playlist instead."
            }
            val updated = existing.copy(
                name = normalized.name,
                server = normalized.server.takeIf { existing.type == Playlist.TYPE_XTREAM },
                username = normalized.username.takeIf { existing.type == Playlist.TYPE_XTREAM },
                password = normalized.password.takeIf { existing.type == Playlist.TYPE_XTREAM },
                url = normalized.url.takeIf { existing.type == Playlist.TYPE_M3U },
                lastSyncEpochMs = 0L,
            ).let(playlistSecrets::seal)
            dao.update(updated)
            updated
        }
    }

    /** Copies a selected M3U file into private app storage and creates a playlist. */
    suspend fun importM3u(uri: Uri): Result<Playlist> = runCatching {
        withContext(Dispatchers.IO) {
            val displayName = queryDisplayName(uri)
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "Imported playlist"
            val directory = File(context.filesDir, "playlist_imports").apply { mkdirs() }
            val destination = File(directory, "${UUID.randomUUID()}.m3u")

            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: error("Could not read the selected file")

            try {
                inspectM3u(destination.toURI().toString())
                save(
                    PlaylistDraft(
                        name = displayName,
                        type = Playlist.TYPE_M3U,
                        url = destination.toURI().toString(),
                    ),
                ).getOrThrow()
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
        }
    }

    /** Opens the sealed credentials into an editable draft for the playlist form. */
    fun draftFrom(playlist: Playlist): PlaylistDraft {
        val opened = playlistSecrets.open(playlist)
        return PlaylistDraft(
            id = opened.id,
            name = opened.name,
            type = opened.type,
            server = opened.server.orEmpty(),
            username = opened.username.orEmpty(),
            password = opened.password.orEmpty(),
            url = opened.url.orEmpty(),
        )
    }

    /** User-entered playlists carry a negative portalId; portal assignments are non-negative. */
    fun isPersonal(playlist: Playlist): Boolean = playlist.portalId < 0L

    /**
     * Best-effort cleanup of the private copy created by [importM3u]. Only
     * touches personal file-backed M3U playlists; deletion errors are swallowed.
     */
    fun deleteImportedFile(playlist: Playlist) {
        val opened = playlistSecrets.open(playlist)
        val source = opened.url ?: return
        if (!isPersonal(opened) || opened.type != Playlist.TYPE_M3U || !source.startsWith("file:")) return
        runCatching { File(URI(source)).delete() }
    }

    // Synthesizes a unique negative portalId (timestamp-based, probing down on
    // collision) so personal rows never clash with portal-assigned ids.
    private suspend fun nextLocalPortalId(): Long {
        val dao = db.playlistDao()
        var candidate = -System.currentTimeMillis().coerceAtLeast(1L)
        while (dao.getByPortalId(candidate) != null) candidate--
        return candidate
    }

    // Trims fields, canonicalizes the type and repairs server/URL forms before validation.
    private fun normalize(draft: PlaylistDraft): PlaylistDraft {
        val type = when (draft.type.lowercase()) {
            Playlist.TYPE_M3U -> Playlist.TYPE_M3U
            else -> Playlist.TYPE_XTREAM
        }
        return draft.copy(
            name = draft.name.trim(),
            type = type,
            server = normalizeServer(draft.server),
            username = draft.username.trim(),
            password = draft.password.trim(),
            url = normalizeUrl(draft.url),
        )
    }

    // Throws IllegalArgumentException with user-facing messages when required
    // fields for the draft's type are missing or malformed.
    private fun validate(draft: PlaylistDraft) {
        require(draft.name.isNotBlank()) { "Enter a playlist name" }
        when (draft.type) {
            Playlist.TYPE_XTREAM -> {
                require(draft.server.isNotBlank()) { "Enter the Xtream server URL" }
                require(draft.username.isNotBlank()) { "Enter the Xtream username" }
                require(draft.password.isNotBlank()) { "Enter the Xtream password" }
                require(draft.server.startsWith("http://") || draft.server.startsWith("https://")) {
                    "Server URL must start with http:// or https://"
                }
            }
            Playlist.TYPE_M3U -> {
                require(draft.url.isNotBlank()) { "Enter an M3U URL" }
                require(
                    draft.url.startsWith("http://") ||
                        draft.url.startsWith("https://") ||
                        draft.url.startsWith("file:"),
                ) { "M3U source must be an HTTP(S) URL or an imported file" }
            }
            else -> error("Unsupported playlist type")
        }
    }

    // Maps the draft to an entity, keeping only the fields relevant to its type.
    private fun PlaylistDraft.toPlaylist(portalId: Long, isActive: Boolean = false): Playlist = Playlist(
        portalId = portalId,
        name = name,
        type = type,
        server = server.takeIf { type == Playlist.TYPE_XTREAM },
        username = username.takeIf { type == Playlist.TYPE_XTREAM },
        password = password.takeIf { type == Playlist.TYPE_XTREAM },
        url = url.takeIf { type == Playlist.TYPE_M3U },
        isActive = isActive,
    )

    // Adds a scheme if missing and strips any pasted /player_api.php suffix and
    // trailing slashes, leaving the bare Xtream base URL.
    private fun normalizeServer(raw: String): String {
        var value = raw.trim()
        if (value.isBlank()) return value
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://$value"
        value = value.substringBefore("/player_api.php")
        return value.trimEnd('/')
    }

    // Adds http:// to bare hosts; file: URIs from imports pass through untouched.
    private fun normalizeUrl(raw: String): String {
        val value = raw.trim()
        if (value.isBlank() || value.startsWith("file:")) return value
        return if (value.startsWith("http://") || value.startsWith("https://")) value else "http://$value"
    }

    // Streams the source on Dispatchers.IO and checks at most 250 lines for
    // #EXTM3U/#EXTINF markers, so a huge or bogus file cannot stall the probe.
    private suspend fun inspectM3u(url: String) = withContext(Dispatchers.IO) {
        val reader = when {
            url.startsWith("file:") -> File(URI(url)).bufferedReader()
            else -> {
                val response = okHttpClient.newCall(Request.Builder().url(url).get().build()).execute()
                if (!response.isSuccessful) {
                    response.close()
                    error("M3U download failed: HTTP ${response.code}")
                }
                response.body?.charStream()?.buffered()
                    ?: run {
                        response.close()
                        error("M3U download returned an empty response")
                    }
            }
        }

        reader.useLines { lines ->
            var foundHeader = false
            var foundEntry = false
            val iterator = lines.iterator()
            var inspected = 0
            while (iterator.hasNext() && inspected < 250) {
                val line = iterator.next().trim()
                inspected++
                if (line.startsWith("#EXTM3U", ignoreCase = true)) foundHeader = true
                if (line.startsWith("#EXTINF", ignoreCase = true)) foundEntry = true
                if (foundEntry) break
            }
            require(foundHeader || foundEntry) { "The selected source does not look like an M3U playlist" }
        }
    }

    // Resolves the picker file's display name for use as the default playlist name.
    private fun queryDisplayName(uri: Uri): String? = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}
