package com.novaplay.tv.data.remote

import com.novaplay.tv.core.LenientDoubleOrNull
import com.novaplay.tv.core.LenientFirstStringOrNull
import com.novaplay.tv.core.LenientInt
import com.novaplay.tv.core.LenientLong
import com.novaplay.tv.core.LenientLongOrNull
import com.novaplay.tv.core.LenientStringOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class XtreamAuthResponse(
    @SerialName("user_info") val userInfo: XtreamUserInfo? = null,
)

@Serializable
data class XtreamUserInfo(
    @Serializable(with = LenientStringOrNull::class) val status: String? = null,
    @Serializable(with = LenientLongOrNull::class) @SerialName("exp_date") val expDate: Long? = null,
    @Serializable(with = LenientLongOrNull::class) @SerialName("max_connections") val maxConnections: Long? = null,
)

@Serializable
data class XtreamCategoryDto(
    @Serializable(with = LenientStringOrNull::class) @SerialName("category_id") val categoryId: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("category_name") val categoryName: String? = null,
)

@Serializable
data class XtreamLiveStreamDto(
    @Serializable(with = LenientLong::class) @SerialName("stream_id") val streamId: Long = 0,
    @Serializable(with = LenientInt::class) val num: Int = 0,
    @Serializable(with = LenientStringOrNull::class) val name: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("stream_icon") val streamIcon: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("category_id") val categoryId: String? = null,
)

@Serializable
data class XtreamVodStreamDto(
    @Serializable(with = LenientLong::class) @SerialName("stream_id") val streamId: Long = 0,
    @Serializable(with = LenientStringOrNull::class) val name: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("stream_icon") val streamIcon: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("category_id") val categoryId: String? = null,
    @Serializable(with = LenientDoubleOrNull::class) val rating: Double? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("container_extension") val containerExtension: String? = null,
    @Serializable(with = LenientStringOrNull::class) val year: String? = null,
)

@Serializable
data class XtreamSeriesDto(
    @Serializable(with = LenientLong::class) @SerialName("series_id") val seriesId: Long = 0,
    @Serializable(with = LenientStringOrNull::class) val name: String? = null,
    @Serializable(with = LenientStringOrNull::class) val cover: String? = null,
    @Serializable(with = LenientStringOrNull::class) val plot: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("category_id") val categoryId: String? = null,
    @Serializable(with = LenientDoubleOrNull::class) val rating: Double? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("release_date") val releaseDate: String? = null,
    @Serializable(with = LenientFirstStringOrNull::class) @SerialName("backdrop_path") val backdropPath: String? = null,
)

@Serializable
data class XtreamVodInfoResponse(
    val info: XtreamVodInfo? = null,
)

@Serializable
data class XtreamVodInfo(
    @Serializable(with = LenientStringOrNull::class) val plot: String? = null,
    @Serializable(with = LenientStringOrNull::class) val genre: String? = null,
    @Serializable(with = LenientLongOrNull::class) @SerialName("duration_secs") val durationSecs: Long? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("releasedate") val releaseDate: String? = null,
    @Serializable(with = LenientDoubleOrNull::class) val rating: Double? = null,
    @Serializable(with = LenientFirstStringOrNull::class) @SerialName("backdrop_path") val backdropPath: String? = null,
)

@Serializable
data class XtreamSeriesInfoResponse(
    val info: XtreamSeriesInfoDetails? = null,
    // Map of season -> episodes on most panels, but a bare array on some;
    // parsed manually in XtreamClient.
    val episodes: JsonElement? = null,
)

@Serializable
data class XtreamSeriesInfoDetails(
    @Serializable(with = LenientStringOrNull::class) val plot: String? = null,
    @Serializable(with = LenientDoubleOrNull::class) val rating: Double? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("release_date") val releaseDate: String? = null,
    @Serializable(with = LenientFirstStringOrNull::class) @SerialName("backdrop_path") val backdropPath: String? = null,
)

@Serializable
data class XtreamEpisodeDto(
    @Serializable(with = LenientLong::class) val id: Long = 0,
    @Serializable(with = LenientInt::class) @SerialName("episode_num") val episodeNum: Int = 0,
    @Serializable(with = LenientStringOrNull::class) val title: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("container_extension") val containerExtension: String? = null,
    @Serializable(with = LenientInt::class) val season: Int = 0,
    val info: XtreamEpisodeInfo? = null,
)

@Serializable
data class XtreamEpisodeInfo(
    @Serializable(with = LenientLongOrNull::class) @SerialName("duration_secs") val durationSecs: Long? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("movie_image") val movieImage: String? = null,
)
