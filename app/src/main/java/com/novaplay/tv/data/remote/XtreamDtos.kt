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

// All fields below use lenient serializers because panels return numbers as strings,
// ints or floats interchangeably, and a single dirty field must never abort a sync.

/** player_api.php handshake envelope; only user_info is consumed. */
@Serializable
data class XtreamAuthResponse(
    @SerialName("user_info") val userInfo: XtreamUserInfo? = null,
)

/** Account state from the auth call: status, expiry and connection limit. */
@Serializable
data class XtreamUserInfo(
    @Serializable(with = LenientStringOrNull::class) val status: String? = null,
    @Serializable(with = LenientLongOrNull::class) @SerialName("exp_date") val expDate: Long? = null,
    @Serializable(with = LenientLongOrNull::class) @SerialName("max_connections") val maxConnections: Long? = null,
)

/** One row of get_live/vod/series_categories; category ids arrive as strings. */
@Serializable
data class XtreamCategoryDto(
    @Serializable(with = LenientStringOrNull::class) @SerialName("category_id") val categoryId: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("category_name") val categoryName: String? = null,
)

/** One row of get_live_streams; `num` is the provider channel number that drives browse order and digit-jump. */
@Serializable
data class XtreamLiveStreamDto(
    @Serializable(with = LenientLong::class) @SerialName("stream_id") val streamId: Long = 0,
    @Serializable(with = LenientInt::class) val num: Int = 0,
    @Serializable(with = LenientStringOrNull::class) val name: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("stream_icon") val streamIcon: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("category_id") val categoryId: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("epg_channel_id") val epgChannelId: String? = null,
)

/** One row of get_vod_streams — list-level fields only; plot and duration come later from get_vod_info. */
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

/** One row of get_series; backdrop_path may be a bare string or an array, hence LenientFirstStringOrNull. */
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

/** get_vod_info envelope; only the `info` block is consumed. */
@Serializable
data class XtreamVodInfoResponse(
    val info: XtreamVodInfo? = null,
)

/** Per-title details fetched lazily and merged into the movies row when the details screen opens. */
@Serializable
data class XtreamVodInfo(
    @Serializable(with = LenientStringOrNull::class) val plot: String? = null,
    @Serializable(with = LenientStringOrNull::class) val genre: String? = null,
    @Serializable(with = LenientLongOrNull::class) @SerialName("duration_secs") val durationSecs: Long? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("releasedate") val releaseDate: String? = null,
    @Serializable(with = LenientDoubleOrNull::class) val rating: Double? = null,
    @Serializable(with = LenientFirstStringOrNull::class) @SerialName("backdrop_path") val backdropPath: String? = null,
)

/** get_series_info envelope: series-level details plus the raw episodes element. */
@Serializable
data class XtreamSeriesInfoResponse(
    val info: XtreamSeriesInfoDetails? = null,
    // Map of season -> episodes on most panels, but a bare array on some;
    // parsed manually in XtreamClient.
    val episodes: JsonElement? = null,
)

/** Series-level details from get_series_info, merged into the series row. */
@Serializable
data class XtreamSeriesInfoDetails(
    @Serializable(with = LenientStringOrNull::class) val plot: String? = null,
    @Serializable(with = LenientDoubleOrNull::class) val rating: Double? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("release_date") val releaseDate: String? = null,
    @Serializable(with = LenientFirstStringOrNull::class) @SerialName("backdrop_path") val backdropPath: String? = null,
)

/** One episode inside get_series_info's season map; ids often arrive as strings. */
@Serializable
data class XtreamEpisodeDto(
    @Serializable(with = LenientLong::class) val id: Long = 0,
    @Serializable(with = LenientInt::class) @SerialName("episode_num") val episodeNum: Int = 0,
    @Serializable(with = LenientStringOrNull::class) val title: String? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("container_extension") val containerExtension: String? = null,
    @Serializable(with = LenientInt::class) val season: Int = 0,
    val info: XtreamEpisodeInfo? = null,
)

/** Nested per-episode metadata: duration and thumbnail. */
@Serializable
data class XtreamEpisodeInfo(
    @Serializable(with = LenientLongOrNull::class) @SerialName("duration_secs") val durationSecs: Long? = null,
    @Serializable(with = LenientStringOrNull::class) @SerialName("movie_image") val movieImage: String? = null,
)
