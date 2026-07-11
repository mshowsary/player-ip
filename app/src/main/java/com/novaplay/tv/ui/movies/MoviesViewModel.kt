package com.novaplay.tv.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaplay.tv.data.db.Bookmark
import com.novaplay.tv.data.db.Movie
import com.novaplay.tv.data.db.VodCategory
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
) : ViewModel() {

    private val playlistId = contentRepository.activePlaylist
        .map { it?.id }
        .distinctUntilChanged()

    val categories: StateFlow<List<VodCategory>> = playlistId
        .filterNotNull()
        .flatMapLatest { contentRepository.vodCategories(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val browser = CatalogBrowser(
        scope = viewModelScope,
        playlistId = playlistId,
        browse = contentRepository::moviesPager,
        search = contentRepository::searchMoviesPager,
    )

    val bookmarkedIds: StateFlow<Set<Long>> = playlistId
        .filterNotNull()
        .flatMapLatest { contentRepository.bookmarkedIds(it, Bookmark.MEDIA_MOVIE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleBookmark(movie: Movie) {
        viewModelScope.launch {
            contentRepository.toggleBookmark(
                playlistId = movie.playlistId,
                mediaType = Bookmark.MEDIA_MOVIE,
                remoteId = movie.streamId,
            )
        }
    }
}
