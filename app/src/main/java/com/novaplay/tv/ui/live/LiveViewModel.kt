package com.novaplay.tv.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.novaplay.tv.data.db.Bookmark
import com.novaplay.tv.data.db.LiveCategory
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.epg.EpgNowNext
import com.novaplay.tv.data.repo.CatalogType
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.ParentalControlsRepository
import com.novaplay.tv.ui.components.ParentalUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Combined pager key: any change in playlist, category or query swaps the
// paging source via flatMapLatest.
private data class BrowseKey(
    val playlistId: Long,
    val categoryId: Long?,
    val searchQuery: String?, // null = browsing, non-null = searching
)

/**
 * State holder for the Live browser: paged channel lists per category,
 * FTS-backed search with a 300 ms debounce, bookmark toggles, and remote
 * digit entry that resolves a channel number to its list position.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val parentalRepository: ParentalControlsRepository,
) : ViewModel() {

    private val playlistId: StateFlow<Long?> = contentRepository.activePlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val categories: StateFlow<List<LiveCategory>> = playlistId
        .filterNotNull()
        .flatMapLatest { contentRepository.liveCategories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedCategoryId = MutableStateFlow<Long?>(null) // null = All
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 300 ms debounce + flatMapLatest below: stale queries are cancelled, and
    // anything under 2 characters falls back to browsing.
    private val effectiveQuery: Flow<String?> = combine(_searchActive, _searchQuery) { active, query ->
        query.takeIf { active && it.length >= 2 }
    }.debounce(300).distinctUntilChanged()

    val channels: Flow<PagingData<LiveChannel>> =
        combine(
            playlistId.filterNotNull(),
            _selectedCategoryId,
            effectiveQuery,
        ) { pid, categoryId, query -> BrowseKey(pid, categoryId, query) }
            .distinctUntilChanged()
            .flatMapLatest { key ->
                if (key.searchQuery != null) {
                    val fts = ContentRepository.ftsPrefixQuery(key.searchQuery)
                    if (fts == null) {
                        emptyFlow()
                    } else {
                        contentRepository.searchChannelsPager(key.playlistId, fts)
                    }
                } else {
                    contentRepository.channelsPager(key.playlistId, key.categoryId)
                }
            }
            .cachedIn(viewModelScope)

    // --- parental control ---

    /** PIN/lock snapshot for the category surfaces; badges and gating read this. */
    val parental: StateFlow<ParentalUiState> = combine(
        parentalRepository.pinConfigured,
        parentalRepository.sessionUnlocked,
        playlistId.filterNotNull().flatMapLatest {
            contentRepository.lockedCategoryIds(CatalogType.LIVE, it)
        },
    ) { configured, unlocked, locked ->
        ParentalUiState(pinConfigured = configured, sessionUnlocked = unlocked, lockedIds = locked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParentalUiState())

    /** Verifies the PIN (lockout-aware) and unlocks the session on success. */
    suspend fun unlockParental(pin: String) = parentalRepository.unlock(pin)

    /** Creates the parental PIN (first lock ever); false for non-4-digit input. */
    suspend fun setParentalPin(pin: String): Boolean = parentalRepository.setPin(pin)

    /** Locks or unlocks one live category; callers gate this behind the PIN. */
    fun toggleCategoryLock(categoryId: Long) {
        val pid = playlistId.value ?: return
        viewModelScope.launch {
            contentRepository.toggleCategoryLock(CatalogType.LIVE, pid, categoryId)
        }
    }

    // Bookmarked stream ids for per-row state; toggling updates instantly.
    val bookmarkedIds: StateFlow<Set<Long>> = playlistId
        .filterNotNull()
        .flatMapLatest { contentRepository.bookmarkedIds(it, Bookmark.MEDIA_LIVE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Adds or removes the channel from bookmarks; [bookmarkedIds] updates reactively. */
    fun toggleBookmark(channel: LiveChannel) {
        viewModelScope.launch {
            contentRepository.toggleBookmark(
                playlistId = channel.playlistId,
                mediaType = Bookmark.MEDIA_LIVE,
                remoteId = channel.streamId,
            )
        }
    }

    /** Switches browsing to [categoryId] (null = All) and leaves search mode. */
    fun selectCategory(categoryId: Long?) {
        _searchActive.value = false
        _searchQuery.value = ""
        _selectedCategoryId.value = categoryId
    }

    /** Enters search mode; queries only take effect from two characters on. */
    fun openSearch() {
        _searchActive.value = true
    }

    /** Leaves search mode and clears the query, returning to category browsing. */
    fun closeSearch() {
        _searchActive.value = false
        _searchQuery.value = ""
    }

    /** Updates the raw search text; it reaches [channels] via the debounced effective query. */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    // --- EPG now/next ---

    // Shared minute tick so every visible row rolls its "now" programme over
    // together without a resync; WhileSubscribed stops it off-screen.
    private val guideTick: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(GUIDE_TICK_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    /**
     * Now/next guide info for one channel row. Each visible row collects its own
     * flow — a cheap indexed two-row query — instead of loading the whole
     * playlist's guide into memory.
     */
    fun nowNext(channel: LiveChannel): Flow<EpgNowNext> =
        guideTick.flatMapLatest { now -> contentRepository.nowNext(channel, now) }

    // --- digit jump ---

    private val _digitBuffer = MutableStateFlow("")
    val digitBuffer: StateFlow<String> = _digitBuffer.asStateFlow()

    private val _jumpToIndex = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val jumpToIndex: SharedFlow<Int> = _jumpToIndex.asSharedFlow()

    private var digitJob: Job? = null

    /**
     * Buffers a remote-control digit (last four kept). After 1.4 s of key
     * inactivity the number commits: an indexed DB lookup resolves it to a
     * list position emitted on [jumpToIndex]. Ignored while search is active.
     */
    fun onDigit(digit: Char) {
        if (_searchActive.value) return
        _digitBuffer.value = (_digitBuffer.value + digit).takeLast(4)
        digitJob?.cancel()
        digitJob = viewModelScope.launch {
            delay(DIGIT_COMMIT_MS)
            val num = _digitBuffer.value.toIntOrNull()
            _digitBuffer.value = ""
            val pid = playlistId.value
            if (num != null && pid != null) {
                val index = contentRepository.positionOfChannelNum(pid, _selectedCategoryId.value, num)
                _jumpToIndex.tryEmit(index)
            }
        }
    }

    private companion object {
        const val DIGIT_COMMIT_MS = 1_400L
        const val GUIDE_TICK_MS = 60_000L
    }
}
