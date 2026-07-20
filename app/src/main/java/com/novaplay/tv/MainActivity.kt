package com.novaplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novaplay.tv.data.prefs.AccentTheme
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.TimeFormatPreference
import com.novaplay.tv.data.prefs.UiModePreference
import com.novaplay.tv.ui.navigation.NovaNavGraph
import com.novaplay.tv.ui.theme.NovaPlayTheme
import com.novaplay.tv.ui.theme.ProvideAdaptiveEnvironment
import com.novaplay.tv.ui.theme.ProvideTimeFormat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host: the whole UI lives in Compose behind [NovaNavGraph]. Also owns
 * system-bar visibility so immersive playback survives focus loss to system dialogs.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    private var immersiveNow: Boolean = false

    /**
     * Builds the Compose tree, threading the persisted UI-mode preference into the
     * adaptive (touch vs. TV/remote) environment so a settings change restyles live.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiMode = appPreferences.uiMode.collectAsStateWithLifecycle(
                initialValue = UiModePreference.AUTO,
            ).value
            val accentTheme = appPreferences.accentTheme.collectAsStateWithLifecycle(
                initialValue = AccentTheme.BRAND,
            ).value
            val timeFormat = appPreferences.timeFormat.collectAsStateWithLifecycle(
                initialValue = TimeFormatPreference.AUTO,
            ).value

            ProvideAdaptiveEnvironment(preference = uiMode) {
                ProvideTimeFormat(preference = timeFormat) {
                    NovaPlayTheme(accentTheme = accentTheme) {
                        NovaNavGraph(onImmersiveChanged = ::updateImmersiveMode)
                    }
                }
            }
        }
    }

    /**
     * Re-hides the system bars when focus returns mid-playback — the system restores
     * them whenever a dialog or the notification shade steals window focus.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && immersiveNow) applySystemBars(hidden = true)
    }

    // Remembers the desired state so onWindowFocusChanged can restore it later.
    private fun updateImmersiveMode(hidden: Boolean) {
        immersiveNow = hidden
        applySystemBars(hidden)
    }

    // Transient-by-swipe behavior keeps hidden bars retrievable on touch devices.
    private fun applySystemBars(hidden: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !hidden)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
