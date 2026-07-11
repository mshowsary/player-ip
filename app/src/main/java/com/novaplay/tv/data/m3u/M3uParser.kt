package com.novaplay.tv.data.m3u

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class M3uEntry(
    val name: String,
    val logoUrl: String?,
    val group: String?,
    val tvgId: String?,
    val url: String,
)

// Streams the playlist line-by-line straight off the socket; a 50k-channel M3U
// is never held in memory as a whole.
@Singleton
class M3uParser @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun parse(url: String, onEntry: suspend (M3uEntry) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("M3U download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("M3U download failed: empty body")
                body.charStream().buffered().useLines { lines ->
                    var pending: PendingEntry? = null
                    for (rawLine in lines) {
                        val line = rawLine.trim()
                        when {
                            line.isEmpty() || line.startsWith("#EXTM3U") -> Unit
                            line.startsWith("#EXTINF") -> pending = parseExtInf(line)
                            line.startsWith("#") -> Unit // other directives (e.g. #EXTVLCOPT) skipped
                            else -> {
                                val info = pending
                                pending = null
                                // A URL with no preceding EXTINF is malformed; skip, never fatal.
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
            }
        }

    private data class PendingEntry(val name: String, val attributes: Map<String, String>)

    private fun parseExtInf(line: String): PendingEntry {
        val attributes = ATTRIBUTE_REGEX.findAll(line)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        // Display name = text after the comma that follows the last quoted attribute
        // (names themselves may contain commas).
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
    }
}
