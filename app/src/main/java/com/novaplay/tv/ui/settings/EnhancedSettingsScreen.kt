package com.novaplay.tv.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.components.NovaDialog
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding

@Composable
fun EnhancedSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var showPerformance by remember { mutableStateOf(false) }
    val openFocus = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        PolishedSettingsScreen(viewModel = viewModel)
        NovaButton(
            text = "Sync & health",
            onClick = { showPerformance = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(screenPadding())
                .focusRequester(openFocus),
        )
    }

    if (showPerformance) {
        PerformanceSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showPerformance = false },
        )
    }
}

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
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp - 120.dp).coerceAtLeast(260.dp)

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
