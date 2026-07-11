package com.novaplay.tv.ui.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.SyncRepository
import com.novaplay.tv.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface GateState {
    data object Loading : GateState
    data object NeedsActivation : GateState
    data object Ready : GateState
}

// Launch gate: no playlist -> activation; playlist -> home, with a silent
// background refresh when the last sync is older than 12 h.
@HiltViewModel
class GateViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val syncRepository: SyncRepository,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow<GateState>(GateState.Loading)
    val state: StateFlow<GateState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val playlist = contentRepository.getActivePlaylist()
            if (playlist == null) {
                _state.value = GateState.NeedsActivation
            } else {
                _state.value = GateState.Ready
                appScope.launch { syncRepository.syncIfStale(playlist) }
            }
        }
    }
}
