package com.novaplay.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.R
import com.novaplay.tv.data.repo.SyncStatus
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.PulsingDot
import com.novaplay.tv.ui.theme.NovaAccent
import com.novaplay.tv.ui.theme.NovaAccentGradient
import com.novaplay.tv.ui.theme.NovaGlassHighlight
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HomeCard(
    val label: String,
    val icon: ImageVector,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val liveFocus = remember { FocusRequester() }
    val isTv = isTvDevice()

    LaunchedEffect(Unit) { if (isTv) liveFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(screenPadding()),
    ) {
        // Header: identity left, status right — small and subdued.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Gradient wordmark — the brand moment of the screen.
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = TextStyle(
                    brush = NovaAccentGradient,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 5.sp,
                ),
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                val compact = isCompactWidth()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    playlist?.let { active ->
                        Text(
                            text = active.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Expiry doesn't fit next to the wordmark on phones.
                        if (!compact) {
                            active.expiryEpochSec?.let { expiry ->
                                Text(
                                    text = "  ·  expires ${formatDate(expiry * 1000)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = "  ·  ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Clock()
                }
            }
        }

        // The app's face: one confident row of five cards. Cards shrink to
        // the available width (landscape phones are narrower than TVs) so
        // the row never wraps or clips; tiny screens still wrap gracefully.
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BoxWithConstraints {
                val cardWidth = minOf(156.dp, (maxWidth - 80.dp) / 5)
                val cardHeight = cardWidth * 172f / 156f
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    HomeCardItem(
                        card = HomeCard("Live TV", Icons.Default.LiveTv),
                        onClick = onOpenLive,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        modifier = Modifier.focusRequester(liveFocus),
                    )
                    HomeCardItem(HomeCard("Movies", Icons.Default.Movie), onOpenMovies, cardWidth, cardHeight)
                    HomeCardItem(HomeCard("Series", Icons.Default.VideoLibrary), onOpenSeries, cardWidth, cardHeight)
                    HomeCardItem(HomeCard("Change Playlist", Icons.Default.SwapHoriz), onOpenPlaylists, cardWidth, cardHeight)
                    HomeCardItem(HomeCard("Settings", Icons.Default.Settings), onOpenSettings, cardWidth, cardHeight)
                }
            }
        }

        // Sync activity, kept quiet at the bottom edge.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(24.dp),
        ) {
            when (val status = syncStatus) {
                is SyncStatus.Syncing -> {
                    PulsingDot(Modifier.size(8.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Syncing ${status.step}…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is SyncStatus.Failed -> Text(
                    text = "Last sync failed — content may be out of date",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                SyncStatus.Idle -> Unit
            }
        }
    }
}

@Composable
private fun HomeCardItem(
    card: HomeCard,
    onClick: () -> Unit,
    cardWidth: Dp = 156.dp,
    cardHeight: Dp = 172.dp,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = onClick,
        modifier = modifier.size(width = cardWidth, height = cardHeight),
        shape = RoundedCornerShape(20.dp),
        focusedScale = 1.09f,
    ) {
        // Faint top sheen makes the card read as glass instead of a flat tile.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(NovaGlassHighlight),
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .border(1.5.dp, NovaAccentGradient, CircleShape)
                    .background(NovaAccent.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = card.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.4.sp,
            )
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val format = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Text(
        text = format.format(Date(now)),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
