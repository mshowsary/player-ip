package com.novaplay.tv.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.R
import com.novaplay.tv.data.db.LiveChannel
import com.novaplay.tv.data.epg.GuideLaneItem
import com.novaplay.tv.data.epg.GuideTimeline
import com.novaplay.tv.ui.components.EmptyState
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.ShimmerBox
import com.novaplay.tv.ui.theme.screenPadding
import kotlinx.coroutines.flow.Flow
import java.text.DateFormat
import java.util.Date

private val CHANNEL_COLUMN_WIDTH = 168.dp
private val ROW_HEIGHT = 52.dp
private val RULER_HEIGHT = 28.dp
private val SLOT_WIDTH = (GuideTimeline.SLOT_MINUTES * GuideTimeline.DP_PER_MINUTE).dp

/**
 * Grid TV guide: a frozen half-day timeline with one lane per channel. All
 * lanes and the time ruler share a single horizontal scroll state so columns
 * stay aligned; vertical channel paging reuses the windowed Live pager. D-pad
 * moves cell-to-cell (focus updates the detail strip); OK or tap tunes the
 * channel. Works identically on TV, tablets and phones — only sizes differ.
 */
@Composable
fun GuideScreen(
    onPlayChannel: (channelId: Long) -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val nowMs by viewModel.nowMs.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val timelineScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Land the viewport just before "now" once, so the airing column is visible
    // without hiding the tail of the current programme.
    LaunchedEffect(Unit) {
        val targetMinutes =
            (GuideTimeline.nowOffsetMinutes(viewModel.windowStartMs, System.currentTimeMillis()) -
                GuideTimeline.SLOT_MINUTES).coerceAtLeast(0)
        timelineScroll.scrollTo(
            with(density) { (targetMinutes * GuideTimeline.DP_PER_MINUTE).dp.toPx() }.toInt(),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(screenPadding()),
    ) {
        Text(
            text = stringResource(R.string.guide_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        DetailStrip(selected = selected)
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(CHANNEL_COLUMN_WIDTH))
            TimeRuler(
                slotTimesMs = viewModel.slotTimesMs,
                scrollState = timelineScroll,
            )
        }
        Spacer(Modifier.height(6.dp))

        when {
            channels.itemCount == 0 && channels.loadState.refresh is LoadState.Loading -> {
                GuideSkeleton()
            }
            channels.itemCount == 0 -> {
                EmptyState(message = stringResource(R.string.guide_empty))
            }
            else -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(
                        count = channels.itemCount,
                        key = channels.itemKey { it.id },
                    ) { index ->
                        channels[index]?.let { channel ->
                            GuideRow(
                                channel = channel,
                                laneFlow = remember(channel.id) { viewModel.lane(channel) },
                                scrollState = timelineScroll,
                                nowMs = nowMs,
                                onCellSelected = { viewModel.onProgrammeSelected(channel, it.programme) },
                                onPlay = { onPlayChannel(channel.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One line describing the focused/last-tapped programme: channel, time range,
 * title and description. Keeps a stable height so focus movement through the
 * grid never causes layout jumps.
 */
@Composable
private fun DetailStrip(selected: SelectedProgramme?) {
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Column(modifier = Modifier.fillMaxWidth().height(58.dp)) {
        if (selected == null) {
            Text(
                text = stringResource(R.string.guide_select_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val p = selected.programme
            Text(
                text = "${selected.channel.name} · " +
                    "${timeFormat.format(Date(p.startMs))} – ${timeFormat.format(Date(p.endMs))} · " +
                    p.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            p.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Slot labels sharing the lanes' scroll state so the ruler stays column-aligned.
@Composable
private fun TimeRuler(
    slotTimesMs: List<Long>,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Row(
        modifier = Modifier
            .height(RULER_HEIGHT)
            .horizontalScroll(scrollState),
    ) {
        for (slot in slotTimesMs) {
            Text(
                text = timeFormat.format(Date(slot)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.width(SLOT_WIDTH),
            )
        }
    }
}

// One channel lane: fixed channel cell plus the gap/cell timeline sharing the
// global horizontal scroll. Lane flows are collected per visible row only.
@Composable
private fun GuideRow(
    channel: LiveChannel,
    laneFlow: Flow<List<GuideLaneItem>>,
    scrollState: androidx.compose.foundation.ScrollState,
    nowMs: Long,
    onCellSelected: (GuideLaneItem.Cell) -> Unit,
    onPlay: () -> Unit,
) {
    val lane by laneFlow.collectAsStateWithLifecycle(emptyList())
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .padding(vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(CHANNEL_COLUMN_WIDTH),
        ) {
            Text(
                text = channel.num.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
            )
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            if (lane.isEmpty()) {
                // Keep empty lanes scroll-aligned with the ruler.
                Spacer(
                    Modifier.width((GuideTimeline.WINDOW_MINUTES * GuideTimeline.DP_PER_MINUTE).dp),
                )
            } else {
                for (item in lane) {
                    when (item) {
                        is GuideLaneItem.Gap -> Spacer(
                            Modifier.width((item.widthMinutes * GuideTimeline.DP_PER_MINUTE).dp),
                        )
                        is GuideLaneItem.Cell -> ProgrammeCell(
                            cell = item,
                            airing = GuideTimeline.isAiring(item.programme, nowMs),
                            onSelected = { onCellSelected(item) },
                            onPlay = onPlay,
                        )
                    }
                }
            }
        }
    }
}

// A programme cell: focus reports the selection (detail strip), OK/tap tunes
// the channel. Airing cells use the highlighted container so "now" reads at a
// glance without a separate time cursor.
@Composable
private fun ProgrammeCell(
    cell: GuideLaneItem.Cell,
    airing: Boolean,
    onSelected: () -> Unit,
    onPlay: () -> Unit,
) {
    NovaClickable(
        onClick = {
            onSelected()
            onPlay()
        },
        containerColor = if (airing) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        focusedScale = 1.0f,
        accessibilityLabel = cell.programme.title,
        modifier = Modifier
            .width((cell.widthMinutes * GuideTimeline.DP_PER_MINUTE).dp)
            .fillMaxHeight()
            .padding(horizontal = 1.dp)
            .clip(MaterialTheme.shapes.small)
            .onFocusChanged { if (it.isFocused) onSelected() },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (airing) {
                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    } else {
                        Modifier
                    },
                ),
        )
        Text(
            text = cell.programme.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 8.dp),
        )
    }
}

// Shimmer rows while the channel pager loads.
@Composable
private fun GuideSkeleton() {
    Column {
        repeat(8) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .padding(vertical = 2.dp),
            )
        }
    }
}
