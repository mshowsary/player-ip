package com.novaplay.tv.ui.playlists

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.repo.ActivationCheck
import com.novaplay.tv.data.repo.ActivationRepository
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.PlaylistDraft
import com.novaplay.tv.data.repo.PlaylistManager
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.di.ApplicationScope
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
