package com.novaplay.tv.data.remote

import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.security.PlaylistSecrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Minimal Retrofit escape hatch: URLs are built per playlist at runtime, so no base-url mapping applies. */
interface XtreamRawApi {
    /** Fetches an absolute URL; @Streaming keeps the body un-buffered so huge catalogues never load into memory. */
    @Streaming
    @GET
    suspend fun raw(@Url url: String): ResponseBody
}

/**
 * Xtream Codes (player_api.php) client. List responses are decoded as streams
 * with lenient serializers, so very large or dirty panel payloads neither blow
 * the heap nor abort a sync. Credentials are opened via [PlaylistSecrets] on
 * every call because stored playlist rows may hold sealed values.
 */
@OptIn(ExperimentalSerializationApi::class)
@Singleton
class XtreamClient @Inject constructor(
    private val api: XtreamRawApi,
    private val json: Json,
    private val playlistSecrets: PlaylistSecrets,
) {
    /** Auth handshake (player_api.php with no action): account status, expiry and connection limit. */
    suspend fun userInfo(playlist: Playlist): XtreamAuthResponse {
        val opened = playlistSecrets.open(playlist)
        return fetch(playerApiUrl(opened).build().toString(), XtreamAuthResponse.serializer())
    }

    /** Fetches the live category list (small; decoded whole). */
    suspend fun liveCategories(playlist: Playlist): List<XtreamCategoryDto> {
        val opened = playlistSecrets.open(playlist)
        return fetch(actionUrl(opened, "get_live_categories"), CATEGORY_LIST)
    }

    /** Fetches the VOD category list (small; decoded whole). */
    suspend fun vodCategories(playlist: Playlist): List<XtreamCategoryDto> {
        val opened = playlistSecrets.open(playlist)
        return fetch(actionUrl(opened, "get_vod_categories"), CATEGORY_LIST)
    }

    /** Fetches the series category list (small; decoded whole). */
    suspend fun seriesCategories(playlist: Playlist): List<XtreamCategoryDto> {
        val opened = playlistSecrets.open(playlist)
        return fetch(actionUrl(opened, "get_series_categories"), CATEGORY_LIST)
    }

    // Legacy direct-stream helpers remain available for small or on-demand calls.
    suspend fun liveStreams(playlist: Playlist, consume: suspend (Sequence<XtreamLiveStreamDto>) -> Unit) {
        val opened = playlistSecrets.open(playlist)
        fetchSequence(actionUrl(opened, "get_live_streams"), XtreamLiveStreamDto.serializer(), consume)
    }

    /** Streams get_vod_streams straight off the network; see [liveStreams]. */
    suspend fun vodStreams(playlist: Playlist, consume: suspend (Sequence<XtreamVodStreamDto>) -> Unit) {
        val opened = playlistSecrets.open(playlist)
        fetchSequence(actionUrl(opened, "get_vod_streams"), XtreamVodStreamDto.serializer(), consume)
    }

    /** Streams get_series straight off the network; see [liveStreams]. */
    suspend fun series(playlist: Playlist, consume: suspend (Sequence<XtreamSeriesDto>) -> Unit) {
        val opened = playlistSecrets.open(playlist)
        fetchSequence(actionUrl(opened, "get_series"), XtreamSeriesDto.serializer(), consume)
    }

    // Bulk sync first downloads to a bounded temporary file. Room transactions
    // then read local disk rather than holding a transaction open across a slow
    // or interrupted provider connection.
    suspend fun stageLiveStreams(playlist: Playlist, destination: File) {
        val opened = playlistSecrets.open(playlist)
        stage(actionUrl(opened, "get_live_streams"), destination)
    }

    /** Stages get_vod_streams to a local file; see [stageLiveStreams]. */
    suspend fun stageVodStreams(playlist: Playlist, destination: File) {
        val opened = playlistSecrets.open(playlist)
        stage(actionUrl(opened, "get_vod_streams"), destination)
    }

    /** Stages get_series to a local file; see [stageLiveStreams]. */
    suspend fun stageSeries(playlist: Playlist, destination: File) {
        val opened = playlistSecrets.open(playlist)
        stage(actionUrl(opened, "get_series"), destination)
    }

    /** Streams live-channel DTOs out of a staged file, decoding lazily one element at a time. */
    suspend fun consumeStagedLive(file: File, consume: suspend (Sequence<XtreamLiveStreamDto>) -> Unit) =
        consumeFileSequence(file, XtreamLiveStreamDto.serializer(), consume)

    /** Streams VOD DTOs out of a staged file, decoding lazily one element at a time. */
    suspend fun consumeStagedVod(file: File, consume: suspend (Sequence<XtreamVodStreamDto>) -> Unit) =
        consumeFileSequence(file, XtreamVodStreamDto.serializer(), consume)

    /** Streams series DTOs out of a staged file, decoding lazily one element at a time. */
    suspend fun consumeStagedSeries(file: File, consume: suspend (Sequence<XtreamSeriesDto>) -> Unit) =
        consumeFileSequence(file, XtreamSeriesDto.serializer(), consume)

    /** Fetches one title's details (plot, duration, backdrop) lazily when its details screen opens. */
    suspend fun vodInfo(playlist: Playlist, vodId: Long): XtreamVodInfoResponse {
        val opened = playlistSecrets.open(playlist)
        return fetch(
            playerApiUrl(opened)
                .addQueryParameter("action", "get_vod_info")
                .addQueryParameter("vod_id", vodId.toString())
                .build().toString(),
            XtreamVodInfoResponse.serializer(),
        )
    }

    /**
     * Fetches series details plus a flattened episode list. Individual malformed
     * episodes are skipped instead of failing the whole response.
     */
    suspend fun seriesInfo(playlist: Playlist, seriesId: Long): Pair<XtreamSeriesInfoDetails?, List<XtreamEpisodeDto>> {
        val opened = playlistSecrets.open(playlist)
        val response = fetch(
            playerApiUrl(opened)
                .addQueryParameter("action", "get_series_info")
                .addQueryParameter("series_id", seriesId.toString())
                .build().toString(),
            XtreamSeriesInfoResponse.serializer(),
        )
        val episodes = mutableListOf<XtreamEpisodeDto>()
        // "episodes" is {"1":[...],"2":[...]} on most panels, [[...],[...]] on some.
        val seasonArrays = when (val el = response.episodes) {
            is JsonObject -> el.values
            is JsonArray -> el
            else -> emptyList()
        }
        for (seasonEl in seasonArrays) {
            val arr = seasonEl as? JsonArray ?: continue
            for (epEl in arr) {
                runCatching {
                    episodes += json.decodeFromJsonElement(XtreamEpisodeDto.serializer(), epEl)
                }
            }
        }
        return response.info to episodes
    }

    /** Builds the direct live stream URL; `extension` picks the container (m3u8 vs ts) for the HLS -> TS fallback. */
    fun liveUrl(playlist: Playlist, streamId: Long, extension: String): String {
        val opened = playlistSecrets.open(playlist)
        return serverUrl(opened).newBuilder()
            .addPathSegment("live")
            .addPathSegment(opened.username.orEmpty())
            .addPathSegment(opened.password.orEmpty())
            .addPathSegment("$streamId.$extension")
            .build().toString()
    }

    /** Builds the movie stream URL, falling back to mp4 when the panel omits the container extension. */
    fun movieUrl(playlist: Playlist, streamId: Long, containerExtension: String?): String {
        val opened = playlistSecrets.open(playlist)
        return serverUrl(opened).newBuilder()
            .addPathSegment("movie")
            .addPathSegment(opened.username.orEmpty())
            .addPathSegment(opened.password.orEmpty())
            .addPathSegment("$streamId.${containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"}")
            .build().toString()
    }

    /** Builds the episode stream URL, falling back to mp4 when the panel omits the container extension. */
    fun episodeUrl(playlist: Playlist, episodeId: Long, containerExtension: String?): String {
        val opened = playlistSecrets.open(playlist)
        return serverUrl(opened).newBuilder()
            .addPathSegment("series")
            .addPathSegment(opened.username.orEmpty())
            .addPathSegment(opened.password.orEmpty())
            .addPathSegment("$episodeId.${containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"}")
            .build().toString()
    }

    // Decodes one whole JSON document straight off the network stream — no intermediate string.
    private suspend fun <T> fetch(url: String, strategy: DeserializationStrategy<T>): T =
        withContext(Dispatchers.IO) {
            api.raw(url).use { body ->
                json.decodeFromStream(strategy, body.byteStream())
            }
        }

    // Streams a JSON array, handing the caller a lazy sequence: elements decode one at a
    // time and the connection stays open only for the duration of `consume`.
    private suspend fun <T> fetchSequence(
        url: String,
        strategy: DeserializationStrategy<T>,
        consume: suspend (Sequence<T>) -> Unit,
    ) = withContext(Dispatchers.IO) {
        api.raw(url).use { body ->
            consume(json.decodeToSequence(body.byteStream(), strategy))
        }
    }

    // Downloads a response into a bounded local file; a partial file is deleted on any
    // failure so consumers never parse truncated JSON.
    private suspend fun stage(url: String, destination: File) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        try {
            api.raw(url).use { body ->
                body.byteStream().use { input ->
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

    // Same lazy element-by-element decode as fetchSequence, but over a staged local file.
    private suspend fun <T> consumeFileSequence(
        file: File,
        strategy: DeserializationStrategy<T>,
        consume: suspend (Sequence<T>) -> Unit,
    ) {
        file.inputStream().buffered().use { input ->
            consume(json.decodeToSequence(input, strategy))
        }
    }

    // player_api.php URL for one API action.
    private fun actionUrl(playlist: Playlist, action: String): String =
        playerApiUrl(playlist).addQueryParameter("action", action).build().toString()

    // Base player_api.php URL carrying the opened credentials as query parameters.
    private fun playerApiUrl(playlist: Playlist): HttpUrl.Builder =
        serverUrl(playlist).newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", playlist.username.orEmpty())
            .addQueryParameter("password", playlist.password.orEmpty())

    // Parses the stored server as an HttpUrl; the trailing slash is stripped so path segments append cleanly.
    private fun serverUrl(playlist: Playlist): HttpUrl =
        playlist.server.orEmpty().trimEnd('/').toHttpUrl()

    // Streams input to output, failing once `limit` bytes are exceeded so a runaway
    // provider response can't fill the disk.
    private fun copyWithLimit(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Provider catalogue exceeds the ${limit / 1024 / 1024} MB safety limit" }
            output.write(buffer, 0, read)
        }
    }

    private companion object {
        private val CATEGORY_LIST = kotlinx.serialization.builtins.ListSerializer(XtreamCategoryDto.serializer())
        private const val MAX_SNAPSHOT_BYTES = 256L * 1024 * 1024
    }
}
