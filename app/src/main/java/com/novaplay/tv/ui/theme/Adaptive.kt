package com.novaplay.tv.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

// TV = leanback UI mode; everything else (phone, tablet, Chromebook) is
// treated as a touch device: no auto-focus grabbing, tighter margins.
@Composable
fun isTvDevice(): Boolean =
    (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION

// Single-pane layouts below this width (portrait phones). Side rails and
// two-column screens collapse into stacked layouts.
@Composable
fun isCompactWidth(): Boolean = LocalConfiguration.current.screenWidthDp < 600

// TV needs overscan-safe margins; touch screens want tighter edges.
@Composable
fun screenPadding(): PaddingValues =
    if (isTvDevice()) {
        PaddingValues(horizontal = OverscanHorizontal, vertical = OverscanVertical)
    } else {
        PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    }
