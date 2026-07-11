package com.novaplay.tv.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class UiModePreference(val label: String) {
    AUTO("Auto"),
    TOUCH("Touch"),
    TV("TV / remote"),
}

enum class LiveFormat(val label: String) {
    AUTO("Auto"),
    HLS("HLS"),
    TS("MPEG-TS"),
}

enum class BackgroundSyncMode(
    val label: String,
    val intervalHours: Long?,
    val description: String,
) {
    OFF("Off", null, "Refresh only when you request it"),
    DAILY("Daily", 24L, "Refresh once a day on Wi-Fi or Ethernet"),
    TWICE_DAILY("Twice daily", 12L, "Refresh about every 12 hours on Wi-Fi or Ethernet"),
}

enum class SubtitleSize(val label: String, val fraction: Float) {
    SMALL("Small", 0.045f),
    MEDIUM("Medium", 0.0533f),
    LARGE("Large", 0.068f),
    XLARGE("Extra-Large", 0.085f),
}

enum class SubtitleColor(val label: String, val argb: Int) {
    WHITE("White", 0xFFFFFFFF.toInt()),
    YELLOW("Yellow", 0xFFFFE55C.toInt()),
    CYAN("Cyan", 0xFF7FE9FA.toInt()),
    GREEN("Green", 0xFF7CFC9A.toInt()),
}

enum class SubtitleBackground(val label: String, val argb: Int) {
    TRANSPARENT("Transparent", 0x00000000),
    SEMI("Semi-black", 0xA6000000.toInt()),
    SOLID("Solid black", 0xFF000000.toInt()),
}

enum class SubtitleEdge(val label: String) {
    NONE("None"),
    OUTLINE("Outline"),
    DROP_SHADOW("Drop shadow"),
}

data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.MEDIUM,
    val color: SubtitleColor = SubtitleColor.WHITE,
    val background: SubtitleBackground = SubtitleBackground.SEMI,
    val edge: SubtitleEdge = SubtitleEdge.OUTLINE,
)

/** Last explicit VOD track choices, reused when another title offers a match. */
data class PlaybackTrackPreferences(
    val audioLanguage: String? = null,
    val audioLabel: String? = null,
    val subtitlesEnabled: Boolean = true,
    val subtitleLanguage: String? = null,
    val subtitleLabel: String? = null,
)

data class LastSyncSummary(
    val completedAtEpochMs: Long = 0L,
    val durationMs: Long = 0L,
    val successful: Boolean = false,
    val trigger: String = "",
    val playlistType: String = "",
    val liveChannels: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
    val error: String? = null,
) {
    val exists: Boolean get() = completedAtEpochMs > 0L
}

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_MAC = stringPreferencesKey("device_mac")
        val DEVICE_KEY = stringPreferencesKey("device_key")
        val UI_MODE = stringPreferencesKey("ui_mode")
        val LIVE_FORMAT = stringPreferencesKey("live_format")
        val BACKGROUND_SYNC_MODE = stringPreferencesKey("background_sync_mode")
        val SUB_SIZE = stringPreferencesKey("sub_size")
        val SUB_COLOR = stringPreferencesKey("sub_color")
        val SUB_BACKGROUND = stringPreferencesKey("sub_background")
        val SUB_EDGE = stringPreferencesKey("sub_edge")
        val VOD_AUDIO_LANGUAGE = stringPreferencesKey("vod_audio_language")
        val VOD_AUDIO_LABEL = stringPreferencesKey("vod_audio_label")
        val VOD_SUBTITLES_ENABLED = booleanPreferencesKey("vod_subtitles_enabled")
        val VOD_SUBTITLE_LANGUAGE = stringPreferencesKey("vod_subtitle_language")
        val VOD_SUBTITLE_LABEL = stringPreferencesKey("vod_subtitle_label")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_SYNC_DURATION = longPreferencesKey("last_sync_duration")
        val LAST_SYNC_SUCCESS = booleanPreferencesKey("last_sync_success")
        val LAST_SYNC_TRIGGER = stringPreferencesKey("last_sync_trigger")
        val LAST_SYNC_PLAYLIST_TYPE = stringPreferencesKey("last_sync_playlist_type")
        val LAST_SYNC_LIVE = intPreferencesKey("last_sync_live")
        val LAST_SYNC_MOVIES = intPreferencesKey("last_sync_movies")
        val LAST_SYNC_SERIES = intPreferencesKey("last_sync_series")
        val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    suspend fun deviceId(): String? = context.dataStore.data.first()[Keys.DEVICE_ID]

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { it[Keys.DEVICE_ID] = id }
    }

    suspend fun deviceMac(): String? = context.dataStore.data.first()[Keys.DEVICE_MAC]

    suspend fun setDeviceMac(mac: String) {
        context.dataStore.edit { it[Keys.DEVICE_MAC] = mac }
    }

    suspend fun deviceKey(): String? = context.dataStore.data.first()[Keys.DEVICE_KEY]

    suspend fun setDeviceKey(key: String) {
        context.dataStore.edit { it[Keys.DEVICE_KEY] = key }
    }

    val uiMode: Flow<UiModePreference> = context.dataStore.data
        .map { it[Keys.UI_MODE].toEnum(UiModePreference.AUTO) }
        .distinctUntilChanged()

    suspend fun setUiMode(mode: UiModePreference) {
        context.dataStore.edit { it[Keys.UI_MODE] = mode.name }
    }

    val liveFormat: Flow<LiveFormat> = context.dataStore.data
        .map { it[Keys.LIVE_FORMAT].toEnum(LiveFormat.AUTO) }
        .distinctUntilChanged()

    suspend fun setLiveFormat(format: LiveFormat) {
        context.dataStore.edit { it[Keys.LIVE_FORMAT] = format.name }
    }

    val backgroundSyncMode: Flow<BackgroundSyncMode> = context.dataStore.data
        .map { it[Keys.BACKGROUND_SYNC_MODE].toEnum(BackgroundSyncMode.DAILY) }
        .distinctUntilChanged()

    suspend fun setBackgroundSyncMode(mode: BackgroundSyncMode) {
        context.dataStore.edit { it[Keys.BACKGROUND_SYNC_MODE] = mode.name }
    }

    val subtitleStyle: Flow<SubtitleStyle> = context.dataStore.data
        .map { prefs ->
            SubtitleStyle(
                size = prefs[Keys.SUB_SIZE].toEnum(SubtitleSize.MEDIUM),
                color = prefs[Keys.SUB_COLOR].toEnum(SubtitleColor.WHITE),
                background = prefs[Keys.SUB_BACKGROUND].toEnum(SubtitleBackground.SEMI),
                edge = prefs[Keys.SUB_EDGE].toEnum(SubtitleEdge.OUTLINE),
            )
        }
        .distinctUntilChanged()

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        context.dataStore.edit {
            it[Keys.SUB_SIZE] = style.size.name
            it[Keys.SUB_COLOR] = style.color.name
            it[Keys.SUB_BACKGROUND] = style.background.name
            it[Keys.SUB_EDGE] = style.edge.name
        }
    }

    val playbackTrackPreferences: Flow<PlaybackTrackPreferences> = context.dataStore.data
        .map { prefs ->
            PlaybackTrackPreferences(
                audioLanguage = prefs[Keys.VOD_AUDIO_LANGUAGE],
                audioLabel = prefs[Keys.VOD_AUDIO_LABEL],
                subtitlesEnabled = prefs[Keys.VOD_SUBTITLES_ENABLED] ?: true,
                subtitleLanguage = prefs[Keys.VOD_SUBTITLE_LANGUAGE],
                subtitleLabel = prefs[Keys.VOD_SUBTITLE_LABEL],
            )
        }
        .distinctUntilChanged()

    suspend fun setAudioTrackPreference(language: String?, label: String?) {
        context.dataStore.edit { prefs ->
            language.storeOrRemove(prefs, Keys.VOD_AUDIO_LANGUAGE)
            label.storeOrRemove(prefs, Keys.VOD_AUDIO_LABEL)
        }
    }

    suspend fun setSubtitleTrackPreference(enabled: Boolean, language: String?, label: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VOD_SUBTITLES_ENABLED] = enabled
            language.storeOrRemove(prefs, Keys.VOD_SUBTITLE_LANGUAGE)
            label.storeOrRemove(prefs, Keys.VOD_SUBTITLE_LABEL)
        }
    }

    val lastSyncSummary: Flow<LastSyncSummary> = context.dataStore.data
        .map { prefs ->
            LastSyncSummary(
                completedAtEpochMs = prefs[Keys.LAST_SYNC_AT] ?: 0L,
                durationMs = prefs[Keys.LAST_SYNC_DURATION] ?: 0L,
                successful = prefs[Keys.LAST_SYNC_SUCCESS] ?: false,
                trigger = prefs[Keys.LAST_SYNC_TRIGGER].orEmpty(),
                playlistType = prefs[Keys.LAST_SYNC_PLAYLIST_TYPE].orEmpty(),
                liveChannels = prefs[Keys.LAST_SYNC_LIVE] ?: 0,
                movies = prefs[Keys.LAST_SYNC_MOVIES] ?: 0,
                series = prefs[Keys.LAST_SYNC_SERIES] ?: 0,
                error = prefs[Keys.LAST_SYNC_ERROR],
            )
        }
        .distinctUntilChanged()

    suspend fun recordSyncSummary(summary: LastSyncSummary) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_AT] = summary.completedAtEpochMs
            prefs[Keys.LAST_SYNC_DURATION] = summary.durationMs
            prefs[Keys.LAST_SYNC_SUCCESS] = summary.successful
            prefs[Keys.LAST_SYNC_TRIGGER] = summary.trigger
            prefs[Keys.LAST_SYNC_PLAYLIST_TYPE] = summary.playlistType
            prefs[Keys.LAST_SYNC_LIVE] = summary.liveChannels
            prefs[Keys.LAST_SYNC_MOVIES] = summary.movies
            prefs[Keys.LAST_SYNC_SERIES] = summary.series
            summary.error.storeOrRemove(prefs, Keys.LAST_SYNC_ERROR)
        }
    }

    private fun String?.storeOrRemove(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
    ) {
        val clean = this?.trim()?.takeIf { it.isNotEmpty() }
        if (clean == null) prefs.remove(key) else prefs[key] = clean
    }
}
