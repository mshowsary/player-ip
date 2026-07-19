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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.novaplay.tv.R
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.prefs.HomeLayout
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedAccessState
import com.novaplay.tv.data.repo.ManagedFeature
import com.novaplay.tv.core.DataSize
import com.novaplay.tv.data.repo.SyncStatus
import com.novaplay.tv.ui.components.SyncProgressDialog
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.PulsingDot
import com.novaplay.tv.ui.theme.LocalNovaAccents
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
    onPlayChannel: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val syncModalVisible by viewModel.syncModalVisible.collectAsStateWithLifecycle()
    val managedAccess by viewModel.managedAccess.collectAsStateWithLifecycle()
    val homeLayout by viewModel.homeLayout.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val bookmarkedChannels by viewModel.bookmarkedChannels.collectAsStateWithLifecycle()
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
                recentChannels = recentChannels,
                bookmarkedChannels = bookmarkedChannels,
                onPlayChannel = onPlayChannel,
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
                    // The footer stays after the modal is closed: same step,
                    // plus percent (or bytes while the total is unknown).
                    val progressSuffix = status.progress?.let { progress ->
                        progress.percent?.let { " · $it%" }
                            ?: " · ${DataSize.format(progress.bytesRead)}"
                    }.orEmpty()
                    Text(
                        text = stringResource(R.string.home_syncing, status.step) + progressSuffix,
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

    (syncStatus as? SyncStatus.Syncing)?.let { syncing ->
        if (syncModalVisible) {
            SyncProgressDialog(
                playlistName = playlist?.name,
                status = syncing,
                onClose = viewModel::dismissSyncModal,
            )
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

// The familiar IPTV arrangement: one dominant panel (Live TV when available),
// the remaining sections as compact cards, and real content underneath —
// recently watched and bookmarked channel rails that tune with one press.
@Composable
private fun HeroHub(
    cards: List<HomeCard>,
    layoutInfo: com.novaplay.tv.ui.theme.AppLayoutInfo,
    isTv: Boolean,
    firstCardLabelRes: Int?,
    firstCardFocus: FocusRequester,
    recentChannels: List<LiveChannel>,
    bookmarkedChannels: List<LiveChannel>,
    onPlayChannel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hero = cards.firstOrNull() ?: return
    val rest = cards.drop(1)
    val inset = if (isTv) 10.dp else 0.dp
    // TV viewing distance wants larger targets; phones keep the tight rhythm.
    val sectionHeight = if (isTv) 96.dp else 84.dp
    // Side-by-side needs real width for the card labels: only expanded
    // windows (TVs, large tablets) qualify — landscape phones stack, otherwise
    // the right-hand pair column truncates names to their first letters.
    val stacked = layoutInfo.widthClass != WindowWidthClass.EXPANDED
    val heroFocus = if (hero.labelRes == firstCardLabelRes) {
        Modifier.focusRequester(firstCardFocus)
    } else {
        Modifier
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp),
    ) {
        if (stacked) {
            HeroCard(
                card = hero,
                large = isTv,
                modifier = Modifier
                    .padding(inset)
                    .fillMaxWidth()
                    .height(if (isTv) 196.dp else 150.dp)
                    .then(heroFocus),
            )
            rest.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { card ->
                        SectionCardCompact(
                            card = card,
                            height = sectionHeight,
                            modifier = Modifier
                                .padding(inset)
                                .weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HeroCard(
                    card = hero,
                    large = isTv,
                    modifier = Modifier
                        .padding(inset)
                        .weight(1.25f)
                        .height(
                            // Matches the two stacked compact cards beside it.
                            sectionHeight * 2 + 12.dp + inset * 2,
                        )
                        .then(heroFocus),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    rest.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { card ->
                                SectionCardCompact(
                                    card = card,
                                    height = sectionHeight,
                                    narrow = true,
                                    modifier = Modifier
                                        .padding(inset)
                                        .weight(1f),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Real content under the sections — this is what keeps the hub from
        // looking empty, exactly like the established players.
        ChannelRail(
            title = "Continue watching",
            channels = recentChannels,
            inset = inset,
            tv = isTv,
            onPlayChannel = onPlayChannel,
        )
        ChannelRail(
            title = "Bookmarks",
            channels = bookmarkedChannels,
            inset = inset,
            tv = isTv,
            onPlayChannel = onPlayChannel,
        )
    }
}

// Compact section entry with a fixed height, so the label can never be
// squeezed out by focus insets or aspect-ratio math on any screen.
@Composable
private fun SectionCardCompact(
    card: HomeCard,
    modifier: Modifier = Modifier,
    height: Dp = 84.dp,
    narrow: Boolean = false,
) {
    val label = stringResource(card.labelRes)
    NovaClickable(
        onClick = card.onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.04f,
        accessibilityLabel = label,
    ) {
        if (narrow) {
            // Beside the hero, half a column is too tight for icon + word:
            // the full name on one centered line beats decorated truncation
            // ("Playlist / s" is worse than no icon).
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 10.dp),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .border(1.5.dp, LocalNovaAccents.current.gradient, CircleShape)
                        .background(
                            LocalNovaAccents.current.accent.copy(alpha = 0.08f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// The dominant panel: accent wash, headline on the left, oversized watermark
// icon on the right — balanced at any height instead of a mostly-empty box.
@Composable
private fun HeroCard(
    card: HomeCard,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val label = stringResource(card.labelRes)
    val accents = LocalNovaAccents.current
    NovaClickable(
        onClick = card.onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        focusedScale = 1.02f,
        accessibilityLabel = label,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(NovaGlassHighlight)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accents.accent.copy(alpha = 0.22f),
                            accents.accentAlt.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 24.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = if (large) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(width = if (large) 58.dp else 46.dp, height = 4.dp)
                        .background(accents.gradient, RoundedCornerShape(2.dp)),
                )
            }
            Box(
                modifier = Modifier
                    .size(if (large) 96.dp else 84.dp)
                    .border(1.5.dp, accents.gradient, CircleShape)
                    .background(accents.accent.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (large) 46.dp else 40.dp),
                )
            }
        }
    }
}

// One horizontal rail of channels; hidden entirely while it has no content.
@Composable
private fun ChannelRail(
    title: String,
    channels: List<LiveChannel>,
    inset: Dp,
    tv: Boolean,
    onPlayChannel: (Long) -> Unit,
) {
    if (channels.isEmpty()) return
    val tileWidth = if (tv) 142.dp else 124.dp
    val tileHeight = if (tv) 106.dp else 96.dp
    val logoBox = if (tv) 52.dp else 46.dp
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = title,
            style = if (tv) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            modifier = Modifier.padding(start = inset, bottom = 10.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(channels, key = { it.id }) { channel ->
                NovaClickable(
                    onClick = { onPlayChannel(channel.id) },
                    modifier = Modifier
                        .padding(inset)
                        .size(width = tileWidth, height = tileHeight),
                    shape = RoundedCornerShape(14.dp),
                    focusedScale = 1.06f,
                    accessibilityLabel = channel.name,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(logoBox)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(logoBox - 6.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }
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
            brush = LocalNovaAccents.current.gradient,
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
                    .border(1.5.dp, LocalNovaAccents.current.gradient, CircleShape)
                    .background(LocalNovaAccents.current.accent.copy(alpha = 0.08f), CircleShape),
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
