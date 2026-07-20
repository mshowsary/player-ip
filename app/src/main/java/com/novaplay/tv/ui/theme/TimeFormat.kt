package com.novaplay.tv.ui.theme

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.novaplay.tv.core.TimeFormatPolicy
import com.novaplay.tv.data.prefs.TimeFormatPreference

/** Whether time is rendered 24-hour, resolved once at the app root. */
val LocalUse24Hour = staticCompositionLocalOf { true }

/**
 * Provides the resolved 12/24-hour choice to every clock and programme time
 * in the app; AUTO follows the Android system's 24-hour setting.
 */
@Composable
fun ProvideTimeFormat(preference: TimeFormatPreference, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val use24 = TimeFormatPolicy.use24Hour(preference, DateFormat.is24HourFormat(context))
    CompositionLocalProvider(LocalUse24Hour provides use24, content = content)
}
