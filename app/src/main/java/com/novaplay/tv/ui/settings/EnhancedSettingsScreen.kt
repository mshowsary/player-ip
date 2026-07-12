package com.novaplay.tv.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.components.NovaDialog
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding

/**
 * Settings entry point with a dedicated full-width synchronization and health
 * action below the scrollable settings content. Keeping the action in layout
 * flow prevents it from covering subtitle options or the bottom navigation.
 */
@Composable
fun EnhancedSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var showPerformance by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            PolishedSettingsScreen(viewModel = viewModel)
        }
        NovaButton(
            text = "Synchronization & device health",
            onClick = { showPerformance = true },
            prominent = true,
            modifier = Modifier
                .padding(screenPadding())
                .fillMaxWidth(),
        )
    }

    if (showPerformance) {
        PerformanceSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showPerformance = false },
        )
    }
}

/**
 * Dialog hosting [PerformanceSettingsPanel]. It refreshes diagnostics when
 * opened, uses a responsive height, and moves TV focus to the first sync choice.
 */
@Composable
private fun PerformanceSettingsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val cacheCleared by viewModel.cacheCleared.collectAsStateWithLifecycle()
    val backgroundMode by viewModel.backgroundSyncMode.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSyncSummary.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val message by viewModel.diagnosticsMessage.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    val isTv = isTvDevice()
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp < 600
    val maxHeight = (
        configuration.screenHeightDp * if (compact) 0.72f else 0.76f
    ).dp.coerceAtLeast(220.dp)

    LaunchedEffect(Unit) {
        viewModel.refreshDiagnostics()
        if (isTv) runCatching { firstFocus.requestFocus() }
    }

    NovaDialog(
        title = "Synchronization and device health",
        onDismiss = onDismiss,
        maxWidth = 720.dp,
    ) {
        Box(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            PerformanceSettingsPanel(
                syncStatus = syncStatus,
                cacheCleared = cacheCleared,
                backgroundMode = backgroundMode,
                lastSync = lastSync,
                diagnostics = diagnostics,
                message = message,
                onBackgroundMode = viewModel::setBackgroundSyncMode,
                onSync = viewModel::resyncNow,
                onClearCache = viewModel::clearImageCache,
                onRefreshDiagnostics = viewModel::refreshDiagnostics,
                onCopyDiagnostics = viewModel::copySupportDiagnostics,
                firstFocusRequester = firstFocus,
            )
        }
    }
}
