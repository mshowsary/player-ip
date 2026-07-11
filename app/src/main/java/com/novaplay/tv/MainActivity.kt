package com.novaplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.UiModePreference
import com.novaplay.tv.ui.navigation.NovaNavGraph
import com.novaplay.tv.ui.theme.NovaPlayTheme
import com.novaplay.tv.ui.theme.ProvideAdaptiveEnvironment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    private var immersiveNow: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiMode = appPreferences.uiMode.collectAsStateWithLifecycle(
                initialValue = UiModePreference.AUTO,
            ).value

            ProvideAdaptiveEnvironment(preference = uiMode) {
                NovaPlayTheme {
                    NovaNavGraph(onImmersiveChanged = ::updateImmersiveMode)
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && immersiveNow) applySystemBars(hidden = true)
    }

    private fun updateImmersiveMode(hidden: Boolean) {
        immersiveNow = hidden
        applySystemBars(hidden)
    }

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
