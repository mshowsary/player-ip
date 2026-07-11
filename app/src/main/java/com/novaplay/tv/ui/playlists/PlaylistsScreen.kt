package com.novaplay.tv.ui.playlists

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.repo.ActivationCheck
import com.novaplay.tv.data.repo.ActivationRepository
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.PlaylistDraft
import com.novaplay.tv.data.repo.PlaylistManager
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.di.ApplicationScope
import com.novaplay.tv.ui.components.EmptyState
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.NovaDialog
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * State and actions for playlist management: exposes the playlist list and active id, runs
 * test/save/import/sync/remove behind a single [busy] flag (operations are dropped, not
 * queued, while one runs), and emits one-shot events when a playlist becomes usable
 * ([playlistReady]) or the last one is removed ([allRemoved]).
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val activationRepository: ActivationRepository,
    private val syncRepository: SyncRepository,
    private val playlistManager: PlaylistManager,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = contentRepository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeId: StateFlow<Long?> = contentRepository.activePlaylist
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _allRemoved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val allRemoved: SharedFlow<Unit> = _allRemoved.asSharedFlow()

    private val _playlistReady = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val playlistReady: SharedFlow<Unit> = _playlistReady.asSharedFlow()

    /** True for user-created playlists (editable); false for portal-managed assignments. */
    fun isPersonal(playlist: Playlist): Boolean = playlistManager.isPersonal(playlist)

    /** Builds an editable draft (including credentials) from a stored playlist. */
    fun draftFrom(playlist: Playlist): PlaylistDraft = playlistManager.draftFrom(playlist)

    /**
     * Makes the playlist the active content source and syncs it if its data is stale.
     * Runs in the app scope so the sync survives leaving this screen.
     */
    fun setActive(playlist: Playlist) {
        appScope.launch {
            contentRepository.setActivePlaylist(playlist.id)
            syncRepository.syncIfStale(playlist)
        }
        showMessage("“${playlist.name}” is now active")
    }

    /**
     * Runs a full sync of the playlist, reporting the outcome and emitting [playlistReady]
     * on success. Dropped silently while another operation is busy.
     */
    fun syncNow(playlist: Playlist) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = syncRepository.sync(playlist)
            _busy.value = false
            result.fold(
                onSuccess = {
                    showMessage("“${playlist.name}” synchronized")
                    _playlistReady.tryEmit(Unit)
                },
                onFailure = { showMessage("Synchronization failed: ${it.userMessage()}") },
            )
        }
    }

    // Re-polls the activation endpoint and upserts whatever the portal returns.
    fun refreshFromPortal() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (val result = activationRepository.checkAndAttach()) {
                is ActivationCheck.Activated ->
                    showMessage("Portal refreshed — ${result.playlistCount} playlist(s) attached")
                ActivationCheck.NotRegistered ->
                    showMessage("Device not registered on the portal")
                ActivationCheck.KeyMismatch ->
                    showMessage("Device key mismatch — re-register this device")
                is ActivationCheck.Failure ->
                    showMessage("Could not reach the portal: ${result.message}")
            }
            _busy.value = false
        }
    }

    /**
     * Probes the draft's source without saving anything; on success the message includes
     * connection count and account expiry when the provider reports them.
     */
    fun test(draft: PlaylistDraft) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val result = playlistManager.test(draft)
            _busy.value = false
            result.fold(
                onSuccess = { probe ->
                    val details = listOfNotNull(
                        probe.maxConnections?.let { "$it connection(s)" },
                        probe.expiryEpochSec?.let {
                            "expires " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                .format(Date(it * 1000))
                        },
                    ).joinToString(" · ")
                    showMessage(probe.message + details.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty())
                },
                onFailure = { showMessage("Connection failed: ${it.userMessage()}") },
            )
        }
    }

    /**
     * Persists the draft, makes it active and syncs it immediately. The playlist is kept
     * even when the follow-up sync fails; [playlistReady] fires only on full success.
     */
    fun save(draft: PlaylistDraft) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            playlistManager.save(draft).fold(
                onSuccess = { saved ->
                    contentRepository.setActivePlaylist(saved.id)
                    val syncResult = syncRepository.sync(saved)
                    _busy.value = false
                    if (syncResult.isSuccess) {
                        showMessage("“${saved.name}” saved and synchronized")
                        _playlistReady.tryEmit(Unit)
                    } else {
                        showMessage(
                            "Playlist saved, but synchronization failed: " +
                                syncResult.exceptionOrNull().userMessage(),
                        )
                    }
                },
                onFailure = {
                    _busy.value = false
                    showMessage("Could not save playlist: ${it.userMessage()}")
                },
            )
        }
    }

    /**
     * Imports a local M3U file as a new playlist, then activates and syncs it, mirroring
     * [save]'s keep-on-sync-failure behavior.
     */
    fun importM3u(uri: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            playlistManager.importM3u(uri).fold(
                onSuccess = { saved ->
                    contentRepository.setActivePlaylist(saved.id)
                    val syncResult = syncRepository.sync(saved)
                    _busy.value = false
                    if (syncResult.isSuccess) {
                        showMessage("Imported “${saved.name}”")
                        _playlistReady.tryEmit(Unit)
                    } else {
                        showMessage(
                            "File imported, but synchronization failed: " +
                                syncResult.exceptionOrNull().userMessage(),
                        )
                    }
                },
                onFailure = {
                    _busy.value = false
                    showMessage("Could not import playlist: ${it.userMessage()}")
                },
            )
        }
    }

    /**
     * Deletes the playlist and its synced content. Emits [allRemoved] instead of a message
     * when it was the last playlist, so the UI can route back to activation.
     */
    fun remove(playlist: Playlist) {
        viewModelScope.launch {
            val remaining = contentRepository.deletePlaylist(playlist.id)
            if (remaining == 0) {
                _allRemoved.tryEmit(Unit)
            } else {
                showMessage("Removed “${playlist.name}”")
            }
        }
    }

    // Shows a transient status message, auto-clearing after 4.5s unless replaced meanwhile.
    private fun showMessage(text: String) {
        viewModelScope.launch {
            _message.value = text
            delay(4_500)
            if (_message.value == text) _message.value = null
        }
    }

    // User-facing text for a throwable, falling back when the message is null/blank.
    private fun Throwable?.userMessage(): String =
        this?.message?.takeIf { it.isNotBlank() } ?: "Unknown error"
}

/**
 * Original single-layout playlists screen: dialog-based per-playlist actions and an editor
 * without field validation. On TV the first row grabs initial focus. Superseded in the nav
 * graph by [AdaptivePlaylistsScreen]; kept as a standalone entry point over the same ViewModel.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistsScreen(
    onAllPlaylistsRemoved: () -> Unit,
    onPlaylistReady: () -> Unit = {},
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var actionsFor by remember { mutableStateOf<Playlist?>(null) }
    var confirmRemove by remember { mutableStateOf<Playlist?>(null) }
    var editorDraft by remember { mutableStateOf<PlaylistDraft?>(null) }
    val firstRowFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val isTv = isTvDevice()
    val compact = isCompactWidth()
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
    LaunchedEffect(playlists.isNotEmpty()) {
        if (isTv && playlists.isNotEmpty()) runCatching { firstRowFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(screenPadding()),
    ) {
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Playlists", style = MaterialTheme.typography.headlineMedium)
                PlaylistToolbar(
                    busy = busy,
                    onAdd = { editorDraft = PlaylistDraft() },
                    onImport = { importLauncher.launch(arrayOf("audio/x-mpegurl", "application/x-mpegURL", "text/plain", "*/*")) },
                    onRefreshPortal = viewModel::refreshFromPortal,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                PlaylistToolbar(
                    busy = busy,
                    onAdd = { editorDraft = PlaylistDraft() },
                    onImport = { importLauncher.launch(arrayOf("audio/x-mpegurl", "application/x-mpegURL", "text/plain", "*/*")) },
                    onRefreshPortal = viewModel::refreshFromPortal,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        if (playlists.isEmpty()) {
            EmptyState(message = "No playlists yet. Add Xtream details, an M3U URL, or import an M3U file.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusRestorer(),
            ) {
                items(count = playlists.size, key = { playlists[it].id }) { index ->
                    val playlist = playlists[index]
                    PlaylistRow(
                        playlist = playlist,
                        isActive = playlist.id == activeId,
                        personal = viewModel.isPersonal(playlist),
                        onClick = { actionsFor = playlist },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstRowFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = if (it.contains("failed", ignoreCase = true) || it.contains("could not", ignoreCase = true)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    editorDraft?.let { initial ->
        PlaylistEditorDialog(
            initial = initial,
            busy = busy,
            onDismiss = { if (!busy) editorDraft = null },
            onTest = viewModel::test,
            onSave = viewModel::save,
        )
    }

    actionsFor?.let { playlist ->
        NovaDialog(title = playlist.name, onDismiss = { actionsFor = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NovaButton(
                    text = "Set active",
                    onClick = {
                        viewModel.setActive(playlist)
                        actionsFor = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                NovaButton(
                    text = "Synchronize now",
                    onClick = {
                        viewModel.syncNow(playlist)
                        actionsFor = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (viewModel.isPersonal(playlist)) {
                    NovaButton(
                        text = "Edit",
                        onClick = {
                            editorDraft = viewModel.draftFrom(playlist)
                            actionsFor = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                NovaButton(
                    text = "Remove",
                    onClick = {
                        confirmRemove = playlist
                        actionsFor = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    confirmRemove?.let { playlist ->
        NovaDialog(title = "Remove “${playlist.name}”?", onDismiss = { confirmRemove = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "All synced channels, movies and series from this playlist will be removed from this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Row of Add / Import M3U / Refresh portal buttons; all ignore clicks while busy. */
@Composable
private fun PlaylistToolbar(
    busy: Boolean,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onRefreshPortal: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NovaButton(text = "Add", onClick = { if (!busy) onAdd() })
        NovaButton(text = "Import M3U", onClick = { if (!busy) onImport() })
        NovaButton(text = "Refresh portal", onClick = { if (!busy) onRefreshPortal() })
    }
}

/**
 * Add/Edit playlist dialog without field validation: Test/Save pass the raw draft through.
 * The type can only be chosen when creating; imported file playlists (file: URL) expose
 * just the name field.
 */
@Composable
private fun PlaylistEditorDialog(
    initial: PlaylistDraft,
    busy: Boolean,
    onDismiss: () -> Unit,
    onTest: (PlaylistDraft) -> Unit,
    onSave: (PlaylistDraft) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val editing = draft.id != null
    val importedFile = draft.url.startsWith("file:")

    NovaDialog(
        title = if (editing) "Edit playlist" else "Add playlist",
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PlaylistTypeButton(
                        text = "Xtream",
                        selected = draft.type == Playlist.TYPE_XTREAM,
                        onClick = { draft = draft.copy(type = Playlist.TYPE_XTREAM) },
                        modifier = Modifier.weight(1f),
                    )
                    PlaylistTypeButton(
                        text = "M3U",
                        selected = draft.type == Playlist.TYPE_M3U,
                        onClick = { draft = draft.copy(type = Playlist.TYPE_M3U) },
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

            PlaylistTextField(
                label = "Playlist name",
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
            )

            if (draft.type == Playlist.TYPE_XTREAM) {
                PlaylistTextField(
                    label = "Server URL",
                    value = draft.server,
                    onValueChange = { draft = draft.copy(server = it) },
                    keyboardType = KeyboardType.Uri,
                )
                PlaylistTextField(
                    label = "Username",
                    value = draft.username,
                    onValueChange = { draft = draft.copy(username = it) },
                )
                PlaylistTextField(
                    label = "Password",
                    value = draft.password,
                    onValueChange = { draft = draft.copy(password = it) },
                    password = true,
                )
            } else if (importedFile) {
                Text(
                    text = "This playlist was imported from a local file. You can rename it here; import another file to replace its contents.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                PlaylistTextField(
                    label = "M3U URL",
                    value = draft.url,
                    onValueChange = { draft = draft.copy(url = it) },
                    keyboardType = KeyboardType.Uri,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NovaButton(
                    text = if (busy) "Working…" else "Test",
                    onClick = { if (!busy) onTest(draft) },
                    modifier = Modifier.weight(1f),
                )
                NovaButton(
                    text = "Save",
                    onClick = { if (!busy) onSave(draft) },
                    prominent = true,
                    modifier = Modifier.weight(1f),
                )
            }
            NovaButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Xtream/M3U type selector button: selected state tints container and label, and the
 * focused state switches to the primary color for D-pad visibility.
 */
@Composable
private fun PlaylistTypeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        focusedContainerColor = MaterialTheme.colorScheme.primary,
        focusedScale = 1.03f,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * Minimal labeled single-line text field with the label doubling as placeholder; supports
 * password masking and a URI keyboard, advancing focus with the IME Next action.
 */
@Composable
private fun PlaylistTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = ImeAction.Next,
            ),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Focusable playlist row showing name, type/ownership/expiry metadata and an ACTIVE marker;
 * clicking (or D-pad select) opens the per-playlist actions dialog.
 */
@Composable
private fun PlaylistRow(
    playlist: Playlist,
    isActive: Boolean,
    personal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
        focusedScale = 1.02f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        if (playlist.type == Playlist.TYPE_XTREAM) "Xtream" else "M3U",
                        if (personal) "Personal" else "Managed",
                        playlist.expiryEpochSec?.let {
                            "expires " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                .format(Date(it * 1000))
                        },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isActive) {
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
