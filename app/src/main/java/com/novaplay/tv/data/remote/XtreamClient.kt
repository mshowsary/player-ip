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

interface XtreamRawApi {
    @Streaming
    @GET
    suspend fun raw(@Url url: String): ResponseBody
}

@OptIn(ExperimentalSerializationApi::class)
@Singleton
class XtreamClient @Inject constructor(
    private val api: XtreamRawApi,
    private val json: Json,
    private val playlistSecrets: PlaylistSecrets,
) {
    suspend fun userInfo(playlist: Playlist): XtreamAuthResponse {
        val opened = playlistSecrets.open(playlist)
        return fetch(playerApiUrl(opened).build().toString(), XtreamAuthResponse.serializer())
    }

    suspend fun liveCategories(playlist: Playlist): List<XtreamCategoryDto> {
        val opened = playlistSecrets.open(playlist)
        return fetch(actionUrl(opened, "get_live_categories"), CATEGORY_LIST)
    }

    suspend fun vodCategories(playlist: Playlist): List<XtreamCategoryDto> {
        val opened = playlistSecrets.open(playlist)
        return fetch(actionUrl(opened, "get_vod_categories"), CATEGORY_LIST)
    }

    suspend fun seriesCategories(playlist: Playlist): List<XtreamCategoryDto> {
        val opened = playlistSecrets.open(playlist)
        return fetch(actionUrl(opened, "get_series_categories"), CATEGORY_LIST)
    }

    // Legacy direct-stream helpers remain available for small or on-demand calls.
    suspend fun liveStreams(playlist: Playlist, consume: suspend (Sequence<XtreamLiveStreamDto>) -> Unit) {
        val opened = playlistSecrets.open(playlist)
        fetchSequence(actionUrl(opened, "get_live_streams"), XtreamLiveStreamDto.serializer(), consume)
    }

    suspend fun vodStreams(playlist: Playlist, consume: suspend (Sequence<XtreamVodStreamDto>) -> Unit) {
        val opened = playlistSecrets.open(playlist)
        fetchSequence(actionUrl(opened, "get_vod_streams"), XtreamVodStreamDto.serializer(), consume)
    }

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

    suspend fun stageVodStreams(playlist: Playlist, destination: File) {
        val opened = playlistSecrets.open(playlist)
        stage(actionUrl(opened, "get_vod_streams"), destination)
    }

    suspend fun stageSeries(playlist: Playlist, destination: File) {
        val opened = playlistSecrets.open(playlist)
        stage(actionUrl(opened, "get_series"), destination)
    }

    suspend fun consumeStagedLive(file: File, consume: suspend (Sequence<XtreamLiveStreamDto>) -> Unit) =
        consumeFileSequence(file, XtreamLiveStreamDto.serializer(), consume)

    suspend fun consumeStagedVod(file: File, consume: suspend (Sequence<XtreamVodStreamDto>) -> Unit) =
        consumeFileSequence(file, XtreamVodStreamDto.serializer(), consume)

    suspend fun consumeStagedSeries(file: File, consume: suspend (Sequence<XtreamSeriesDto>) -> Unit) =
        consumeFileSequence(file, XtreamSeriesDto.serializer(), consume)

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

    fun liveUrl(playlist: Playlist, streamId: Long, extension: String): String {
        val opened = playlistSecrets.open(playlist)
        return serverUrl(opened).newBuilder()
            .addPathSegment("live")
            .addPathSegment(opened.username.orEmpty())
            .addPathSegment(opened.password.orEmpty())
            .addPathSegment("$streamId.$extension")
            .build().toString()
    }

    fun movieUrl(playlist: Playlist, streamId: Long, containerExtension: String?): String {
        val opened = playlistSecrets.open(playlist)
        return serverUrl(opened).newBuilder()
            .addPathSegment("movie")
            .addPathSegment(opened.username.orEmpty())
            .addPathSegment(opened.password.orEmpty())
            .addPathSegment("$streamId.${containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"}")
            .build().toString()
    }

    fun episodeUrl(playlist: Playlist, episodeId: Long, containerExtension: String?): String {
        val opened = playlistSecrets.open(playlist)
        return serverUrl(opened).newBuilder()
            .addPathSegment("series")
            .addPathSegment(opened.username.orEmpty())
            .addPathSegment(opened.password.orEmpty())
            .addPathSegment("$episodeId.${containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"}")
            .build().toString()
    }

    private suspend fun <T> fetch(url: String, strategy: DeserializationStrategy<T>): T =
        withContext(Dispatchers.IO) {
            api.raw(url).use { body ->
                json.decodeFromStream(strategy, body.byteStream())
            }
        }

    private suspend fun <T> fetchSequence(
        url: String,
        strategy: DeserializationStrategy<T>,
        consume: suspend (Sequence<T>) -> Unit,
    ) = withContext(Dispatchers.IO) {
        api.raw(url).use { body ->
            consume(json.decodeToSequence(body.byteStream(), strategy))
        }
    }

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

    private suspend fun <T> consumeFileSequence(
        file: File,
        strategy: DeserializationStrategy<T>,
        consume: suspend (Sequence<T>) -> Unit,
    ) {
        file.inputStream().buffered().use { input ->
            consume(json.decodeToSequence(input, strategy))
        }
    }

    private fun actionUrl(playlist: Playlist, action: String): String =
        playerApiUrl(playlist).addQueryParameter("action", action).build().toString()

    private fun playerApiUrl(playlist: Playlist): HttpUrl.Builder =
        serverUrl(playlist).newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", playlist.username.orEmpty())
            .addQueryParameter("password", playlist.password.orEmpty())

    private fun serverUrl(playlist: Playlist): HttpUrl =
        playlist.server.orEmpty().trimEnd('/').toHttpUrl()

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
