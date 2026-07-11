package com.novaplay.tv.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
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
    AUTO("Auto (HLS → TS fallback)"),
    HLS("HLS"),
    TS("MPEG-TS"),
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

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DEVICE_MAC = stringPreferencesKey("device_mac")
        val DEVICE_KEY = stringPreferencesKey("device_key")
        val UI_MODE = stringPreferencesKey("ui_mode")
        val LIVE_FORMAT = stringPreferencesKey("live_format")
        val SUB_SIZE = stringPreferencesKey("sub_size")
        val SUB_COLOR = stringPreferencesKey("sub_color")
        val SUB_BACKGROUND = stringPreferencesKey("sub_background")
        val SUB_EDGE = stringPreferencesKey("sub_edge")
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

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
}
