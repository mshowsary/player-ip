package com.novaplay.tv.ui.series

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
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
import com.novaplay.tv.data.db.Episode
import com.novaplay.tv.data.db.WatchProgress
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.ShimmerBox
import com.novaplay.tv.ui.movies.formatPosition
import com.novaplay.tv.ui.theme.NovaBackground
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.screenPadding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeriesDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contentRepository: ContentRepository,
) : ViewModel() {
    private val seriesId: Long = checkNotNull(savedStateHandle["seriesId"])

    val series = contentRepository.observeSeries(seriesId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val episodesBySeason: StateFlow<Map<Int, List<Episode>>> =
        contentRepository.episodes(seriesId)
            .map { list -> list.groupBy { it.season }.toSortedMap() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val progressByEpisode: StateFlow<Map<Long, WatchProgress>> =
        contentRepository.watchProgressForSeries(seriesId)
            .map { list -> list.associateBy { it.remoteId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _selectedSeason = MutableStateFlow<Int?>(null)
    val selectedSeason: StateFlow<Int?> = _selectedSeason.asStateFlow()

    private val _loadingEpisodes = MutableStateFlow(true)
    val loadingEpisodes: StateFlow<Boolean> = _loadingEpisodes.asStateFlow()

    init {
        viewModelScope.launch {
            contentRepository.seriesById(seriesId)?.let { contentRepository.refreshSeriesInfo(it) }
            _loadingEpisodes.value = false
        }
    }

    fun selectSeason(season: Int) {
        _selectedSeason.value = season
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesDetailsScreen(
    onPlayEpisode: (episodeId: Long, resume: Boolean) -> Unit,
    viewModel: SeriesDetailsViewModel = hiltViewModel(),
) {
    val series by viewModel.series.collectAsStateWithLifecycle()
    val episodesBySeason by viewModel.episodesBySeason.collectAsStateWithLifecycle()
    val progressByEpisode by viewModel.progressByEpisode.collectAsStateWithLifecycle()
    val selectedSeasonState by viewModel.selectedSeason.collectAsStateWithLifecycle()
    val loadingEpisodes by viewModel.loadingEpisodes.collectAsStateWithLifecycle()

    val current = series ?: return
    val seasons = episodesBySeason.keys.toList()
    val selectedSeason = selectedSeasonState ?: seasons.firstOrNull()
    val episodes = selectedSeason?.let { episodesBySeason[it] }.orEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = current.backdropUrl ?: current.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.25f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(NovaBackground.copy(alpha = 0.55f), NovaBackground),
                    ),
                ),
        )

        if (isCompactWidth()) {
            // Portrait phones: one scrolling list — header, plot, season tabs,
            // then the episodes.
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(screenPadding()),
            ) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AsyncImage(
                            model = current.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(120.dp)
                                .height(180.dp)
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
                            Text(
                                text = listOfNotNull(
                                    current.year,
                                    current.rating?.let { "★ %.1f".format(it) },
                                ).joinToString("   ·   "),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                current.plot?.let { plot ->
                    item(key = "plot") {
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(count = seasons.size, key = { seasons[it] }) { index ->
                                val season = seasons[index]
                                SeasonTab(
                                    season = season,
                                    selected = season == selectedSeason,
                                    onClick = { viewModel.selectSeason(season) },
                                )
                            }
                        }
                    }
                }
                when {
                    loadingEpisodes && episodes.isEmpty() -> item(key = "skeleton") {
                        EpisodeListSkeleton()
                    }
                    episodes.isEmpty() -> item(key = "empty") {
                        Text(
                            text = "No episodes available",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                    else -> items(count = episodes.size, key = { episodes[it].id }) { index ->
                        val episode = episodes[index]
                        val progress = progressByEpisode[episode.remoteEpisodeId]
                        EpisodeRow(
                            episode = episode,
                            progress = progress,
                            onClick = {
                                onPlayEpisode(
                                    episode.id,
                                    progress.isEpisodeResumable(),
                                )
                            },
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(screenPadding()),
            ) {
                Column(modifier = Modifier.width(240.dp)) {
                    AsyncImage(
                        model = current.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(240.dp)
                            .height(360.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = listOfNotNull(
                            current.year,
                            current.rating?.let { "★ %.1f".format(it) },
                        ).joinToString("   ·   "),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.width(36.dp))

                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = current.name,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    current.plot?.let { plot ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = plot,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    if (seasons.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.focusRestorer(),
                        ) {
                            items(count = seasons.size, key = { seasons[it] }) { index ->
                                val season = seasons[index]
                                SeasonTab(
                                    season = season,
                                    selected = season == selectedSeason,
                                    onClick = { viewModel.selectSeason(season) },
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    when {
                        loadingEpisodes && episodes.isEmpty() -> EpisodeListSkeleton()
                        episodes.isEmpty() -> Text(
                            text = "No episodes available",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                        else -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxHeight()
                                .focusRestorer(),
                        ) {
                            items(count = episodes.size, key = { episodes[it].id }) { index ->
                                val episode = episodes[index]
                                val progress = progressByEpisode[episode.remoteEpisodeId]
                                EpisodeRow(
                                    episode = episode,
                                    progress = progress,
                                    onClick = {
                                        onPlayEpisode(
                                            episode.id,
                                            progress.isEpisodeResumable(),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun WatchProgress?.isEpisodeResumable(): Boolean {
    if (this == null || durationMs <= 0) return false
    return positionMs >= 60_000 && positionMs < durationMs * 95 / 100
}

@Composable
private fun SeasonTab(season: Int, selected: Boolean, onClick: () -> Unit) {
    NovaClickable(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        focusedScale = 1.05f,
    ) {
        Text(
            text = "Season $season",
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: WatchProgress?,
    onClick: () -> Unit,
) {
    NovaClickable(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp),
        focusedScale = 1.02f,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 118.dp, height = 64.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = episode.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "E${episode.episodeNum}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(52.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = listOfNotNull(
                        episode.durationSecs?.let { "${it / 60} min" },
                        progress?.takeIf { it.durationMs > 0 && it.positionMs > 0 }
                            ?.let { "watched ${formatPosition(it.positionMs)}" },
                    ).joinToString("  ·  ")
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Thin progress bar for partially-watched episodes.
            val fraction = progress
                ?.takeIf { it.durationMs > 0 }
                ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
            if (fraction != null && fraction > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeListSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(4) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
            )
        }
    }
}
