package com.novaplay.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.HomeLayout
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedAccessRepository
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.data.repo.SyncStatus
import com.novaplay.tv.data.repo.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Read-only state for the hub screen: the active [playlist] (null until
 * loaded), live [syncStatus] for the footer, and the [managedAccess] policy
 * used to filter which section cards are shown.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    contentRepository: ContentRepository,
    syncRepository: SyncRepository,
    managedAccessRepository: ManagedAccessRepository,
    prefs: AppPreferences,
) : ViewModel() {

    val playlist: StateFlow<Playlist?> = contentRepository.activePlaylist
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncStatus: StateFlow<SyncStatus> = syncRepository.status

    // The progress modal auto-opens for user-initiated syncs only; silent
    // startup/background refreshes must never steal focus from the hub. A
    // dismissal hides it for the rest of that run and re-arms once the sync
    // ends, so the next update shows it again.
    private val syncModalDismissed = MutableStateFlow(false)

    val syncModalVisible: StateFlow<Boolean> =
        combine(syncRepository.status, syncModalDismissed) { status, dismissed ->
            status is SyncStatus.Syncing && status.trigger == SyncTrigger.FOREGROUND && !dismissed
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            syncRepository.status.collect { status ->
                if (status !is SyncStatus.Syncing) syncModalDismissed.value = false
            }
        }
    }

    fun dismissSyncModal() {
        syncModalDismissed.value = true
    }

    val managedAccess: StateFlow<ManagedAccessPolicy> = managedAccessRepository.policy

    /** Selected hub arrangement; restyles the grid live when changed in Settings. */
    val homeLayout: StateFlow<HomeLayout> = prefs.homeLayout
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayout.HERO)

    /** Recently watched channels for the hub rail; empty until something was played. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recentChannels: StateFlow<List<LiveChannel>> = contentRepository.activePlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else contentRepository.recentChannelsRail(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bookmarked channels for the hub rail. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val bookmarkedChannels: StateFlow<List<LiveChannel>> = contentRepository.activePlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else contentRepository.bookmarkedChannelsRail(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
