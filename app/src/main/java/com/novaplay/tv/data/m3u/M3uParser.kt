package com.novaplay.tv.data.m3u

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/** One channel parsed from an #EXTINF/URL line pair; optional fields come from EXTINF attributes. */
data class M3uEntry(
    val name: String,
    val logoUrl: String?,
    val group: String?,
    val tvgId: String?,
    val url: String,
)

/**
 * Line-by-line streaming M3U parser: entries are emitted to a callback one at a
 * time, so arbitrarily large playlists are never materialized in memory.
 */
@Singleton
class M3uParser @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    // Direct parsing remains available for validation and smaller operations.
    suspend fun parse(url: String, onEntry: suspend (M3uEntry) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            openReader(url).use { reader -> parseReader(reader, onEntry) }
        }

    /**
     * Copies a remote or local M3U into a bounded cache file before Room changes
     * begin. If the network fails, the existing catalogue is untouched.
     */
    suspend fun snapshot(url: String, destination: File): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        try {
            val input = when {
                url.startsWith("file:") -> File(URI(url)).inputStream().buffered()
                else -> {
                    val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                    if (!response.isSuccessful) {
                        response.close()
                        throw IOException("M3U download failed: HTTP ${response.code}")
                    }
                    response.body?.byteStream()?.buffered()
                        ?: run {
                            response.close()
                            throw IOException("M3U download failed: empty body")
                        }
                }
            }
            input.use { source ->
                destination.outputStream().buffered().use { output ->
                    copyWithLimit(source, output, MAX_SNAPSHOT_BYTES)
                }
            }
            destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    /** Parse an already-local snapshot without any provider network request. */
    suspend fun parseSnapshot(file: File, onEntry: suspend (M3uEntry) -> Unit) {
        file.bufferedReader().use { reader -> parseReader(reader, onEntry) }
    }

    // Core state machine: an #EXTINF line arms `pending`; the next non-comment line is
    // its URL. Nameless entries are dropped so dirty rows never abort the whole playlist.
    private suspend fun parseReader(reader: BufferedReader, onEntry: suspend (M3uEntry) -> Unit) {
        var pending: PendingEntry? = null
        while (true) {
            val line = reader.readLine()?.trim() ?: break
            when {
                line.isEmpty() || line.startsWith("#EXTM3U") -> Unit
                line.startsWith("#EXTINF") -> pending = parseExtInf(line)
                line.startsWith("#") -> Unit // e.g. #EXTVLCOPT
                else -> {
                    val info = pending
                    pending = null
                    if (info != null && info.name.isNotBlank()) {
                        onEntry(
                            M3uEntry(
                                name = info.name,
                                logoUrl = info.attributes["tvg-logo"]?.takeIf { it.isNotBlank() },
                                group = info.attributes["group-title"]?.takeIf { it.isNotBlank() },
                                tvgId = info.attributes["tvg-id"]?.takeIf { it.isNotBlank() },
                                url = line,
                            ),
                        )
                    }
                }
            }
        }
    }

    // Opens a buffered reader over a file: URI or an HTTP body; failed responses are
    // closed before throwing so connections are not leaked.
    private fun openReader(url: String): BufferedReader = when {
        url.startsWith("file:") -> File(URI(url)).bufferedReader()
        else -> {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw IOException("M3U download failed: HTTP ${response.code}")
            }
            val body = response.body ?: run {
                response.close()
                throw IOException("M3U download failed: empty body")
            }
            body.charStream().buffered()
        }
    }

    // Streams input to output, failing once `limit` bytes are exceeded so a hostile or
    // endless playlist can't fill the disk.
    private fun copyWithLimit(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "M3U playlist exceeds the ${limit / 1024 / 1024} MB safety limit" }
            output.write(buffer, 0, read)
        }
    }

    // #EXTINF metadata waiting for its URL line.
    private data class PendingEntry(val name: String, val attributes: Map<String, String>)

    // Pulls key="value" attributes and the display name after the last comma. The comma is
    // searched after the last quote because quoted attribute values may contain commas;
    // an empty name falls back to tvg-name.
    private fun parseExtInf(line: String): PendingEntry {
        val attributes = ATTRIBUTE_REGEX.findAll(line)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val lastQuote = line.lastIndexOf('"')
        val commaIndex = line.indexOf(',', startIndex = if (lastQuote >= 0) lastQuote else 0)
        val name = if (commaIndex >= 0) line.substring(commaIndex + 1).trim() else ""
        return PendingEntry(
            name = name.ifBlank { attributes["tvg-name"].orEmpty() },
            attributes = attributes,
        )
    }

    private companion object {
        val ATTRIBUTE_REGEX = Regex("""([\w-]+)="([^"]*)"""")
        const val MAX_SNAPSHOT_BYTES = 256L * 1024 * 1024
    }
}
