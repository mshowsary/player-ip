package com.novaplay.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.repo.ContentRepository
import com.novaplay.tv.ui.components.EmptyState
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.ShimmerBox
import com.novaplay.tv.ui.theme.catalogLayoutSpec
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding

/**
 * Adaptive Live browser used by phones, tablets, desktop-sized windows, and TV.
 * Compact touch windows use a smart category selector; larger windows and TV
 * use a fixed category rail so the channel list remains comfortably readable.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ResponsiveLiveScreen(
    onPlayChannel: (channelId: Long, categoryId: Long) -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val searchActive by viewModel.searchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val digitBuffer by viewModel.digitBuffer.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val channelListState = rememberLazyListState()
    val layout = catalogLayoutSpec()

    LaunchedEffect(Unit) {
        viewModel.jumpToIndex.collect { index ->
            channelListState.scrollToItem(index.coerceAtLeast(0))
        }
    }

    LaunchedEffect(selectedCategoryId, searchActive) {
        channelListState.scrollToItem(0)
    }

    val categoryLabel = when (selectedCategoryId) {
        null -> "All channels"
        ContentRepository.CATEGORY_BOOKMARKS -> "Bookmarks"
        ContentRepository.CATEGORY_RECENT -> "Recently viewed"
        else -> categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Channels"
    }

    val channelPane: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(text = "Live TV", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = if (searchActive) "Search results" else categoryLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))

            if (searchActive) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClose = viewModel::closeSearch,
                    placeholder = "Search channels…",
                )
                Spacer(Modifier.height(12.dp))
            }

            when {
                channels.itemCount == 0 && channels.loadState.refresh is LoadState.Loading -> {
                    ResponsiveChannelSkeleton(rowHeightDp = layout.liveRowHeightDp)
                }
                channels.itemCount == 0 -> {
                    EmptyState(
                        message = when {
                            searchActive -> "No channels match your search"
                            selectedCategoryId == ContentRepository.CATEGORY_BOOKMARKS ->
                                "No bookmarks yet — use the bookmark button on any channel"
                            selectedCategoryId == ContentRepository.CATEGORY_RECENT ->
                                "Channels you watch will appear here"
                            else -> "No channels here yet"
                        },
                    )
                }
                else -> {
                    LazyColumn(
                        state = channelListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRestorer(),
                    ) {
                        items(
                            count = channels.itemCount,
                            key = channels.itemKey { it.id },
                        ) { index ->
                            channels[index]?.let { channel ->
                                ResponsiveChannelRow(
                                    channel = channel,
                                    bookmarked = channel.streamId in bookmarkedIds,
                                    rowHeightDp = layout.liveRowHeightDp,
                                    logoSizeDp = layout.liveLogoSizeDp,
                                    onClick = {
                                        onPlayChannel(channel.id, selectedCategoryId ?: -1L)
                                    },
                                    onToggleBookmark = { viewModel.toggleBookmark(channel) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val digit = event.key.liveDigitOrNull()
                if (digit != null && event.type == KeyEventType.KeyDown) {
                    viewModel.onDigit(digit)
                    true
                } else {
                    false
                }
            }
            .padding(screenPadding()),
    ) {
        if (!layout.showCategoryRail) {
            Column(modifier = Modifier.fillMaxSize()) {
                CompactCategorySelector(
                    categories = categories.map { it.id to it.name },
                    selectedCategoryId = selectedCategoryId,
                    searchActive = searchActive,
                    onSelectCategory = viewModel::selectCategory,
                    onOpenSearch = viewModel::openSearch,
                )
                Spacer(Modifier.height(12.dp))
                channelPane()
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                CategoryRail(
                    categories = categories.map { it.id to it.name },
                    selectedCategoryId = selectedCategoryId,
                    searchActive = searchActive,
                    onSelectCategory = viewModel::selectCategory,
                    onOpenSearch = viewModel::openSearch,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(layout.categoryRailWidthDp.dp),
                )
                Spacer(Modifier.width(24.dp))
                channelPane()
            }
        }

        if (digitBuffer.isNotEmpty()) {
            Text(
                text = digitBuffer,
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ResponsiveChannelRow(
    channel: LiveChannel,
    bookmarked: Boolean,
    rowHeightDp: Int,
    logoSizeDp: Int,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    NovaClickable(
        onClick = onClick,
        onLongClick = onToggleBookmark,
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeightDp.dp),
        focusedScale = 1.02f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(
                text = channel.num.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
            )
            Box(
                modifier = Modifier
                    .size(logoSizeDp.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LiveBookmarkButton(
                bookmarked = bookmarked,
                onToggle = onToggleBookmark,
            )
        }
    }
}

@Composable
private fun LiveBookmarkButton(
    bookmarked: Boolean,
    onToggle: () -> Unit,
) {
    val isTv = isTvDevice()
    val interaction = if (isTv) {
        Modifier.pointerInput(Unit) { detectTapGestures { onToggle() } }
    } else {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = if (bookmarked) "Remove bookmark" else "Add bookmark",
            onClick = onToggle,
        )
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = if (bookmarked) "Remove bookmark" else "Add bookmark",
            tint = if (bookmarked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun ResponsiveChannelSkeleton(rowHeightDp: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(8) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeightDp.dp),
            )
        }
    }
}

private fun Key.liveDigitOrNull(): Char? = when (this) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}
