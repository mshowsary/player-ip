package com.novaplay.tv.ui.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novaplay.tv.ui.components.CatalogGridScreen
import com.novaplay.tv.ui.components.PosterCard

@Composable
fun SeriesScreen(
    onOpenSeries: (seriesId: Long) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsStateWithLifecycle()

    CatalogGridScreen(
        categories = categories.map { it.id to it.name },
        browser = viewModel.browser,
        searchPlaceholder = "Search series…",
        emptyMessage = "No series in this category yet",
        itemId = { it.id },
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
