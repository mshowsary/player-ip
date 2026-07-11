package com.novaplay.tv.data.remote

import com.novaplay.tv.data.db.Playlist
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
) {
    suspend fun userInfo(playlist: Playlist): XtreamAuthResponse =
        fetch(playerApiUrl(playlist).build().toString(), XtreamAuthResponse.serializer())

    suspend fun liveCategories(playlist: Playlist): List<XtreamCategoryDto> =
        fetch(actionUrl(playlist, "get_live_categories"), CATEGORY_LIST)

    suspend fun vodCategories(playlist: Playlist): List<XtreamCategoryDto> =
        fetch(actionUrl(playlist, "get_vod_categories"), CATEGORY_LIST)

    suspend fun seriesCategories(playlist: Playlist): List<XtreamCategoryDto> =
        fetch(actionUrl(playlist, "get_series_categories"), CATEGORY_LIST)

    // Stream lists can hold 50k+ entries: decodeToSequence parses lazily from the
    // network stream so the full list is never materialized in memory.
    suspend fun liveStreams(playlist: Playlist, consume: suspend (Sequence<XtreamLiveStreamDto>) -> Unit) =
        fetchSequence(actionUrl(playlist, "get_live_streams"), XtreamLiveStreamDto.serializer(), consume)

    suspend fun vodStreams(playlist: Playlist, consume: suspend (Sequence<XtreamVodStreamDto>) -> Unit) =
        fetchSequence(actionUrl(playlist, "get_vod_streams"), XtreamVodStreamDto.serializer(), consume)

    suspend fun series(playlist: Playlist, consume: suspend (Sequence<XtreamSeriesDto>) -> Unit) =
        fetchSequence(actionUrl(playlist, "get_series"), XtreamSeriesDto.serializer(), consume)

    suspend fun vodInfo(playlist: Playlist, vodId: Long): XtreamVodInfoResponse =
        fetch(
            playerApiUrl(playlist)
                .addQueryParameter("action", "get_vod_info")
                .addQueryParameter("vod_id", vodId.toString())
                .build().toString(),
            XtreamVodInfoResponse.serializer(),
        )

    suspend fun seriesInfo(playlist: Playlist, seriesId: Long): Pair<XtreamSeriesInfoDetails?, List<XtreamEpisodeDto>> {
        val response = fetch(
            playerApiUrl(playlist)
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

    private fun actionUrl(playlist: Playlist, action: String): String =
        playerApiUrl(playlist).addQueryParameter("action", action).build().toString()

    private fun playerApiUrl(playlist: Playlist): HttpUrl.Builder =
        serverUrl(playlist).newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", playlist.username.orEmpty())
            .addQueryParameter("password", playlist.password.orEmpty())

    companion object {
        private val CATEGORY_LIST = kotlinx.serialization.builtins.ListSerializer(XtreamCategoryDto.serializer())

        private fun serverUrl(playlist: Playlist): HttpUrl =
            playlist.server.orEmpty().trimEnd('/').toHttpUrl()

        fun liveUrl(playlist: Playlist, streamId: Long, extension: String): String =
            serverUrl(playlist).newBuilder()
                .addPathSegment("live")
                .addPathSegment(playlist.username.orEmpty())
                .addPathSegment(playlist.password.orEmpty())
                .addPathSegment("$streamId.$extension")
                .build().toString()

        fun movieUrl(playlist: Playlist, streamId: Long, containerExtension: String?): String =
            serverUrl(playlist).newBuilder()
                .addPathSegment("movie")
                .addPathSegment(playlist.username.orEmpty())
                .addPathSegment(playlist.password.orEmpty())
                .addPathSegment("$streamId.${containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"}")
                .build().toString()

        fun episodeUrl(playlist: Playlist, episodeId: Long, containerExtension: String?): String =
            serverUrl(playlist).newBuilder()
                .addPathSegment("series")
                .addPathSegment(playlist.username.orEmpty())
                .addPathSegment(playlist.password.orEmpty())
                .addPathSegment("$episodeId.${containerExtension?.takeIf { it.isNotBlank() } ?: "mp4"}")
                .build().toString()
    }
}
