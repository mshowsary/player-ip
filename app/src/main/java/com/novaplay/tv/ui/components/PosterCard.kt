package com.novaplay.tv.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage

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
    NovaClickable(
        onClick = onClick,
        onLongClick = onToggleBookmark,
        modifier = modifier.aspectRatio(2f / 3f),
        focusedScale = 1.07f,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 26.dp),
                )
            }
        }
        onToggleBookmark?.let { toggle ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) { detectTapGestures { toggle() } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (bookmarked) "Remove bookmark" else "Add bookmark",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
fun PosterGridSkeleton(columns: Int = 5) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
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
