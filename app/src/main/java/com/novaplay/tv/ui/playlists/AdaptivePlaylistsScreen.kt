package com.novaplay.tv.ui.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.repo.PlaylistDraft
import com.novaplay.tv.data.repo.PortalPairingSession
import com.novaplay.tv.ui.components.AdaptiveFormField
import com.novaplay.tv.ui.components.EmptyState
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.NovaDialog
import com.novaplay.tv.ui.components.QrImage
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Playlist management with direct touch actions on phones/tablets and a compact
 * focus-first action dialog on TV. Both modes share the same validated editor.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun AdaptivePlaylistsScreen(
    onAllPlaylistsRemoved: () -> Unit,
    onPlaylistReady: () -> Unit = {},
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val phoneEntry by viewModel.phoneEntry.collectAsStateWithLifecycle()
    val isTv = isTvDevice()
    val compact = isCompactWidth()

    var actionsFor by remember { mutableStateOf<Playlist?>(null) }
    var confirmRemove by remember { mutableStateOf<Playlist?>(null) }
    var editorDraft by remember { mutableStateOf<PlaylistDraft?>(null) }
    val firstRowFocus = remember { FocusRequester() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importM3u)
    }

    LaunchedEffect(Unit) {
        viewModel.allRemoved.collect { onAllPlaylistsRemoved() }
    }
    LaunchedEffect(Unit) {
        viewModel.playlistReady.collect {
            editorDraft = null
            onPlaylistReady()
        }
    }
    LaunchedEffect(isTv, playlists.isNotEmpty()) {
        if (isTv && playlists.isNotEmpty()) runCatching { firstRowFocus.requestFocus() }
    }
    // First-run hand-off: "Add from your phone" on the setup screen opens the
    // phone-entry panel immediately instead of landing on a menu.
    LaunchedEffect(Unit) {
        if (PhoneEntryLaunch.consume() && viewModel.phoneEntryAvailable) {
            viewModel.startPhoneEntry()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(screenPadding()),
    ) {
        PlaylistHeader(
            compact = compact,
            busy = busy,
            allowPersonal = BuildConfig.ALLOW_PERSONAL_PLAYLISTS,
            onAdd = { editorDraft = PlaylistDraft() },
            onImport = {
                importLauncher.launch(
                    arrayOf("audio/x-mpegurl", "application/x-mpegURL", "text/plain", "*/*"),
                )
            },
            onRefreshPortal = viewModel::refreshFromPortal,
            onPhoneEntry = if (viewModel.phoneEntryAvailable) viewModel::startPhoneEntry else null,
        )
        Spacer(Modifier.height(18.dp))

        if (playlists.isEmpty()) {
            EmptyState(message = "No playlists yet. Add Xtream details, an M3U URL, or import an M3U file.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(if (isTv) 0.dp else 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusRestorer(),
            ) {
                items(count = playlists.size, key = { playlists[it].id }) { index ->
                    val playlist = playlists[index]
                    val focusSafeModifier = Modifier.padding(
                        horizontal = if (isTv) 16.dp else 0.dp,
                        vertical = if (isTv) 10.dp else 0.dp,
                    )
                    val rowModifier = focusSafeModifier.then(
                        if (index == 0 && isTv) {
                            Modifier.focusRequester(firstRowFocus)
                        } else {
                            Modifier
                        },
                    )

                    if (isTv) {
                        TvPlaylistCard(
                            playlist = playlist,
                            active = playlist.id == activeId,
                            personal = viewModel.isPersonal(playlist),
                            onOpenActions = { actionsFor = playlist },
                            modifier = rowModifier,
                        )
                    } else {
                        TouchPlaylistCard(
                            playlist = playlist,
                            active = playlist.id == activeId,
                            personal = viewModel.isPersonal(playlist),
                            busy = busy,
                            onSetActive = { viewModel.setActive(playlist) },
                            onSync = { viewModel.syncNow(playlist) },
                            onEdit = { editorDraft = viewModel.draftFrom(playlist) },
                            onRemove = { confirmRemove = playlist },
                            modifier = rowModifier,
                        )
                    }
                }
            }
        }

        message?.let { StatusMessage(it) }
    }

    editorDraft?.let { initial ->
        ValidatedPlaylistEditor(
            initial = initial,
            busy = busy,
            onDismiss = { if (!busy) editorDraft = null },
            onTest = viewModel::test,
            onSave = viewModel::save,
        )
    }

    phoneEntry?.let { session ->
        PhoneEntryDialog(session = session, onCancel = viewModel::cancelPhoneEntry)
    }

    actionsFor?.let { playlist ->
        TvPlaylistActions(
            playlist = playlist,
            active = playlist.id == activeId,
            personal = viewModel.isPersonal(playlist),
            onDismiss = { actionsFor = null },
            onSetActive = {
                viewModel.setActive(playlist)
                actionsFor = null
            },
            onSync = {
                viewModel.syncNow(playlist)
                actionsFor = null
            },
            onEdit = {
                editorDraft = viewModel.draftFrom(playlist)
                actionsFor = null
            },
            onRemove = {
                confirmRemove = playlist
                actionsFor = null
            },
        )
    }

    confirmRemove?.let { playlist ->
        NovaDialog(title = "Remove “${playlist.name}”?", onDismiss = { confirmRemove = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "All synced channels, movies, series, bookmarks, recents and playback progress from this playlist will be removed from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                NovaButton(
                    text = "Cancel",
                    onClick = { confirmRemove = null },
                    modifier = Modifier.fillMaxWidth(),
                )
                NovaButton(
                    text = "Remove playlist",
                    onClick = {
                        viewModel.remove(playlist)
                        confirmRemove = null
                    },
                    prominent = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Screen title with Add / Import M3U / Refresh portal actions: stacked full-width buttons
 * on compact widths, a single header row elsewhere. All actions no-op while busy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistHeader(
    compact: Boolean,
    busy: Boolean,
    allowPersonal: Boolean,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onRefreshPortal: () -> Unit,
    onPhoneEntry: (() -> Unit)? = null,
) {
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "Playlists", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Add your own source or refresh playlists assigned by a provider.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Managed-only brands: the provider assigns playlists; every
            // personal entry point disappears, not just the activation one.
            if (allowPersonal) {
                NovaButton(
                    text = "Add playlist",
                    onClick = { if (!busy) onAdd() },
                    prominent = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                onPhoneEntry?.let { start ->
                    NovaButton(
                        text = "Add from your phone",
                        onClick = { if (!busy) start() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (allowPersonal) {
                    NovaButton(
                        text = "Import M3U",
                        onClick = { if (!busy) onImport() },
                        modifier = Modifier.weight(1f),
                    )
                }
                NovaButton(
                    text = "Refresh portal",
                    onClick = { if (!busy) onRefreshPortal() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Playlists", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Personal sources and provider-managed assignments",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allowPersonal) {
                    NovaButton(text = "Add playlist", onClick = { if (!busy) onAdd() }, prominent = true)
                    onPhoneEntry?.let { start ->
                        NovaButton(text = "Add from your phone", onClick = { if (!busy) start() })
                    }
                    NovaButton(text = "Import M3U", onClick = { if (!busy) onImport() })
                }
                NovaButton(text = "Refresh portal", onClick = { if (!busy) onRefreshPortal() })
            }
        }
    }
}

/**
 * Touch-mode playlist card with inline Use / Sync / Edit / Remove buttons. "Use" is hidden
 * for the active playlist and "Edit" for managed ones; clicks are ignored while busy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TouchPlaylistCard(
    playlist: Playlist,
    active: Boolean,
    personal: Boolean,
    busy: Boolean,
    onSetActive: () -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        PlaylistSummary(playlist = playlist, active = active, personal = personal)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!active) NovaButton(text = "Use", onClick = { if (!busy) onSetActive() })
            NovaButton(text = if (busy) "Working…" else "Sync", onClick = { if (!busy) onSync() })
            if (personal) NovaButton(text = "Edit", onClick = { if (!busy) onEdit() })
            NovaButton(text = "Remove", onClick = { if (!busy) onRemove() })
        }
    }
}

/**
 * TV-mode playlist row: a single focusable card that opens the actions dialog on click,
 * keeping D-pad traversal to one focus stop per playlist instead of a button row.
 */
@Composable
private fun TvPlaylistCard(
    playlist: Playlist,
    active: Boolean,
    personal: Boolean,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onOpenActions,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp),
        focusedScale = 1.025f,
    ) {
        PlaylistSummary(
            playlist = playlist,
            active = active,
            personal = personal,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

/**
 * Shared name + ACTIVE badge + metadata block (type, Personal/Managed, sync state, account
 * expiry) rendered inside both the touch and TV cards.
 */
@Composable
private fun PlaylistSummary(
    playlist: Playlist,
    active: Boolean,
    personal: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (active) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(
                    if (playlist.type == Playlist.TYPE_XTREAM) "Xtream" else "M3U",
                    if (personal) "Personal" else "Managed",
                    playlist.syncLabel(),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            playlist.expiryEpochSec?.let { expiry ->
                Text(
                    text = "Account expires ${formatDate(expiry * 1000)}" +
                        playlist.maxConnections?.let { "  ·  $it connection(s)" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Transient ViewModel status line below the list, tinted as an error when the text
 * heuristically reads like a failure ("failed", "could not", "mismatch").
 */
@Composable
private fun StatusMessage(text: String) {
    val failed = text.contains("failed", ignoreCase = true) ||
        text.contains("could not", ignoreCase = true) ||
        text.contains("mismatch", ignoreCase = true)
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    )
}

/**
 * Focus-first per-playlist action dialog for TV. "Set active" is hidden for the already
 * active playlist and "Edit" for managed (portal-assigned) ones; each action also dismisses.
 */
@Composable
private fun TvPlaylistActions(
    playlist: Playlist,
    active: Boolean,
    personal: Boolean,
    onDismiss: () -> Unit,
    onSetActive: () -> Unit,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    NovaDialog(title = playlist.name, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!active) {
                NovaButton(
                    text = "Set active",
                    onClick = onSetActive,
                    prominent = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            NovaButton(text = "Synchronize now", onClick = onSync, modifier = Modifier.fillMaxWidth())
            if (personal) NovaButton(text = "Edit", onClick = onEdit, modifier = Modifier.fillMaxWidth())
            NovaButton(text = "Remove", onClick = onRemove, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Add/Edit playlist dialog shared by TV and touch, backed by [validatePlaylistDraft].
 * Field errors only show after the first submit attempt; on TV the first field grabs focus.
 * Imported file playlists (file: URL) can only be renamed, and the body caps its height to
 * stay scrollable on small screens.
 */
@Composable
private fun ValidatedPlaylistEditor(
    initial: PlaylistDraft,
    busy: Boolean,
    onDismiss: () -> Unit,
    onTest: (PlaylistDraft) -> Unit,
    onSave: (PlaylistDraft) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var showValidation by rememberSaveable(initial.id) { mutableStateOf(false) }
    val errors = validatePlaylistDraft(draft)
    val editing = draft.id != null
    val importedFile = draft.url.startsWith("file:")
    val firstFocus = remember { FocusRequester() }
    val isTv = isTvDevice()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val bodyMaxHeight = (screenHeight - 140.dp).coerceIn(220.dp, 680.dp)

    // Turns on validation display; runs the action only when the draft is valid and idle.
    fun submit(action: (PlaylistDraft) -> Unit) {
        showValidation = true
        if (errors.isValid && !busy) action(draft)
    }

    LaunchedEffect(Unit) {
        if (isTv) runCatching { firstFocus.requestFocus() }
    }

    NovaDialog(
        title = if (editing) "Edit playlist" else "Add playlist",
        onDismiss = onDismiss,
        maxWidth = 540.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(13.dp),
            modifier = Modifier
                .heightIn(max = bodyMaxHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PlaylistTypeChoice(
                        text = "Xtream",
                        selected = draft.type == Playlist.TYPE_XTREAM,
                        onClick = {
                            draft = draft.copy(type = Playlist.TYPE_XTREAM)
                            showValidation = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PlaylistTypeChoice(
                        text = "M3U",
                        selected = draft.type == Playlist.TYPE_M3U,
                        onClick = {
                            draft = draft.copy(type = Playlist.TYPE_M3U)
                            showValidation = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Text(
                    text = if (draft.type == Playlist.TYPE_XTREAM) "Xtream playlist" else "M3U playlist",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            AdaptiveFormField(
                label = "Playlist name",
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                error = errors.name.takeIf { showValidation },
                focusRequester = firstFocus,
            )

            if (draft.type == Playlist.TYPE_XTREAM) {
                AdaptiveFormField(
                    label = "Server URL",
                    value = draft.server,
                    onValueChange = { draft = draft.copy(server = it) },
                    placeholder = "http://provider.example:8080",
                    keyboardType = KeyboardType.Uri,
                    error = errors.server.takeIf { showValidation },
                    supportingText = "Include the port when your provider uses one.",
                )
                AdaptiveFormField(
                    label = "Username",
                    value = draft.username,
                    onValueChange = { draft = draft.copy(username = it) },
                    error = errors.username.takeIf { showValidation },
                )
                AdaptiveFormField(
                    label = "Password",
                    value = draft.password,
                    onValueChange = { draft = draft.copy(password = it) },
                    password = true,
                    imeAction = ImeAction.Done,
                    error = errors.password.takeIf { showValidation },
                    onImeAction = { submit(onSave) },
                )
            } else if (importedFile) {
                Text(
                    text = "This playlist is stored as an imported local file. You can rename it here; import another file to replace its contents.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AdaptiveFormField(
                    label = "M3U URL",
                    value = draft.url,
                    onValueChange = { draft = draft.copy(url = it) },
                    placeholder = "https://provider.example/playlist.m3u",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                    error = errors.url.takeIf { showValidation },
                    onImeAction = { submit(onSave) },
                )
            }

            if (showValidation && !errors.isValid) {
                Text(
                    text = "Complete the highlighted fields before testing or saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(2.dp))
            NovaButton(
                text = if (busy) "Working…" else "Save and synchronize",
                onClick = { submit(onSave) },
                prominent = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NovaButton(
                text = "Test connection",
                onClick = { submit(onTest) },
                modifier = Modifier.fillMaxWidth(),
            )
            NovaButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Pill-shaped Xtream/M3U type selector; the selected pill gets a variant background and
 * primary label, with a slight scale on focus.
 */
@Composable
private fun PlaylistTypeChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        focusedScale = 1.03f,
        modifier = modifier.height(50.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

// Human-readable last-sync status for the metadata line; 0 means never synced.
private fun Playlist.syncLabel(): String = if (lastSyncEpochMs <= 0L) {
    "Not synchronized"
} else {
    "Synced ${formatDateTime(lastSyncEpochMs)}"
}

// Locale-aware date, e.g. "Mar 4, 2026" — used for account expiry.
private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))

// Locale-aware date + time, e.g. "Mar 4, 18:32" — used for the last-sync label.
private fun formatDateTime(epochMs: Long): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(epochMs))

/**
 * Phone-entry panel: the viewer scans the QR (or opens the address and types
 * the code) and enters the playlist on their phone — never with the remote.
 * The dialog closes by itself the moment the playlist arrives.
 */
@Composable
private fun PhoneEntryDialog(
    session: PortalPairingSession,
    onCancel: () -> Unit,
) {
    NovaDialog(title = "Add from your phone", onDismiss = onCancel) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Scan with your phone camera and type your playlist there.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QrImage(content = session.verificationUri, size = 168.dp)
            Text(
                text = session.userCode,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "No camera? Open ${session.verificationUri.substringBefore("?")} and enter the code.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Waiting for your playlist…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            NovaButton(text = "Cancel", onClick = onCancel)
        }
    }
}
