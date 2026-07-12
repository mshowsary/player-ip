package com.novaplay.tv.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.novaplay.tv.data.db.EpgProgramme
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.epg.GuideLaneItem
import com.novaplay.tv.data.epg.GuideTimeline
import com.novaplay.tv.data.repo.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State holder for the grid guide. The timeline window is frozen at open so
 * lanes and the ruler never shift underneath the user; only [nowMs] follows
 * the clock for the airing highlight. Channels page vertically through the
 * same windowed pager as the Live browser; each visible row collects its own
 * lane flow — a cheap indexed range query per channel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GuideViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
) : ViewModel() {

    val windowStartMs: Long = GuideTimeline.windowStartMs(System.currentTimeMillis())
    val windowEndMs: Long = GuideTimeline.windowEndMs(windowStartMs)
    val slotTimesMs: List<Long> = GuideTimeline.slotTimesMs(windowStartMs)

    /** Minute tick driving the airing highlight; stops when the screen is off-screen. */
    val nowMs: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    private val playlistId: StateFlow<Long?> = contentRepository.activePlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** All channels of the active playlist in browse order, windowed by Paging 3. */
    val channels: Flow<PagingData<LiveChannel>> = playlistId
        .filterNotNull()
        .flatMapLatest { contentRepository.channelsPager(it, categoryId = null) }
        .cachedIn(viewModelScope)

    /** Gap/cell lane for one channel across the frozen window. */
    fun lane(channel: LiveChannel): Flow<List<GuideLaneItem>> =
        if (channel.epgChannelId == null) {
            flowOf(emptyList())
        } else {
            contentRepository.guideProgrammes(channel, windowStartMs, windowEndMs)
                .map { GuideTimeline.laneItems(it, windowStartMs) }
        }

    // Focus-driven detail selection: the strip above the grid describes the
    // focused (TV) or last-tapped (touch) programme.
    private val _selected = MutableStateFlow<SelectedProgramme?>(null)
    val selected: StateFlow<SelectedProgramme?> = _selected.asStateFlow()

    /** Records the programme the user is focused on, with its channel for context. */
    fun onProgrammeSelected(channel: LiveChannel, programme: EpgProgramme) {
        _selected.value = SelectedProgramme(channel, programme)
    }
}

/** The programme currently described by the guide's detail strip. */
data class SelectedProgramme(
    val channel: LiveChannel,
    val programme: EpgProgramme,
)
