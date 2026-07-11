package com.novaplay.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.novaplay.tv.ui.theme.NovaAccentGradient
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding

/**
 * Live TV browser: category rail (chips on compact widths) beside a paged
 * channel list. Remote number keys buffer digits and jump the list to that
 * channel number; group changes reset the scroll, and focusRestorer returns
 * D-pad focus to the last-focused row when coming back from the player.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(
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

    // Digit-jump lands the scroll window on the requested channel number.
    LaunchedEffect(Unit) {
        viewModel.jumpToIndex.collect { index ->
            channelListState.scrollToItem(index.coerceAtLeast(0))
        }
    }

    // Every group change starts the list back at the top.
    LaunchedEffect(selectedCategoryId, searchActive) {
        channelListState.scrollToItem(0)
    }

    // Channel pane shared by the wide (rail beside list) and compact
    // (chips above list) layouts.
    val channelPane: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
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
                channels.itemCount == 0 && channels.loadState.refresh is androidx.paging.LoadState.Loading -> {
                    ChannelListSkeleton()
                }
                channels.itemCount == 0 -> {
                    EmptyState(
                        message = when {
                            searchActive -> "No channels match your search"
                            selectedCategoryId == ContentRepository.CATEGORY_BOOKMARKS ->
                                "No bookmarks yet — tap the bookmark icon on any channel"
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
                                ChannelRow(
                                    channel = channel,
                                    bookmarked = channel.streamId in bookmarkedIds,
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
                val digit = event.key.toDigitOrNull()
                if (digit != null && event.type == KeyEventType.KeyDown) {
                    viewModel.onDigit(digit)
                    true
                } else {
                    false
                }
            }
            .padding(screenPadding()),
    ) {
        if (isCompactWidth()) {
            Column(modifier = Modifier.fillMaxSize()) {
                CategoryChipsRow(
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
                        .fillMaxWidth(0.3f),
                )

                Spacer(Modifier.width(24.dp))

                channelPane()
            }
        }

        // Typed channel number, echoed large while collecting digits.
        if (digitBuffer.isNotEmpty()) {
            Text(
                text = digitBuffer,
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Vertical category list for wide layouts: Search, All, Bookmarks, Recently
 * Viewed, then the provider categories. On TV it claims initial focus exactly
 * once per back-stack entry so focus restoration wins on return.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CategoryRail(
    categories: List<Pair<Long, String>>,
    selectedCategoryId: Long?,
    searchActive: Boolean,
    onSelectCategory: (Long?) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sensible initial focus exactly once per back-stack entry: returning from
    // a player must restore the previously focused item, not steal focus back.
    val railFocus = remember { FocusRequester() }
    val isTv = isTvDevice()
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (isTv && !initialFocusDone) {
            initialFocusDone = true
            runCatching { railFocus.requestFocus() }
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.focusRestorer(),
    ) {
        item(key = "search") {
            NovaClickable(
                onClick = onOpenSearch,
                modifier = Modifier
                    .focusRequester(railFocus)
                    .fillMaxWidth()
                    .height(52.dp),
                containerColor = if (searchActive) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(text = "Search", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        item(key = "all") {
            CategoryRow(
                name = "All",
                selected = !searchActive && selectedCategoryId == null,
                onClick = { onSelectCategory(null) },
            )
        }

        item(key = "bookmarks") {
            CategoryRow(
                name = "Bookmarks",
                selected = !searchActive && selectedCategoryId == ContentRepository.CATEGORY_BOOKMARKS,
                onClick = { onSelectCategory(ContentRepository.CATEGORY_BOOKMARKS) },
            )
        }

        item(key = "recent") {
            CategoryRow(
                name = "Recently Viewed",
                selected = !searchActive && selectedCategoryId == ContentRepository.CATEGORY_RECENT,
                onClick = { onSelectCategory(ContentRepository.CATEGORY_RECENT) },
            )
        }

        items(count = categories.size, key = { categories[it].first }) { index ->
            val (id, name) = categories[index]
            CategoryRow(
                name = name,
                selected = !searchActive && selectedCategoryId == id,
                onClick = { onSelectCategory(id) },
            )
        }
    }
}

// Compact-width replacement for the category rail: one horizontal strip of
// pill chips (search first) above the content list.
@Composable
fun CategoryChipsRow(
    categories: List<Pair<Long, String>>,
    selectedCategoryId: Long?,
    searchActive: Boolean,
    onSelectCategory: (Long?) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        item(key = "search") {
            CategoryChip(selected = searchActive, onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        item(key = "all") {
            CategoryChip(selected = !searchActive && selectedCategoryId == null, onClick = {
                onSelectCategory(null)
            }) {
                ChipLabel(text = "All", selected = !searchActive && selectedCategoryId == null)
            }
        }
        item(key = "bookmarks") {
            val selected = !searchActive && selectedCategoryId == ContentRepository.CATEGORY_BOOKMARKS
            CategoryChip(selected = selected, onClick = {
                onSelectCategory(ContentRepository.CATEGORY_BOOKMARKS)
            }) {
                ChipLabel(text = "Bookmarks", selected = selected)
            }
        }
        item(key = "recent") {
            val selected = !searchActive && selectedCategoryId == ContentRepository.CATEGORY_RECENT
            CategoryChip(selected = selected, onClick = {
                onSelectCategory(ContentRepository.CATEGORY_RECENT)
            }) {
                ChipLabel(text = "Recently Viewed", selected = selected)
            }
        }
        items(count = categories.size, key = { categories[it].first }) { index ->
            val (id, name) = categories[index]
            val selected = !searchActive && selectedCategoryId == id
            CategoryChip(selected = selected, onClick = { onSelectCategory(id) }) {
                ChipLabel(text = name, selected = selected)
            }
        }
    }
}

// Focusable pill container for a single chip in the compact strip.
@Composable
private fun CategoryChip(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    NovaClickable(
        onClick = onClick,
        // Selected chips wear the signature gradient ring.
        modifier = if (selected) {
            Modifier.border(1.dp, NovaAccentGradient, RoundedCornerShape(50))
        } else {
            Modifier
        },
        shape = RoundedCornerShape(50),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        focusedScale = 1.05f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            content()
        }
    }
}

// Single-line chip caption, tinted primary while its chip is selected.
@Composable
private fun ChipLabel(text: String, selected: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

// Full-width focusable entry in the category rail; the selected row shows a
// primary indicator bar at its leading edge.
@Composable
private fun CategoryRow(name: String, selected: Boolean, onClick: () -> Unit) {
    NovaClickable(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        focusedScale = 1.04f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 16.dp),
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 20.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One channel entry: number, logo, name and bookmark state. OK/click plays
 * the channel; long-press OK (or tapping the icon on touch) toggles the
 * bookmark.
 */
@Composable
private fun ChannelRow(
    channel: LiveChannel,
    bookmarked: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    NovaClickable(
        onClick = onClick,
        // TV remotes: long-press OK toggles the bookmark.
        onLongClick = onToggleBookmark,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        focusedScale = 1.02f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = channel.num.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp),
            )
            // Logo on a subtle dark chip; Coil decodes at chip size only.
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
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
            BookmarkToggle(bookmarked = bookmarked, onToggle = onToggleBookmark)
        }
    }
}

// Touch affordance: not focusable, so TV D-pad navigation is unaffected
// (remotes use long-press OK instead).
@Composable
fun BookmarkToggle(
    bookmarked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .pointerInput(Unit) { detectTapGestures { onToggle() } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = if (bookmarked) "Remove bookmark" else "Add bookmark",
            tint = if (bookmarked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

// Shimmer placeholder rows shown while the first channel page loads.
@Composable
private fun ChannelListSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(8) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
        }
    }
}

// Shared search input for Live/Movies/Series panes; focusing it brings up the
// standard TV on-screen keyboard.
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Grab focus (and the IME) when search opens — but not again when
    // recomposing on return from the player.
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Back && event.type == KeyEventType.KeyUp -> {
                        onClose()
                        true
                    }
                    // The text field traps D-pad; hand focus to the results
                    // list explicitly.
                    event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown -> {
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            // Touch affordance to leave search (TV remotes use BACK).
            NovaClickable(
                onClick = onClose,
                modifier = Modifier.size(36.dp),
                shape = MaterialTheme.shapes.small,
                containerColor = Color.Transparent,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp),
                )
            }
        }
    }
}

// Maps number-row and numpad keys to their digit for channel-number entry.
private fun Key.toDigitOrNull(): Char? = when (this) {
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
