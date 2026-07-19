package com.novaplay.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.R
import com.novaplay.tv.core.DataSize
import com.novaplay.tv.data.repo.SyncStatus

/**
 * Modal progress for a user-initiated playlist update: current step, a
 * determinate bar with byte counts when the provider sent a size (shimmer
 * otherwise), and a Close action. Closing only hides the modal — the sync
 * keeps running and the Home footer keeps showing the live step.
 */
@Composable
fun SyncProgressDialog(
    playlistName: String?,
    status: SyncStatus.Syncing,
    onClose: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    NovaDialog(
        title = stringResource(R.string.sync_modal_title),
        onDismiss = onClose,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            playlistName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingDot(Modifier.size(8.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = status.step,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val progress = status.progress
            val percent = progress?.percent
            ProgressTrack(percent = percent, modifier = Modifier.fillMaxWidth())

            if (progress != null) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (progress.totalBytes > 0) {
                            stringResource(
                                R.string.sync_modal_progress_of,
                                DataSize.format(progress.bytesRead),
                                DataSize.format(progress.totalBytes),
                            )
                        } else {
                            stringResource(R.string.sync_modal_downloaded, DataSize.format(progress.bytesRead))
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    percent?.let {
                        Text(
                            text = "$it%",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.sync_modal_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            NovaButton(
                text = stringResource(R.string.sync_modal_close),
                onClick = onClose,
                prominent = true,
                modifier = Modifier
                    .align(Alignment.End)
                    .focusRequester(closeFocus),
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }
}

// Determinate accent fill when the percent is known; shimmer while the size is
// still unknown (or the current step is local install work with no byte count).
@Composable
private fun ProgressTrack(percent: Int?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(3.dp)
    if (percent == null) {
        ShimmerBox(modifier = modifier.height(6.dp), shape = shape)
        return
    }
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(percent / 100f)
                .background(MaterialTheme.colorScheme.primary, shape),
        )
    }
}
