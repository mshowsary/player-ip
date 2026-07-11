package com.novaplay.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.R
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedFeature
import com.novaplay.tv.data.repo.SyncStatus
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.PulsingDot
import com.novaplay.tv.ui.theme.NovaAccent
import com.novaplay.tv.ui.theme.NovaAccentGradient
import com.novaplay.tv.ui.theme.NovaGlassHighlight
import com.novaplay.tv.ui.theme.WindowWidthClass
import com.novaplay.tv.ui.theme.appLayoutInfo
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class HomeCard(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val managedFeature: ManagedFeature? = null,
)

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
    val managedAccess by viewModel.managedAccess.collectAsStateWithLifecycle()
    val firstCardFocus = remember { FocusRequester() }
    val isTv = isTvDevice()
    val layoutInfo = appLayoutInfo()

    val cards = remember(
        managedAccess,
        onOpenLive,
        onOpenMovies,
        onOpenSeries,
        onOpenPlaylists,
        onOpenSettings,
    ) {
        listOf(
            HomeCard("Live TV", Icons.Default.LiveTv, onOpenLive, ManagedFeature.LIVE),
            HomeCard("Movies", Icons.Default.Movie, onOpenMovies, ManagedFeature.MOVIES),
            HomeCard("Series", Icons.Default.VideoLibrary, onOpenSeries, ManagedFeature.SERIES),
            HomeCard("Playlists", Icons.Default.SwapHoriz, onOpenPlaylists),
            HomeCard("Settings", Icons.Default.Settings, onOpenSettings),
        ).filter { card -> card.managedFeature?.let(managedAccess::allows) ?: true }
    }

    val firstCardLabel = cards.firstOrNull()?.label
    LaunchedEffect(isTv, firstCardLabel) {
        if (isTv && firstCardLabel != null) runCatching { firstCardFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(screenPadding()),
    ) {
        if (layoutInfo.widthClass == WindowWidthClass.COMPACT) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Wordmark()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    playlist?.let { active ->
                        Text(
                            text = active.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Clock()
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Wordmark()
                Spacer(Modifier.weight(1f))
                playlist?.let { active ->
                    Text(
                        text = active.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    active.expiryEpochSec?.let { expiry ->
                        Text(
                            text = "  ·  expires ${formatDate(expiry * 1000)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

        if (managedAccess.shouldShowHomeNotice()) {
            Spacer(Modifier.height(14.dp))
            ManagedAccessNotice(managedAccess)
        }

        val minimumCardWidth = when (layoutInfo.widthClass) {
            WindowWidthClass.COMPACT -> 132.dp
            WindowWidthClass.MEDIUM -> 148.dp
            WindowWidthClass.EXPANDED -> 164.dp
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minimumCardWidth),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 20.dp),
        ) {
            items(cards, key = { it.label }) { card ->
                HomeCardItem(
                    card = card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.94f)
                        .then(
                            if (card.label == firstCardLabel) {
                                Modifier.focusRequester(firstCardFocus)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }

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

private fun ManagedAccessPolicy.shouldShowHomeNotice(): Boolean =
    isManaged && (isBlocked || !allowLive || !allowMovies || !allowSeries)

@Composable
private fun ManagedAccessNotice(policy: ManagedAccessPolicy) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .border(
                1.dp,
                if (policy.isBlocked) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                },
                MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = if (policy.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = policy.statusLabel(),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = policy.message ?: managedServicesSummary(policy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        policy.supportCode?.let { code ->
            Text(
                text = code,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun managedServicesSummary(policy: ManagedAccessPolicy): String {
    val available = buildList {
        if (policy.allowLive) add("Live")
        if (policy.allowMovies) add("Movies")
        if (policy.allowSeries) add("Series")
    }
    return if (available.isEmpty()) {
        "No managed streaming services are currently available."
    } else {
        "Available services: ${available.joinToString()}"
    }
}

@Composable
private fun Wordmark() {
    Text(
        text = stringResource(R.string.app_name).uppercase(),
        style = TextStyle(
            brush = NovaAccentGradient,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 5.sp,
        ),
    )
}

@Composable
private fun HomeCardItem(
    card: HomeCard,
    modifier: Modifier = Modifier,
) {
    NovaClickable(
        onClick = card.onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        focusedScale = 1.07f,
    ) {
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
                    .size(58.dp)
                    .border(1.5.dp, NovaAccentGradient, CircleShape)
                    .background(NovaAccent.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = card.label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = card.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
