package com.novaplay.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyListScope
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
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

    LaunchedEffect(isTv) { if (isTv) firstFocus.requestFocus() }

    if (isCompactWidth()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPadding()),
        ) {
            subtitleSection(subtitleStyle, firstFocus, viewModel)
            item { Spacer(Modifier.height(10.dp)) }
            generalSection(uiMode, liveFormat, syncStatus, deviceInfo, cacheCleared, viewModel)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPadding()),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .focusRestorer(),
            ) {
                subtitleSection(subtitleStyle, firstFocus, viewModel)
            }

            Spacer(Modifier.width(48.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusRestorer(),
            ) {
                generalSection(uiMode, liveFormat, syncStatus, deviceInfo, cacheCleared, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun LazyListScope.subtitleSection(
    subtitleStyle: SubtitleStyle,
    firstFocus: FocusRequester,
    viewModel: SettingsViewModel,
) {
    item {
        Text(
            text = "Subtitle appearance",
            style = MaterialTheme.typography.headlineMedium,
        )
    }
    item { SubtitlePreview(style = subtitleStyle) }
    item {
        ChoiceRow(
            label = "Text size",
            options = SubtitleSize.entries.map { it.label },
            selectedIndex = subtitleStyle.size.ordinal,
            onSelect = { viewModel.setSubtitleSize(SubtitleSize.entries[it]) },
            firstFocus = firstFocus,
        )
    }
    item {
        ChoiceRow(
            label = "Text color",
            options = SubtitleColor.entries.map { it.label },
            selectedIndex = subtitleStyle.color.ordinal,
            onSelect = { viewModel.setSubtitleColor(SubtitleColor.entries[it]) },
        )
    }
    item {
        ChoiceRow(
            label = "Background",
            options = SubtitleBackground.entries.map { it.label },
            selectedIndex = subtitleStyle.background.ordinal,
            onSelect = { viewModel.setSubtitleBackground(SubtitleBackground.entries[it]) },
        )
    }
    item {
        ChoiceRow(
            label = "Edge style",
            options = SubtitleEdge.entries.map { it.label },
            selectedIndex = subtitleStyle.edge.ordinal,
            onSelect = { viewModel.setSubtitleEdge(SubtitleEdge.entries[it]) },
        )
    }
}

private fun LazyListScope.generalSection(
    uiMode: UiModePreference,
    liveFormat: LiveFormat,
    syncStatus: SyncStatus,
    deviceInfo: DeviceInfo,
    cacheCleared: Boolean,
    viewModel: SettingsViewModel,
) {
    item {
        Text(
            text = "General",
            style = MaterialTheme.typography.headlineMedium,
        )
    }
    item {
        ChoiceRow(
            label = "Interface mode",
            options = UiModePreference.entries.map { it.label },
            selectedIndex = uiMode.ordinal,
            onSelect = { viewModel.setUiMode(UiModePreference.entries[it]) },
        )
        Text(
            text = "Auto detects phones, tablets, Android TV, Fire TV, and TV boxes. Use TV / remote when a box is detected incorrectly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    item {
        ChoiceRow(
            label = "Live stream format",
            options = LiveFormat.entries.map { it.label },
            selectedIndex = liveFormat.ordinal,
            onSelect = { viewModel.setLiveFormat(LiveFormat.entries[it]) },
            vertical = true,
        )
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NovaButton(
                text = if (cacheCleared) "Image cache cleared ✓" else "Clear image cache",
                onClick = viewModel::clearImageCache,
            )
        }
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NovaButton(text = "Re-sync content now", onClick = viewModel::resyncNow)
            Spacer(Modifier.width(14.dp))
            when (val status = syncStatus) {
                is SyncStatus.Syncing -> Text(
                    text = "Syncing ${status.step}…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is SyncStatus.Failed -> Text(
                    text = status.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                SyncStatus.Idle -> Unit
            }
        }
    }
    item {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Text(
                text = "Device",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoRow("MAC address", deviceInfo.mac)
            InfoRow("Device key", deviceInfo.deviceKey)
            InfoRow("App version", deviceInfo.appVersion)
        }
    }
}

@Composable
private fun SubtitlePreview(style: SubtitleStyle) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2A3550), Color(0xFF0E1524), Color(0xFF1B2439)),
                ),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val textSize = with(LocalDensity.current) {
            (style.size.fraction * 720f * 0.8f).toSp()
        }
        val baseStyle = TextStyle(
            color = Color(style.color.argb),
            fontSize = textSize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            shadow = when (style.edge) {
                SubtitleEdge.DROP_SHADOW -> Shadow(
                    color = Color.Black,
                    offset = Offset(3f, 3f),
                    blurRadius = 6f,
                )
                else -> null
            },
        )
        Box(modifier = Modifier.padding(bottom = 18.dp)) {
            if (style.edge == SubtitleEdge.OUTLINE) {
                Text(
                    text = "Sample subtitle text",
                    style = baseStyle.copy(
                        color = Color.Black,
                        drawStyle = Stroke(width = 6f),
                    ),
                    modifier = Modifier.background(Color.Transparent),
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    firstFocus: FocusRequester? = null,
    vertical: Boolean = false,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val chips: @Composable () -> Unit = {
            options.forEachIndexed { index, option ->
                ChoiceChip(
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
        if (vertical) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { chips() }
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onClick,
        modifier = modifier.then(
            if (selected) {
                Modifier.border(1.dp, NovaAccentGradient, RoundedCornerShape(50))
            } else {
                Modifier
            },
        ),
        shape = RoundedCornerShape(50),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        focusedScale = 1.05f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
