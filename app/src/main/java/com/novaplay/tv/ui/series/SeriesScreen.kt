package com.novaplay.tv.ui.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novaplay.tv.ui.components.CatalogGridScreen
import com.novaplay.tv.ui.components.PosterCard
import com.novaplay.tv.ui.components.rememberParentalGate

/**
 * Series catalog: category tabs, search, and a poster grid delegated to
 * [CatalogGridScreen] (Paging 3 windows over Room, including D-pad focus
 * handling). Cards open the series details and can toggle a bookmark.
 */
@Composable
fun SeriesScreen(
    onOpenSeries: (seriesId: Long) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsStateWithLifecycle()
    val parental by viewModel.parental.collectAsStateWithLifecycle()
    val gate = rememberParentalGate(
        state = parental,
        onUnlock = viewModel::unlockParental,
        onSetPin = viewModel::setParentalPin,
        onToggleLock = viewModel::toggleCategoryLock,
    )

    CatalogGridScreen(
        title = "Series",
        categories = categories.map { it.id to it.name },
        browser = viewModel.browser,
        searchPlaceholder = "Search series…",
        emptyMessage = "No series in this category yet",
        itemId = { it.id },
        lockedIds = parental.lockedIds,
        gate = gate,
    ) { series ->
        PosterCard(
            title = series.name,
            posterUrl = series.posterUrl,
            onClick = { onOpenSeries(series.id) },
            bookmarked = series.seriesId in bookmarkedIds,
            onToggleBookmark = { viewModel.toggleBookmark(series) },
        )
    }
}
