package com.novaplay.tv.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.novaplay.tv.data.db.EpgProgramme
import com.novaplay.tv.data.db.NovaDatabase
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.epg.EpgRetentionPolicy
import com.novaplay.tv.data.epg.XmltvParser
import com.novaplay.tv.data.remote.XtreamClient
import com.novaplay.tv.data.security.PlaylistSecrets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes the local TV guide from a playlist's XMLTV source: Xtream panels
 * serve it at xmltv.php, M3U playlists advertise it via the url-tvg header
 * attribute persisted on the playlist row. Same staged, transactional
 * replacement guarantees as the catalogue sync — a failed refresh throws and
 * leaves the previous guide untouched.
 */
@Singleton
class EpgRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: NovaDatabase,
    private val xtream: XtreamClient,
    private val xmltvParser: XmltvParser,
    private val playlistSecrets: PlaylistSecrets,
    private val okHttpClient: OkHttpClient,
) {
    /**
     * Downloads, parses and installs the guide for [playlist], returning the
     * number of stored programmes. Returns 0 without any I/O when the playlist
     * has no guide source. Programmes are filtered to the retention window and
     * to channels that actually exist in the playlist, so a shared provider
     * guide covering thousands of foreign channels cannot bloat local storage.
     */
    suspend fun refresh(playlist: Playlist, nowMs: Long): Int {
        val knownChannels = db.liveDao().epgChannelKeys(playlist.id).toHashSet()
        if (knownChannels.isEmpty()) return 0

        val snapshot = newSnapshot()
        try {
            when (playlist.type) {
                Playlist.TYPE_XTREAM -> xtream.stageXmltv(playlist, snapshot)
                Playlist.TYPE_M3U -> {
                    val guideUrl = playlistSecrets.open(playlist).epgUrl
                        ?.takeIf { it.isNotBlank() }
                        ?: return 0
                    stageUrl(guideUrl, snapshot)
                }
                else -> return 0
            }

            var installed = 0
            db.withTransaction {
                val dao = db.epgDao()
                dao.wipeForPlaylist(playlist.id)
                val buffer = ArrayList<EpgProgramme>(CHUNK)
                snapshot.inputStream().buffered().use { stream ->
                    // SAX callbacks cannot suspend, so inserts use the blocking DAO
                    // variant — legal here because withTransaction bodies run on
                    // Room's transaction dispatcher.
                    xmltvParser.parse(stream) { programme ->
                        if (
                            programme.channelId in knownChannels &&
                            EpgRetentionPolicy.shouldStore(programme.startMs, programme.endMs, nowMs)
                        ) {
                            buffer += EpgProgramme(
                                playlistId = playlist.id,
                                epgChannelId = programme.channelId,
                                startMs = programme.startMs,
                                endMs = programme.endMs,
                                title = programme.title,
                                description = programme.description,
                            )
                            installed++
                            if (buffer.size >= CHUNK) {
                                dao.insertAllBlocking(buffer)
                                buffer.clear()
                            }
                        }
                    }
                }
                if (buffer.isNotEmpty()) dao.insertAllBlocking(buffer)
            }
            return installed
        } finally {
            snapshot.delete()
        }
    }

    // Bounded download of an arbitrary guide URL (M3U url-tvg sources); a partial
    // file is deleted on any failure so the parser never sees truncated XML.
    private suspend fun stageUrl(url: String, destination: File) = withContext(Dispatchers.IO) {
        try {
            val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                response.close()
                throw IOException("Guide download failed: HTTP ${response.code}")
            }
            val body = response.body ?: run {
                response.close()
                throw IOException("Guide download failed: empty body")
            }
            body.use { source ->
                source.byteStream().buffered().use { input ->
                    destination.outputStream().buffered().use { output ->
                        copyWithLimit(input, output, MAX_SNAPSHOT_BYTES)
                    }
                }
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    // Same snapshot-cache hygiene as the catalogue sync: leftovers from a crash
    // are purged by age so the cache directory stays bounded.
    private fun newSnapshot(): File {
        val directory = File(context.cacheDir, "epg_snapshots").apply { mkdirs() }
        directory.listFiles()
            ?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - SNAPSHOT_TTL_MS }
            ?.forEach(File::delete)
        return File.createTempFile("xmltv-", ".xml", directory)
    }

    // Streams input to output, failing once `limit` bytes are exceeded so a
    // runaway guide response can't fill the disk.
    private fun copyWithLimit(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Guide document exceeds the ${limit / 1024 / 1024} MB safety limit" }
            output.write(buffer, 0, read)
        }
    }

    private companion object {
        const val CHUNK = 1000
        const val SNAPSHOT_TTL_MS = 24L * 60 * 60 * 1000
        const val MAX_SNAPSHOT_BYTES = 256L * 1024 * 1024
    }
}
