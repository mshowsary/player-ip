package com.novaplay.tv.ui.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaplay.tv.data.db.Bookmark
import com.novaplay.tv.data.db.Series
import com.novaplay.tv.data.db.SeriesCategory
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.ui.components.CatalogBrowser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Catalog state for the Series grid, all scoped to the active playlist:
 * series [categories], a paged/searchable [browser] (Paging 3 over Room), and
 * the set of bookmarked series ids. Everything re-resolves when the active
 * playlist switches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
) : ViewModel() {

    private val playlistId = contentRepository.activePlaylist
        .map { it?.id }
        .distinctUntilChanged()

    val categories: StateFlow<List<SeriesCategory>> = playlistId
        .filterNotNull()
        .flatMapLatest { contentRepository.seriesCategories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val browser = CatalogBrowser(
        scope = viewModelScope,
        playlistId = playlistId,
        browse = contentRepository::seriesPager,
        search = contentRepository::searchSeriesPager,
    )

    val bookmarkedIds: StateFlow<Set<Long>> = playlistId
        .filterNotNull()
        .flatMapLatest { contentRepository.bookmarkedIds(it, Bookmark.MEDIA_SERIES) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Adds or removes the series bookmark; [bookmarkedIds] updates reactively via Room. */
    fun toggleBookmark(series: Series) {
        viewModelScope.launch {
            contentRepository.toggleBookmark(
                playlistId = series.playlistId,
                mediaType = Bookmark.MEDIA_SERIES,
                remoteId = series.seriesId,
            )
        }
    }
}
