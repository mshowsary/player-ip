package com.novaplay.tv.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.novaplay.tv.data.db.Bookmark
import com.novaplay.tv.data.db.LiveCategory
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.repo.ContentRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class BrowseKey(
    val playlistId: Long,
    val categoryId: Long?,
    val searchQuery: String?, // null = browsing, non-null = searching
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
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

    // Bookmarked stream ids for per-row state; toggling updates instantly.
    val bookmarkedIds: StateFlow<Set<Long>> = playlistId
        .filterNotNull()
        .flatMapLatest { contentRepository.bookmarkedIds(it, Bookmark.MEDIA_LIVE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleBookmark(channel: LiveChannel) {
        viewModelScope.launch {
            contentRepository.toggleBookmark(
                playlistId = channel.playlistId,
                mediaType = Bookmark.MEDIA_LIVE,
                remoteId = channel.streamId,
            )
        }
    }

    fun selectCategory(categoryId: Long?) {
        _searchActive.value = false
        _searchQuery.value = ""
        _selectedCategoryId.value = categoryId
    }

    fun openSearch() {
        _searchActive.value = true
    }

    fun closeSearch() {
        _searchActive.value = false
        _searchQuery.value = ""
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    // --- digit jump ---

    private val _digitBuffer = MutableStateFlow("")
    val digitBuffer: StateFlow<String> = _digitBuffer.asStateFlow()

    private val _jumpToIndex = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val jumpToIndex: SharedFlow<Int> = _jumpToIndex.asSharedFlow()

    private var digitJob: Job? = null

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
    }
}
