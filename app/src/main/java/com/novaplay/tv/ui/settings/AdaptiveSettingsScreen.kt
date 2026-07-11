package com.novaplay.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.data.prefs.LiveFormat
import com.novaplay.tv.data.prefs.SubtitleBackground
import com.novaplay.tv.data.prefs.SubtitleColor
import com.novaplay.tv.data.prefs.SubtitleEdge
import com.novaplay.tv.data.prefs.SubtitleSize
import com.novaplay.tv.data.prefs.SubtitleStyle
import com.novaplay.tv.data.prefs.UiModePreference
import com.novaplay.tv.data.repo.SyncStatus
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.theme.NovaAccentGradient
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding

/**
 * Adaptive settings screen that stacks the setting cards in a single column on
 * compact widths and splits them into two columns otherwise. On TV, initial focus
 * lands on the first interface-mode pill and each column restores focus on re-entry.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun AdaptiveSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiMode by viewModel.uiMode.collectAsStateWithLifecycle()
    val subtitleStyle by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val liveFormat by viewModel.liveFormat.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val cacheCleared by viewModel.cacheCleared.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    val isTv = isTvDevice()

    LaunchedEffect(isTv) {
        if (isTv) runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(screenPadding()),
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Playback, interface, subtitles, storage and device information",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        if (isCompactWidth()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer(),
            ) {
                item {
                    InterfaceCard(
                        uiMode = uiMode,
                        firstFocus = firstFocus,
                        onSelect = viewModel::setUiMode,
                    )
                }
                item { PlaybackCard(liveFormat, viewModel::setLiveFormat) }
                item {
                    MaintenanceCard(
                        syncStatus = syncStatus,
                        cacheCleared = cacheCleared,
                        onSync = viewModel::resyncNow,
                        onClearCache = viewModel::clearImageCache,
                    )
                }
                item {
                    SubtitleCard(
                        style = subtitleStyle,
                        onSize = viewModel::setSubtitleSize,
                        onColor = viewModel::setSubtitleColor,
                        onBackground = viewModel::setSubtitleBackground,
                        onEdge = viewModel::setSubtitleEdge,
                    )
                }
                item { DeviceCard(deviceInfo) }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRestorer(),
                ) {
                    item {
                        InterfaceCard(
                            uiMode = uiMode,
                            firstFocus = firstFocus,
                            onSelect = viewModel::setUiMode,
                        )
                    }
                    item { PlaybackCard(liveFormat, viewModel::setLiveFormat) }
                    item {
                        MaintenanceCard(
                            syncStatus = syncStatus,
                            cacheCleared = cacheCleared,
                            onSync = viewModel::resyncNow,
                            onClearCache = viewModel::clearImageCache,
                        )
                    }
                    item { DeviceCard(deviceInfo) }
                }
                Spacer(Modifier.width(24.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .focusRestorer(),
                ) {
                    item {
                        SubtitleCard(
                            style = subtitleStyle,
                            onSize = viewModel::setSubtitleSize,
                            onColor = viewModel::setSubtitleColor,
                            onBackground = viewModel::setSubtitleBackground,
                            onEdge = viewModel::setSubtitleEdge,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card for overriding the auto/touch/TV interface mode, with a hint line describing
 * the active mode. Its first pill carries the screen's initial focus requester.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterfaceCard(
    uiMode: UiModePreference,
    firstFocus: FocusRequester,
    onSelect: (UiModePreference) -> Unit,
) {
    SettingsCard(
        title = "Interface",
        description = "Auto detects touch devices and TV remotes. Override it only when a TV box is identified incorrectly.",
    ) {
        ChoiceGroup(
            label = "Interface mode",
            options = UiModePreference.entries.map { it.label },
            selectedIndex = uiMode.ordinal,
            onSelect = { onSelect(UiModePreference.entries[it]) },
            firstFocus = firstFocus,
        )
        Text(
            text = when (uiMode) {
                UiModePreference.AUTO -> "Using automatic device and window detection"
                UiModePreference.TOUCH -> "Touch navigation is forced on this device"
                UiModePreference.TV -> "Remote-first TV navigation is forced on this device"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Card for picking the preferred live stream format (auto tries HLS, then MPEG-TS). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackCard(
    liveFormat: LiveFormat,
    onSelect: (LiveFormat) -> Unit,
) {
    SettingsCard(
        title = "Live playback",
        description = "Auto tries HLS first and falls back to transport stream when necessary.",
    ) {
        ChoiceGroup(
            label = "Preferred stream format",
            options = LiveFormat.entries.map { it.label },
            selectedIndex = liveFormat.ordinal,
            onSelect = { onSelect(LiveFormat.entries[it]) },
        )
    }
}

/**
 * Card with re-sync and clear-image-cache actions. The sync button reflects live
 * progress and the failure message is surfaced inline when a sync fails.
 */
@Composable
private fun MaintenanceCard(
    syncStatus: SyncStatus,
    cacheCleared: Boolean,
    onSync: () -> Unit,
    onClearCache: () -> Unit,
) {
    SettingsCard(
        title = "Storage and synchronization",
        description = "Refresh the active catalogue or clear downloaded artwork without deleting playlists.",
    ) {
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
    }
}

/**
 * Card grouping the subtitle style choices (size, color, background, edge) below a
 * live preview. Every selection is persisted immediately via the callbacks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleCard(
    style: SubtitleStyle,
    onSize: (SubtitleSize) -> Unit,
    onColor: (SubtitleColor) -> Unit,
    onBackground: (SubtitleBackground) -> Unit,
    onEdge: (SubtitleEdge) -> Unit,
) {
    SettingsCard(
        title = "Subtitle appearance",
        description = "Changes are saved immediately and applied to VOD playback.",
    ) {
        SubtitlePreview(style)
        ChoiceGroup(
            label = "Text size",
            options = SubtitleSize.entries.map { it.label },
            selectedIndex = style.size.ordinal,
            onSelect = { onSize(SubtitleSize.entries[it]) },
        )
        ChoiceGroup(
            label = "Text color",
            options = SubtitleColor.entries.map { it.label },
            selectedIndex = style.color.ordinal,
            onSelect = { onColor(SubtitleColor.entries[it]) },
        )
        ChoiceGroup(
            label = "Background",
            options = SubtitleBackground.entries.map { it.label },
            selectedIndex = style.background.ordinal,
            onSelect = { onBackground(SubtitleBackground.entries[it]) },
        )
        ChoiceGroup(
            label = "Edge style",
            options = SubtitleEdge.entries.map { it.label },
            selectedIndex = style.edge.ordinal,
            onSelect = { onEdge(SubtitleEdge.entries[it]) },
        )
    }
}

/** Card listing the device identifiers (MAC, device key, app version) used for portal activation and support. */
@Composable
private fun DeviceCard(info: DeviceInfo) {
    SettingsCard(
        title = "This device",
        description = "These identifiers are used for managed portal activation and support.",
    ) {
        DeviceInfoRow("MAC address", info.mac)
        DeviceInfoRow("Device key", info.deviceKey)
        DeviceInfoRow("App version", info.appVersion)
    }
}

/** Bordered surface container with a title and description above the slot content, shared by all cards on this screen. */
@Composable
private fun SettingsCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
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
            .padding(18.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/**
 * Labelled single-select group rendered as a wrapping row of pills. When [firstFocus]
 * is provided it is attached to the first pill so the screen can direct initial
 * D-pad focus there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceGroup(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, option ->
                ChoicePill(
                    text = option,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = if (index == 0 && firstFocus != null) {
                        Modifier.focusRequester(firstFocus)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/**
 * Focusable pill for one option. Selection shows an accent-gradient border and a
 * leading dot; D-pad focus scales the pill slightly via NovaClickable.
 */
@Composable
private fun ChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
        focusedScale = 1.04f,
        modifier = modifier
            .height(48.dp)
            .then(if (selected) Modifier.border(1.dp, NovaAccentGradient, RoundedCornerShape(50)) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Renders a sample subtitle line over a dark gradient so style changes preview
 * instantly. Text size is derived from a 720p reference height, and the OUTLINE
 * edge is simulated by a stroked black copy drawn beneath the text.
 */
@Composable
private fun SubtitlePreview(style: SubtitleStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2A3550), Color(0xFF0E1524), Color(0xFF1B2439)),
                ),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val textSize = with(LocalDensity.current) { (style.size.fraction * 720f * 0.8f).toSp() }
        val baseStyle = TextStyle(
            color = Color(style.color.argb),
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            shadow = when (style.edge) {
                SubtitleEdge.DROP_SHADOW -> Shadow(Color.Black, Offset(3f, 3f), 6f)
                else -> null
            },
        )
        Box(modifier = Modifier.padding(bottom = 18.dp)) {
            if (style.edge == SubtitleEdge.OUTLINE) {
                Text(
                    text = "Sample subtitle text",
                    style = baseStyle.copy(color = Color.Black, drawStyle = Stroke(width = 6f)),
                )
            }
            Text(
                text = "Sample subtitle text",
                style = baseStyle,
                modifier = Modifier.background(Color(style.background.argb)),
            )
        }
    }
}

/** Two-column label/value row; blank values render as an em dash. */
@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f),
        )
    }
}
