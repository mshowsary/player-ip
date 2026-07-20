package com.novaplay.tv.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novaplay.tv.data.db.Bookmark
import com.novaplay.tv.data.db.Movie
import com.novaplay.tv.data.db.VodCategory
import com.novaplay.tv.data.repo.CatalogType
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.data.repo.ParentalControlsRepository
import com.novaplay.tv.ui.components.CatalogBrowser
import com.novaplay.tv.ui.components.ParentalUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Catalog state for the Movies grid, all scoped to the active playlist:
 * VOD [categories], a paged/searchable [browser] (Paging 3 over Room), and
 * the set of bookmarked stream ids. Everything re-resolves when the active
 * playlist switches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val parentalRepository: ParentalControlsRepository,
) : ViewModel() {

    private val playlistId: StateFlow<Long?> = contentRepository.activePlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    /** PIN/lock snapshot for the category surfaces; badges and gating read this. */
    val parental: StateFlow<ParentalUiState> = combine(
        parentalRepository.pinConfigured,
        parentalRepository.sessionUnlocked,
        playlistId.filterNotNull().flatMapLatest {
            contentRepository.lockedCategoryIds(CatalogType.VOD, it)
        },
    ) { configured, unlocked, locked ->
        ParentalUiState(pinConfigured = configured, sessionUnlocked = unlocked, lockedIds = locked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParentalUiState())

    /** Verifies the PIN (lockout-aware) and unlocks the session on success. */
    suspend fun unlockParental(pin: String) = parentalRepository.unlock(pin)

    /** Creates the parental PIN (first lock ever); false for non-4-digit input. */
    suspend fun setParentalPin(pin: String): Boolean = parentalRepository.setPin(pin)

    /** Locks or unlocks one VOD category; callers gate this behind the PIN. */
    fun toggleCategoryLock(categoryId: Long) {
        val pid = playlistId.value ?: return
        viewModelScope.launch {
            contentRepository.toggleCategoryLock(CatalogType.VOD, pid, categoryId)
        }
    }

    /** Adds or removes the movie's bookmark; [bookmarkedIds] updates reactively via Room. */
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
