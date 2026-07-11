package com.novaplay.tv.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.novaplay.tv.data.db.WatchProgress
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.theme.NovaBackground
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
) : ViewModel() {
    private val movieId: Long = checkNotNull(savedStateHandle["movieId"])

    val movie = contentRepository.observeMovie(movieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val progress = contentRepository.observeWatchProgress(WatchProgress.MEDIA_MOVIE, movieId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Details (plot, duration, backdrop) load lazily; failures are fine —
        // the screen simply shows what the catalog sync already had.
        viewModelScope.launch {
            contentRepository.movieById(movieId)?.let { contentRepository.refreshMovieDetails(it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailsScreen(
    onPlay: (movieId: Long, resume: Boolean) -> Unit,
    viewModel: MovieDetailsViewModel = hiltViewModel(),
) {
    val movie by viewModel.movie.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }
    val isTv = isTvDevice()

    val current = movie ?: return
    val resumable = progress.isResumable()

    LaunchedEffect(Unit) { if (isTv) playFocus.requestFocus() }

    val metaLine: @Composable () -> Unit = {
        Text(
            text = listOfNotNull(
                current.year,
                current.rating?.let { "★ %.1f".format(it) },
                current.durationSecs?.let { formatDuration(it) },
                current.genre,
            ).joinToString("   ·   "),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val playButtons: @Composable () -> Unit = {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (resumable) {
                NovaButton(
                    text = "Resume from ${formatPosition(progress!!.positionMs)}",
                    onClick = { onPlay(current.id, true) },
                    modifier = Modifier.focusRequester(playFocus),
                    prominent = true,
                )
                NovaButton(text = "Play", onClick = { onPlay(current.id, false) })
            } else {
                NovaButton(
                    text = "Play",
                    onClick = { onPlay(current.id, false) },
                    modifier = Modifier.focusRequester(playFocus),
                    prominent = true,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = current.backdropUrl ?: current.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.35f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(NovaBackground, NovaBackground.copy(alpha = 0.35f)),
                    ),
                ),
        )

        if (isCompactWidth()) {
            // Portrait phones: poster and title side by side, everything
            // below scrolls.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(screenPadding()),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AsyncImage(
                        model = current.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(130.dp)
                            .height(195.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(
                            text = current.name,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        metaLine()
                    }
                }
                Spacer(Modifier.height(20.dp))
                playButtons()
                current.plot?.let { plot ->
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(screenPadding()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = current.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(220.dp)
                        .height(330.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.width(40.dp))
                Column(modifier = Modifier.widthIn(max = 560.dp)) {
                    Text(
                        text = current.name,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    metaLine()
                    current.plot?.let { plot ->
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    playButtons()
                }
            }
        }
    }
}

// Resume appears once 60 s are watched and disappears past 95 %.
fun WatchProgress?.isResumable(): Boolean {
    if (this == null || durationMs <= 0) return false
    return positionMs >= 60_000 && positionMs < durationMs * 95 / 100
}

fun formatPosition(ms: Long): String {
    val totalSecs = ms / 1000
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatDuration(secs: Int): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) "${h}h ${m}min" else "${m}min"
}
