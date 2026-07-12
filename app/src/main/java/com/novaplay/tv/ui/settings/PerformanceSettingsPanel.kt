package com.novaplay.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.data.prefs.BackgroundSyncMode
import com.novaplay.tv.data.prefs.LastSyncSummary
import com.novaplay.tv.data.repo.AppDiagnostics
import com.novaplay.tv.data.repo.AppDiagnosticsRepository
import com.novaplay.tv.data.repo.SyncStatus
import com.novaplay.tv.ui.components.NovaButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerformanceSettingsPanel(
    syncStatus: SyncStatus,
    cacheCleared: Boolean,
    backgroundMode: BackgroundSyncMode,
    lastSync: LastSyncSummary,
    diagnostics: AppDiagnostics,
    message: String?,
    onBackgroundMode: (BackgroundSyncMode) -> Unit,
    onSync: () -> Unit,
    onClearCache: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    firstFocusRequester: FocusRequester? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compactContent = maxWidth < 520.dp

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.border.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(if (compactContent) 14.dp else 18.dp),
        ) {
            Text(
                text = "Keep the active catalogue fresh without interrupting browsing, and create privacy-safe support information.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Automatic catalogue refresh",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackgroundSyncMode.entries.forEachIndexed { index, mode ->
                    PerformanceChoice(
                        text = mode.label,
                        selected = mode == backgroundMode,
                        onClick = { onBackgroundMode(mode) },
                        focusRequester = firstFocusRequester.takeIf { index == 0 },
                    )
                }
            }
            Text(
                text = backgroundMode.description + if (backgroundMode == BackgroundSyncMode.OFF) {
                    "."
                } else {
                    ". Android may delay it to protect battery, storage and network stability."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            if (lastSync.exists) {
                val result = if (lastSync.successful) "Successful" else "Failed"
                DiagnosticRow(
                    label = "Last refresh",
                    value = "$result · ${lastSync.trigger.ifBlank { "Unknown" }}",
                    stacked = compactContent,
                )
                DiagnosticRow(
                    label = "Completed",
                    value = AppDiagnosticsRepository.formatDateTime(lastSync.completedAtEpochMs),
                    stacked = compactContent,
                )
                DiagnosticRow(
                    label = "Duration",
                    value = AppDiagnosticsRepository.formatDuration(lastSync.durationMs),
                    stacked = compactContent,
                )
                DiagnosticRow(
                    label = "Installed catalogue",
                    value = "${lastSync.liveChannels} live · ${lastSync.movies} movies · ${lastSync.series} series",
                    stacked = compactContent,
                )
                lastSync.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Text(
                    text = "No refresh report has been recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NovaButton(
                text = when (syncStatus) {
                    is SyncStatus.Syncing -> "Syncing ${syncStatus.step}…"
                    else -> "Re-sync active playlist"
                },
                onClick = onSync,
                prominent = syncStatus !is SyncStatus.Syncing,
                modifier = Modifier.fillMaxWidth(),
            )
            if (syncStatus is SyncStatus.Failed) {
                Text(
                    text = syncStatus.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            NovaButton(
                text = if (cacheCleared) "Image cache cleared ✓" else "Clear image cache",
                onClick = onClearCache,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Build and security",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiagnosticRow("Build channel", diagnostics.buildChannel, compactContent)
            DiagnosticRow("Portal control plane", diagnostics.portalSecurityStatus, compactContent)
            DiagnosticRow("Application-data backup", diagnostics.backupStatus, compactContent)
            DiagnosticRow(
                label = "HTTP playlists",
                value = if (diagnostics.httpPlaylistCompatibility) {
                    "Supported for stream compatibility · portal remains HTTPS-only"
                } else {
                    "Disabled"
                },
                stacked = compactContent,
            )

            Text(
                text = "Device health",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiagnosticRow("Network", diagnostics.networkLabel, compactContent)
            DiagnosticRow(
                label = "Memory",
                value = "${diagnostics.memoryClassMb} MB" +
                    if (diagnostics.lowRamDevice) " · low-RAM mode" else "",
                stacked = compactContent,
            )
            DiagnosticRow(
                "Database",
                AppDiagnosticsRepository.formatBytes(diagnostics.databaseBytes),
                compactContent,
            )
            DiagnosticRow(
                "Image cache",
                AppDiagnosticsRepository.formatBytes(diagnostics.imageCacheBytes),
                compactContent,
            )
            DiagnosticRow(
                "Temporary snapshots",
                AppDiagnosticsRepository.formatBytes(diagnostics.snapshotCacheBytes),
                compactContent,
            )
            DiagnosticRow(
                "Free storage",
                AppDiagnosticsRepository.formatBytes(diagnostics.freeStorageBytes),
                compactContent,
            )
            DiagnosticRow(
                label = "Active catalogue",
                value = "${diagnostics.liveChannels} live · ${diagnostics.movies} movies · ${diagnostics.series} series",
                stacked = compactContent,
            )

            if (compactContent) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NovaButton(
                        text = "Refresh health",
                        onClick = onRefreshDiagnostics,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NovaButton(
                        text = "Copy support info",
                        onClick = onCopyDiagnostics,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NovaButton(
                        text = "Refresh health",
                        onClick = onRefreshDiagnostics,
                        modifier = Modifier.weight(1f),
                    )
                    NovaButton(
                        text = "Copy support info",
                        onClick = onCopyDiagnostics,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = message ?: "Support info excludes playlist URLs, credentials, tokens and device identifiers.",
                style = MaterialTheme.typography.bodySmall,
                color = if (message == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, stacked: Boolean) {
    if (stacked) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.7f),
            )
        }
    }
}

@Composable
private fun PerformanceChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val localRequester = remember { FocusRequester() }
    val requester = focusRequester ?: localRequester
    val shape = RoundedCornerShape(50)
    val background = when {
        selected -> MaterialTheme.colorScheme.surfaceVariant
        focused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.background
    }
    val outline = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
        else -> MaterialTheme.colorScheme.border.copy(alpha = 0.62f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 1.dp, outline, shape)
            .clickable {
                runCatching { requester.requestFocus() }
                onClick()
            }
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .heightIn(min = 46.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected || focused) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}
