package com.novaplay.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.ui.live.CategoryChipsRow
import com.novaplay.tv.ui.live.CategoryRail
import com.novaplay.tv.ui.live.SearchField
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.screenPadding

// Category rail + poster grid, shared by Movies and Series. The grid sizes
// its columns to the space: ~5 on TV, 3 on a portrait phone, more on tablets.
// Compact widths swap the side rail for a chip strip above the grid.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T : Any> CatalogGridScreen(
    categories: List<Pair<Long, String>>,
    browser: CatalogBrowser<T>,
    searchPlaceholder: String,
    emptyMessage: String,
    itemId: (T) -> Long,
    itemContent: @Composable (T) -> Unit,
) {
    val selectedCategoryId by browser.selectedCategoryId.collectAsStateWithLifecycle()
    val searchActive by browser.searchActive.collectAsStateWithLifecycle()
    val searchQuery by browser.searchQuery.collectAsStateWithLifecycle()
    val items: LazyPagingItems<T> = browser.items.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()

    // Every group change starts the grid back at the top.
    LaunchedEffect(selectedCategoryId, searchActive) {
        gridState.scrollToItem(0)
    }

    val gridPane: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            if (searchActive) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = browser::onSearchQueryChange,
                    onClose = browser::closeSearch,
                    placeholder = searchPlaceholder,
                )
                Spacer(Modifier.height(14.dp))
            }

            when {
                items.itemCount == 0 && items.loadState.refresh is LoadState.Loading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(2) { PosterGridSkeleton(columns = 5) }
                    }
                }
                items.itemCount == 0 -> {
                    EmptyState(
                        message = when {
                            searchActive -> "Nothing matches your search"
                            selectedCategoryId == ContentRepository.CATEGORY_BOOKMARKS ->
                                "No bookmarks yet — tap the bookmark badge on any poster"
                            selectedCategoryId == ContentRepository.CATEGORY_RECENT ->
                                "Titles you play will appear here"
                            else -> emptyMessage
                        },
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 96.dp),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRestorer(),
                    ) {
                        items(
                            count = items.itemCount,
                            key = items.itemKey { itemId(it) },
                        ) { index ->
                            items[index]?.let { item ->
                                Box { itemContent(item) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCompactWidth()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPadding()),
        ) {
            CategoryChipsRow(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                searchActive = searchActive,
                onSelectCategory = browser::selectCategory,
                onOpenSearch = browser::openSearch,
            )
            Spacer(Modifier.height(12.dp))
            gridPane()
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPadding()),
        ) {
            CategoryRail(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                searchActive = searchActive,
                onSelectCategory = browser::selectCategory,
                onOpenSearch = browser::openSearch,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f),
            )

            Spacer(Modifier.width(24.dp))

            gridPane()
        }
    }
}
