package com.novaplay.tv.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.di.ApplicationScope
import com.novaplay.tv.ui.components.EmptyState
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.NovaDialog
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

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val activationRepository: ActivationRepository,
    private val syncRepository: SyncRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = contentRepository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeId: StateFlow<Long?> = contentRepository.activePlaylist
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _allRemoved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val allRemoved: SharedFlow<Unit> = _allRemoved.asSharedFlow()

    fun setActive(playlist: Playlist) {
        appScope.launch {
            contentRepository.setActivePlaylist(playlist.id)
            syncRepository.syncIfStale(playlist)
        }
        showMessage("“${playlist.name}” is now active")
    }

    // Re-polls the activation endpoint and upserts whatever the portal returns.
    fun refreshFromPortal() {
        viewModelScope.launch {
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
        }
    }

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

    private fun showMessage(text: String) {
        viewModelScope.launch {
            _message.value = text
            delay(3_500)
            if (_message.value == text) _message.value = null
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistsScreen(
    onAllPlaylistsRemoved: () -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val activeId by viewModel.activeId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var actionsFor by remember { mutableStateOf<Playlist?>(null) }
    var confirmRemove by remember { mutableStateOf<Playlist?>(null) }
    val firstRowFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val isTv = isTvDevice()

    LaunchedEffect(Unit) {
        viewModel.allRemoved.collect { onAllPlaylistsRemoved() }
    }
    LaunchedEffect(playlists.isNotEmpty()) {
        if (isTv && playlists.isNotEmpty()) runCatching { firstRowFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(screenPadding()),
    ) {
        Text(
            text = "Playlists",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(20.dp))

        if (playlists.isEmpty()) {
            EmptyState(message = "No playlists attached to this device")
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
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
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
                    text = "Refresh from portal",
                    onClick = {
                        viewModel.refreshFromPortal()
                        actionsFor = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    text = "All synced channels, movies and series from this playlist will be wiped from this device.",
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

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
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
