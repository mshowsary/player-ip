package com.novaplay.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.data.prefs.LiveFormat
import com.novaplay.tv.data.prefs.SubtitleBackground
import com.novaplay.tv.data.prefs.SubtitleColor
import com.novaplay.tv.data.prefs.SubtitleEdge
import com.novaplay.tv.data.prefs.SubtitleSize
import com.novaplay.tv.data.prefs.SubtitleStyle
import com.novaplay.tv.data.prefs.UiModePreference
import com.novaplay.tv.data.repo.DebugManagedPolicyPreset
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedAccessState
import com.novaplay.tv.data.repo.ManagedFeature
import com.novaplay.tv.data.repo.SyncStatus
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding

/**
 * Settings choices deliberately do not scale or glow. A stable two-pixel focus
 * outline is enough for TV navigation and cannot grow into adjacent options.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PolishedSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiMode by viewModel.uiMode.collectAsStateWithLifecycle()
    val subtitleStyle by viewModel.subtitleStyle.collectAsStateWithLifecycle()
    val liveFormat by viewModel.liveFormat.collectAsStateWithLifecycle()
    val playerGestures by viewModel.playerGesturesEnabled.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceInfo.collectAsStateWithLifecycle()
    val cacheCleared by viewModel.cacheCleared.collectAsStateWithLifecycle()
    val managedAccess by viewModel.managedAccess.collectAsStateWithLifecycle()
    val managedRefreshMessage by viewModel.managedRefreshMessage.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    val compact = isCompactWidth()
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
            text = "Playback, managed access, interface, subtitles, storage and device information",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        if (compact) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.weight(1f),
            ) {
                item { InterfacePanel(uiMode, firstFocus, viewModel::setUiMode) }
                item {
                    ManagedAccessPanel(
                        policy = managedAccess,
                        refreshMessage = managedRefreshMessage,
                        onRefresh = viewModel::refreshManagedAccess,
                        onDebugPreset = viewModel::setDebugManagedPolicy,
                    )
                }
                item {
                    PlaybackPanel(
                        format = liveFormat,
                        gesturesEnabled = playerGestures,
                        showGestureChoice = !isTv,
                        onSelect = viewModel::setLiveFormat,
                        onSetGestures = viewModel::setPlayerGesturesEnabled,
                    )
                }
                item {
                    MaintenancePanel(
                        syncStatus,
                        cacheCleared,
                        viewModel::resyncNow,
                        viewModel::clearImageCache,
                    )
                }
                item {
                    SubtitlePanel(
                        subtitleStyle,
                        viewModel::setSubtitleSize,
                        viewModel::setSubtitleColor,
                        viewModel::setSubtitleBackground,
                        viewModel::setSubtitleEdge,
                    )
                }
                item { DevicePanel(deviceInfo) }
            }
        } else {
            Row(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    item { InterfacePanel(uiMode, firstFocus, viewModel::setUiMode) }
                    item {
                        ManagedAccessPanel(
                            policy = managedAccess,
                            refreshMessage = managedRefreshMessage,
                            onRefresh = viewModel::refreshManagedAccess,
                            onDebugPreset = viewModel::setDebugManagedPolicy,
                        )
                    }
                    item {
                        PlaybackPanel(
                            format = liveFormat,
                            gesturesEnabled = playerGestures,
                            showGestureChoice = !isTv,
                            onSelect = viewModel::setLiveFormat,
                            onSetGestures = viewModel::setPlayerGesturesEnabled,
                        )
                    }
                    item {
                        MaintenancePanel(
                            syncStatus,
                            cacheCleared,
                            viewModel::resyncNow,
                            viewModel::clearImageCache,
                        )
                    }
                    item { DevicePanel(deviceInfo) }
                }

                Spacer(Modifier.width(24.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                ) {
                    item {
                        SubtitlePanel(
                            subtitleStyle,
                            viewModel::setSubtitleSize,
                            viewModel::setSubtitleColor,
                            viewModel::setSubtitleBackground,
                            viewModel::setSubtitleEdge,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Panel for overriding the auto/touch/TV interface mode, with a hint line describing
 * the active mode. Its first choice carries the screen's initial focus requester.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterfacePanel(
    mode: UiModePreference,
    firstFocus: FocusRequester,
    onSelect: (UiModePreference) -> Unit,
) {
    SettingsPanel(
        title = "Interface",
        description = "Auto detects touch devices and TV remotes. Override it only when a TV box is identified incorrectly.",
    ) {
        ChoiceGroup(
            label = "Interface mode",
            options = UiModePreference.entries.map { it.label },
            selectedIndex = mode.ordinal,
            onSelect = { onSelect(UiModePreference.entries[it]) },
            firstFocus = firstFocus,
        )
        Text(
            text = when (mode) {
                UiModePreference.AUTO -> "Using automatic device and window detection"
                UiModePreference.TOUCH -> "Touch navigation is forced on this device"
                UiModePreference.TV -> "Remote-first TV navigation is forced on this device"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Panel showing the provider-assigned access policy: state, per-feature availability,
 * policy revision, support code and a refresh action. Debug builds additionally expose
 * a preset picker for previewing policy states; release builds only receive the portal policy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManagedAccessPanel(
    policy: ManagedAccessPolicy,
    refreshMessage: String?,
    onRefresh: () -> Unit,
    onDebugPreset: (DebugManagedPolicyPreset) -> Unit,
) {
    SettingsPanel(
        title = "Managed access",
        description = if (policy.isManaged) {
            "Service access assigned to this device by the provider portal."
        } else {
            "No managed service policy is currently attached to this installation."
        },
    ) {
        Text(
            text = policy.statusLabel(),
            style = MaterialTheme.typography.titleMedium,
            color = when (policy.state) {
                ManagedAccessState.SUSPENDED, ManagedAccessState.REVOKED -> MaterialTheme.colorScheme.error
                ManagedAccessState.ACTIVE -> MaterialTheme.colorScheme.primary
                ManagedAccessState.UNMANAGED -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        policy.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AccessServiceRow("Live TV", policy.allows(ManagedFeature.LIVE))
        AccessServiceRow("Movies", policy.allows(ManagedFeature.MOVIES))
        AccessServiceRow("Series", policy.allows(ManagedFeature.SERIES))
        if (policy.revision > 0L) DeviceInfoRow("Policy revision", policy.revision.toString())
        policy.supportCode?.let { DeviceInfoRow("Support code", it) }
        NovaButton(
            text = refreshMessage ?: "Refresh managed access",
            onClick = onRefresh,
            prominent = refreshMessage == null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (BuildConfig.DEBUG) {
            ChoiceGroup(
                label = "Debug policy preview",
                options = DebugManagedPolicyPreset.entries.map { it.label },
                selectedIndex = debugPresetIndex(policy),
                onSelect = { onDebugPreset(DebugManagedPolicyPreset.entries[it]) },
            )
            Text(
                text = "Debug builds only. Release builds receive this policy from the portal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Maps the current policy back to the closest debug preset ordinal, or -1 when none matches.
private fun debugPresetIndex(policy: ManagedAccessPolicy): Int = when {
    policy.state == ManagedAccessState.UNMANAGED -> DebugManagedPolicyPreset.UNMANAGED.ordinal
    policy.state == ManagedAccessState.SUSPENDED -> DebugManagedPolicyPreset.SUSPENDED.ordinal
    policy.state == ManagedAccessState.REVOKED -> DebugManagedPolicyPreset.REVOKED.ordinal
    policy.allowLive && !policy.allowMovies && !policy.allowSeries -> DebugManagedPolicyPreset.LIVE_ONLY.ordinal
    policy.state == ManagedAccessState.ACTIVE -> DebugManagedPolicyPreset.FULL_ACCESS.ordinal
    else -> -1
}

/** Row for one managed feature: a primary-colored dot and "Available" when allowed, error-colored when blocked. */
@Composable
private fun AccessServiceRow(label: String, allowed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (allowed) "Available" else "Unavailable",
            style = MaterialTheme.typography.labelMedium,
            color = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

/** Panel for picking the preferred live stream format (auto tries HLS, then MPEG-TS). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackPanel(
    format: LiveFormat,
    gesturesEnabled: Boolean,
    showGestureChoice: Boolean,
    onSelect: (LiveFormat) -> Unit,
    onSetGestures: (Boolean) -> Unit,
) {
    SettingsPanel(
        title = "Live playback",
        description = "Auto tries HLS first and falls back to MPEG-TS when necessary.",
    ) {
        ChoiceGroup(
            label = "Preferred stream format",
            options = LiveFormat.entries.map { it.label },
            selectedIndex = format.ordinal,
            onSelect = { onSelect(LiveFormat.entries[it]) },
        )
        // Touch-only: TV playback is remote-driven, so the choice is hidden there.
        if (showGestureChoice) {
            ChoiceGroup(
                label = "Slide gestures (volume, brightness, channel swipe)",
                options = listOf("On", "Off"),
                selectedIndex = if (gesturesEnabled) 0 else 1,
                onSelect = { onSetGestures(it == 0) },
            )
        }
    }
}

/**
 * Panel with re-sync and clear-image-cache actions. The sync button reflects live
 * progress and the failure message is surfaced inline when a sync fails.
 */
@Composable
private fun MaintenancePanel(
    status: SyncStatus,
    cacheCleared: Boolean,
    onSync: () -> Unit,
    onClearCache: () -> Unit,
) {
    SettingsPanel(
        title = "Storage and synchronization",
        description = "Refresh the active catalogue or clear downloaded artwork without deleting playlists.",
    ) {
        NovaButton(
            text = when (status) {
                is SyncStatus.Syncing -> "Syncing ${status.step}…"
                else -> "Re-sync active playlist"
            },
            onClick = onSync,
            prominent = status !is SyncStatus.Syncing,
            modifier = Modifier.fillMaxWidth(),
        )
        if (status is SyncStatus.Failed) {
            Text(
                text = status.message,
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
 * Panel grouping the subtitle style choices (size, color, background, edge) below a
 * live preview. Every selection is persisted immediately and applied to VOD playback.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitlePanel(
    style: SubtitleStyle,
    onSize: (SubtitleSize) -> Unit,
    onColor: (SubtitleColor) -> Unit,
    onBackground: (SubtitleBackground) -> Unit,
    onEdge: (SubtitleEdge) -> Unit,
) {
    SettingsPanel(
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

/** Panel listing the device identifiers (MAC, device key, app version) used for portal activation and support. */
@Composable
private fun DevicePanel(info: DeviceInfo) {
    SettingsPanel(
        title = "This device",
        description = "These identifiers are used for managed portal activation and support.",
    ) {
        DeviceInfoRow("MAC address", info.mac)
        DeviceInfoRow("Device key", info.deviceKey)
        DeviceInfoRow("App version", info.appVersion)
    }
}

/** Bordered surface container with a title and description above the slot content, shared by all panels on this screen. */
@Composable
private fun SettingsPanel(
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
 * Labelled single-select group rendered as a wrapping row of [StableChoice] pills.
 * When [firstFocus] is provided it is attached to the first option so the screen
 * can direct initial D-pad focus there.
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEachIndexed { index, option ->
                StableChoice(
                    text = option,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    focusRequester = firstFocus.takeIf { index == 0 },
                )
            }
        }
    }
}

/**
 * Radio-style choice pill with a stable outline that thickens on focus instead of
 * scaling or glowing (see the screen KDoc). Clicking also requests focus to keep
 * touch selection and D-pad position in sync.
 */
@Composable
private fun StableChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val localFocusRequester = remember { FocusRequester() }
    val requester = focusRequester ?: localFocusRequester
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
            overflow = TextOverflow.Ellipsis,
        )
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

/** Two-column label/value row; blank values render as an em dash and long values are ellipsized. */
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f),
        )
    }
}
