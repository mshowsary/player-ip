package com.novaplay.tv.ui.components

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.novaplay.tv.data.repo.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest

// Shared browse/search state machine for the Movies and Series catalogs:
// category selection, 300 ms debounced FTS search (min 2 chars), stale
// queries cancelled by flatMapLatest.
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CatalogBrowser<T : Any>(
    scope: CoroutineScope,
    playlistId: Flow<Long?>,
    browse: (playlistId: Long, categoryId: Long?) -> Flow<PagingData<T>>,
    search: (playlistId: Long, ftsQuery: String) -> Flow<PagingData<T>>,
) {
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _searchActive = MutableStateFlow(false)
    val searchActive: StateFlow<Boolean> = _searchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val effectiveQuery: Flow<String?> =
        combine(_searchActive, _searchQuery) { active, query ->
            query.takeIf { active && it.length >= 2 }
        }.debounce(300).distinctUntilChanged()

    val items: Flow<PagingData<T>> =
        combine(
            playlistId.filterNotNull().distinctUntilChanged(),
            _selectedCategoryId,
            effectiveQuery,
        ) { pid, categoryId, query -> Triple(pid, categoryId, query) }
            .distinctUntilChanged()
            .flatMapLatest { (pid, categoryId, query) ->
                if (query != null) {
                    val fts = ContentRepository.ftsPrefixQuery(query)
                    if (fts == null) emptyFlow() else search(pid, fts)
                } else {
                    browse(pid, categoryId)
                }
            }
            .cachedIn(scope)

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
}
