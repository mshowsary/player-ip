package com.novaplay.tv.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.novaplay.tv.data.prefs.HomeLayout
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedAccessState
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

/** One hub tile; cards carrying a [managedFeature] are hidden when the policy denies it. */
private data class HomeCard(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val managedFeature: ManagedFeature? = null,
)

/**
 * Hub screen: wordmark/playlist/clock header, an adaptive grid of section
 * cards filtered by the managed-access policy, and a sync status footer. On TV
 * the first visible card takes initial focus, and each card gets an inset so
 * the focus scale-up never clips at the grid edges.
 */
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
    val homeLayout by viewModel.homeLayout.collectAsStateWithLifecycle()
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
            HomeCard(R.string.home_live_tv, Icons.Default.LiveTv, onOpenLive, ManagedFeature.LIVE),
            HomeCard(R.string.home_movies, Icons.Default.Movie, onOpenMovies, ManagedFeature.MOVIES),
            HomeCard(R.string.home_series, Icons.Default.VideoLibrary, onOpenSeries, ManagedFeature.SERIES),
            HomeCard(R.string.home_playlists, Icons.Default.SwapHoriz, onOpenPlaylists),
            HomeCard(R.string.home_settings, Icons.Default.Settings, onOpenSettings),
        ).filter { card -> card.managedFeature?.let(managedAccess::allows) ?: true }
    }

    val firstCardLabelRes = cards.firstOrNull()?.labelRes
    LaunchedEffect(isTv, firstCardLabelRes) {
        if (isTv && firstCardLabelRes != null) runCatching { firstCardFocus.requestFocus() }
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
                            text = "  ·  ${stringResource(R.string.home_expires, formatDate(expiry * 1000))}",
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

        // Cards render in the user-selected arrangement; every variant keeps the
        // same managed filtering, focus-safety insets and initial TV focus.
        val hubModifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        when (homeLayout) {
            HomeLayout.CLASSIC -> ClassicHub(
                cards = cards,
                layoutInfo = layoutInfo,
                isTv = isTv,
                firstCardLabelRes = firstCardLabelRes,
                firstCardFocus = firstCardFocus,
                modifier = hubModifier,
            )
            HomeLayout.HERO -> HeroHub(
                cards = cards,
                layoutInfo = layoutInfo,
                isTv = isTv,
                firstCardLabelRes = firstCardLabelRes,
                firstCardFocus = firstCardFocus,
                modifier = hubModifier,
            )
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
                        text = stringResource(R.string.home_syncing, status.step),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is SyncStatus.Failed -> Text(
                    text = stringResource(R.string.home_last_sync_failed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                SyncStatus.Idle -> Unit
            }
        }
    }
}

// The original adaptive grid of equal cards.
@Composable
private fun ClassicHub(
    cards: List<HomeCard>,
    layoutInfo: com.novaplay.tv.ui.theme.AppLayoutInfo,
    isTv: Boolean,
    firstCardLabelRes: Int?,
    firstCardFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val minimumCardWidth = when (layoutInfo.widthClass) {
        WindowWidthClass.COMPACT -> 132.dp
        WindowWidthClass.MEDIUM -> 148.dp
        WindowWidthClass.EXPANDED -> 164.dp
    }
    val focusSafetyInset = if (isTv) 18.dp else 0.dp
    val gridSpacing = if (isTv) 0.dp else 16.dp
    val compactTv = isTv && layoutInfo.widthClass == WindowWidthClass.COMPACT
    val verticalArrangement = if (compactTv) {
        Arrangement.spacedBy(gridSpacing, Alignment.CenterVertically)
    } else {
        Arrangement.spacedBy(gridSpacing)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minimumCardWidth),
        horizontalArrangement = Arrangement.spacedBy(gridSpacing),
        verticalArrangement = verticalArrangement,
        modifier = modifier.padding(vertical = if (isTv) 0.dp else 20.dp),
    ) {
        items(cards, key = { it.labelRes }) { card ->
            HomeCardItem(
                card = card,
                modifier = Modifier
                    .padding(focusSafetyInset)
                    .fillMaxWidth()
                    .aspectRatio(0.94f)
                    .then(
                        if (card.labelRes == firstCardLabelRes) {
                            Modifier.focusRequester(firstCardFocus)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

// The familiar IPTV arrangement: one dominant panel (Live TV when available)
// with the remaining sections in a small grid beside or below it.
@Composable
private fun HeroHub(
    cards: List<HomeCard>,
    layoutInfo: com.novaplay.tv.ui.theme.AppLayoutInfo,
    isTv: Boolean,
    firstCardLabelRes: Int?,
    firstCardFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val hero = cards.firstOrNull() ?: return
    val rest = cards.drop(1)
    val inset = if (isTv) 18.dp else 0.dp
    val heroFocus = if (hero.labelRes == firstCardLabelRes) {
        Modifier.focusRequester(firstCardFocus)
    } else {
        Modifier
    }

    if (layoutInfo.widthClass == WindowWidthClass.COMPACT) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            HeroCard(
                card = hero,
                modifier = Modifier
                    .padding(inset)
                    .fillMaxWidth()
                    .height(176.dp)
                    .then(heroFocus),
            )
            rest.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { card ->
                        HomeCardItem(
                            card = card,
                            modifier = Modifier
                                .padding(inset)
                                .weight(1f)
                                .aspectRatio(1.5f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.padding(vertical = if (isTv) 12.dp else 20.dp),
        ) {
            HeroCard(
                card = hero,
                modifier = Modifier
                    .padding(inset)
                    .weight(1.15f)
                    .fillMaxHeight()
                    .then(heroFocus),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 0.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTv) 0.dp else 14.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(rest, key = { it.labelRes }) { card ->
                    HomeCardItem(
                        card = card,
                        modifier = Modifier
                            .padding(inset)
                            .fillMaxWidth()
                            .aspectRatio(1.15f),
                    )
                }
            }
        }
    }
}

// The dominant panel of the hero arrangement: accent-washed, oversized icon
// and headline label anchored to the bottom edge.
@Composable
private fun HeroCard(
    card: HomeCard,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(card.labelRes)
    NovaClickable(
        onClick = card.onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        focusedScale = 1.03f,
        accessibilityLabel = label,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(NovaGlassHighlight)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NovaAccent.copy(alpha = 0.20f), Color.Transparent),
                        radius = 900f,
                    ),
                ),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(26.dp)) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(1.5.dp, NovaAccentGradient, CircleShape)
                    .background(NovaAccent.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// The notice only appears for managed playlists that block or hide something.
private fun ManagedAccessPolicy.shouldShowHomeNotice(): Boolean =
    isManaged && (isBlocked || !allowLive || !allowMovies || !allowSeries)

/**
 * Banner summarising the managed-access policy: state, per-feature availability,
 * policy revision, support code and a provider message when present.
 */
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
                text = managedStatusLabel(policy.state),
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

@Composable
private fun managedStatusLabel(state: ManagedAccessState): String = stringResource(
    when (state) {
        ManagedAccessState.UNMANAGED -> R.string.managed_status_personal
        ManagedAccessState.ACTIVE -> R.string.managed_status_active
        ManagedAccessState.SUSPENDED -> R.string.managed_status_paused
        ManagedAccessState.REVOKED -> R.string.managed_status_revoked
    },
)

@Composable
private fun managedServicesSummary(policy: ManagedAccessPolicy): String {
    val available = buildList {
        if (policy.allowLive) add(stringResource(R.string.service_live))
        if (policy.allowMovies) add(stringResource(R.string.service_movies))
        if (policy.allowSeries) add(stringResource(R.string.service_series))
    }
    return if (available.isEmpty()) {
        stringResource(R.string.managed_no_services)
    } else {
        stringResource(R.string.managed_available_services, available.joinToString())
    }
}

/** Uppercase app wordmark drawn with the accent gradient brush. */
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

/** One focusable action with a combined spoken label; the icon is decorative. */
@Composable
private fun HomeCardItem(
    card: HomeCard,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(card.labelRes)
    NovaClickable(
        onClick = card.onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        focusedScale = 1.07f,
        accessibilityLabel = label,
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
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** HH:mm clock that refreshes every 10 s while the screen is composed. */
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

// Locale-aware date used for the playlist expiry in the header.
private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMs))
