package com.novaplay.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.ui.live.CategoryRail
import com.novaplay.tv.ui.live.CompactCategorySelector
import com.novaplay.tv.ui.live.SearchField
import com.novaplay.tv.ui.theme.catalogLayoutSpec
import com.novaplay.tv.ui.theme.screenPadding

/**
 * Shared responsive category browser and poster grid for Movies and Series.
 * Compact touch windows use a smart category selector. Wider touch windows and
 * TV use a fixed-width category rail and a larger adaptive poster grid.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T : Any> CatalogGridScreen(
    title: String,
    categories: List<Pair<Long, String>>,
    browser: CatalogBrowser<T>,
    searchPlaceholder: String,
    emptyMessage: String,
    itemId: (T) -> Long,
    lockedIds: Set<Long> = emptySet(),
    gate: ParentalGateState? = null,
    itemContent: @Composable (T) -> Unit,
) {
    // Locked categories open only through the parental gate; the long-press
    // route toggles locks (creating the PIN in place the first time).
    val selectCategory: (Long?) -> Unit = { id ->
        if (gate != null) {
            gate.open(id) { browser.selectCategory(id) }
        } else {
            browser.selectCategory(id)
        }
    }
    val longPressCategory: ((Long) -> Unit)? = gate?.let { g -> { id -> g.toggleLock(id) } }

    val selectedCategoryId by browser.selectedCategoryId.collectAsStateWithLifecycle()
    val searchActive by browser.searchActive.collectAsStateWithLifecycle()
    val searchQuery by browser.searchQuery.collectAsStateWithLifecycle()
    val items: LazyPagingItems<T> = browser.items.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val layout = catalogLayoutSpec()

    LaunchedEffect(selectedCategoryId, searchActive) {
        gridState.scrollToItem(0)
    }

    val selectedCategoryLabel = when (selectedCategoryId) {
        null -> "All"
        ContentRepository.CATEGORY_BOOKMARKS -> "Bookmarks"
        ContentRepository.CATEGORY_RECENT -> "Recently viewed"
        else -> categories.firstOrNull { it.first == selectedCategoryId }?.second ?: "Category"
    }

    val gridPane: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = if (searchActive) "Search results" else selectedCategoryLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))

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
                    ResponsivePosterSkeleton(
                        posterMinWidthDp = layout.posterMinWidthDp,
                        spacingDp = layout.gridSpacingDp,
                    )
                }
                items.itemCount == 0 -> {
                    EmptyState(
                        message = when {
                            searchActive -> "Nothing matches your search"
                            selectedCategoryId == ContentRepository.CATEGORY_BOOKMARKS ->
                                "No bookmarks yet — use the bookmark button on any poster"
                            selectedCategoryId == ContentRepository.CATEGORY_RECENT ->
                                "Titles you play will appear here"
                            else -> emptyMessage
                        },
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = layout.posterMinWidthDp.dp),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(layout.gridSpacingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(layout.gridSpacingDp.dp),
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

    if (!layout.showCategoryRail) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenPadding()),
        ) {
            CompactCategorySelector(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                searchActive = searchActive,
                onSelectCategory = selectCategory,
                onOpenSearch = browser::openSearch,
                lockedIds = lockedIds,
                onLongPressCategory = longPressCategory,
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
                onSelectCategory = selectCategory,
                onOpenSearch = browser::openSearch,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(layout.categoryRailWidthDp.dp),
                lockedIds = lockedIds,
                onLongPressCategory = longPressCategory,
            )
            Spacer(Modifier.width(24.dp))
            gridPane()
        }
    }

    gate?.let { ParentalGateDialogs(it) }
}

// Shimmer placeholder shown while the first page loads: derives the column
// count from the available width so it mirrors the real GridCells.Adaptive grid.
@Composable
private fun ResponsivePosterSkeleton(
    posterMinWidthDp: Int,
    spacingDp: Int,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val available = maxWidth.value.coerceAtLeast(posterMinWidthDp.toFloat())
        val columns = (available / (posterMinWidthDp + spacingDp))
            .toInt()
            .coerceIn(2, 8)

        Column(verticalArrangement = Arrangement.spacedBy(spacingDp.dp)) {
            repeat(2) {
                PosterGridSkeleton(columns = columns, spacingDp = spacingDp)
            }
        }
    }
}
