package com.novaplay.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaplay.tv.data.db.Playlist
import com.novaplay.tv.data.prefs.AppPreferences
import com.novaplay.tv.data.prefs.HomeLayout
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedAccessRepository
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.data.repo.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

    val managedAccess: StateFlow<ManagedAccessPolicy> = managedAccessRepository.policy

    /** Selected hub arrangement; restyles the grid live when changed in Settings. */
    val homeLayout: StateFlow<HomeLayout> = prefs.homeLayout
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayout.CLASSIC)
}
