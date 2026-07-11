package com.novaplay.tv.ui.movies

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novaplay.tv.ui.components.CatalogGridScreen
import com.novaplay.tv.ui.components.PosterCard

@Composable
fun MoviesScreen(
    onOpenMovie: (movieId: Long) -> Unit,
    viewModel: MoviesViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsStateWithLifecycle()

    CatalogGridScreen(
        categories = categories.map { it.id to it.name },
        browser = viewModel.browser,
        searchPlaceholder = "Search movies…",
        emptyMessage = "No movies in this category yet",
        itemId = { it.id },
    ) { movie ->
        PosterCard(
            title = movie.name,
            posterUrl = movie.posterUrl,
            onClick = { onOpenMovie(movie.id) },
            bookmarked = movie.streamId in bookmarkedIds,
            onToggleBookmark = { viewModel.toggleBookmark(movie) },
        )
    }
}
