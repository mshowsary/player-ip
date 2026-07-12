package com.novaplay.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.novaplay.tv.R
import com.novaplay.tv.ui.theme.isTvDevice

// 2:3 poster card with a bottom scrim for the title. Image decodes at cell
// size (Coil sizes from the layout), never full resolution. Bookmarking:
// tap the corner badge (touch) or long-press the card (TV remote).
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    bookmarked: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
) {
    val cardLabel = buildString {
        append(title)
        subtitle?.takeIf { it.isNotBlank() }?.let {
            append(", ")
            append(it)
        }
    }
    val bookmarkState = if (onToggleBookmark != null) {
        stringResource(if (bookmarked) R.string.state_bookmarked else R.string.state_not_bookmarked)
    } else {
        null
    }

    NovaClickable(
        onClick = onClick,
        onLongClick = onToggleBookmark,
        modifier = modifier.aspectRatio(2f / 3f),
        focusedScale = 1.07f,
        accessibilityLabel = cardLabel,
        accessibilityStateDescription = bookmarkState,
    ) {
        AsyncImage(
            model = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                    ),
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 26.dp),
                )
            }
        }
        onToggleBookmark?.let { toggle ->
            PosterBookmarkButton(
                bookmarked = bookmarked,
                onToggle = toggle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }
    }
}

// 48 dp corner badge that toggles the bookmark. Deliberately kept out of the
// TV focus chain (raw tap handler, no focusable) so D-pad traversal skips it;
// remotes long-press the card instead. Accessibility services still receive a
// named click action even in forced TV mode.
@Composable
private fun PosterBookmarkButton(
    bookmarked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = isTvDevice()
    val actionLabel = stringResource(
        if (bookmarked) R.string.action_remove_bookmark else R.string.action_add_bookmark,
    )
    val interactionModifier = if (isTv) {
        Modifier
            .pointerInput(Unit) { detectTapGestures { onToggle() } }
            .semantics {
                role = Role.Button
                contentDescription = actionLabel
                onClick(actionLabel) {
                    onToggle()
                    true
                }
            }
    } else {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = actionLabel,
            onClick = onToggle,
        )
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .then(interactionModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.64f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = if (bookmarked) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** One row of shimmering 2:3 poster placeholders, shown while a grid's first page loads. */
@Composable
fun PosterGridSkeleton(
    columns: Int = 5,
    spacingDp: Int = 14,
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacingDp.dp),
    ) {
        repeat(columns) {
            ShimmerBox(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(2f / 3f),
            )
        }
    }
}
