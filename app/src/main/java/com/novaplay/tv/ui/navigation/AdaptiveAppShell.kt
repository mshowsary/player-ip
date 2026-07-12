package com.novaplay.tv.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.R
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedFeature
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.theme.WindowWidthClass
import com.novaplay.tv.ui.theme.appLayoutInfo
import com.novaplay.tv.ui.theme.isTvDevice

/** A top-level shell entry; [managedFeature] documents the corresponding managed service. */
private data class ShellDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val managedFeature: ManagedFeature? = null,
)

private val topLevelDestinations = listOf(
    ShellDestination(Routes.HOME, R.string.nav_home, Icons.Default.Home),
    ShellDestination(Routes.LIVE, R.string.nav_live, Icons.Default.LiveTv, ManagedFeature.LIVE),
    ShellDestination(Routes.MOVIES, R.string.nav_movies, Icons.Default.Movie, ManagedFeature.MOVIES),
    ShellDestination(Routes.SERIES, R.string.nav_series, Icons.Default.VideoLibrary, ManagedFeature.SERIES),
    ShellDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings),
)

/** True when the route is a top-level shell destination — only those get nav chrome on touch. */
fun isTopLevelRoute(route: String?): Boolean = topLevelDestinations.any { it.route == route }

// Uses the pure policy so destination order and access filtering stay identical
// for bottom navigation, rails, tests and accessibility traversal.
private fun visibleDestinations(policy: ManagedAccessPolicy): List<ShellDestination> {
    val visibleRoutes = TopLevelNavigationPolicy.visibleRoutes(policy).toSet()
    return topLevelDestinations.filter { it.route in visibleRoutes }
}

/**
 * TV keeps the familiar full-screen, focus-first experience. Touch devices get
 * a bottom bar on compact windows and a persistent rail on tablets, foldables,
 * landscape phones, and resizable desktop windows.
 */
@Composable
fun AdaptiveAppShell(
    currentRoute: String?,
    policy: ManagedAccessPolicy,
    onNavigate: (String) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    if (isTvDevice() || !isTopLevelRoute(currentRoute)) {
        content(Modifier.fillMaxSize())
        return
    }

    val destinations = visibleDestinations(policy)
    val widthClass = appLayoutInfo().widthClass
    if (widthClass == WindowWidthClass.COMPACT) {
        Column(modifier = Modifier.fillMaxSize()) {
            content(Modifier.weight(1f).fillMaxWidth())
            TouchBottomBar(
                destinations = destinations,
                currentRoute = currentRoute,
                onNavigate = onNavigate,
            )
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            TouchNavigationRail(
                destinations = destinations,
                currentRoute = currentRoute,
                expanded = widthClass == WindowWidthClass.EXPANDED,
                onNavigate = onNavigate,
            )
            content(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** Compact-width bottom navigation bar: evenly weighted icon-over-label buttons. */
@Composable
private fun TouchBottomBar(
    destinations: List<ShellDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        destinations.forEach { destination ->
            ShellButton(
                destination = destination,
                selected = TopLevelNavigationPolicy.isSelected(currentRoute, destination.route),
                showLabel = true,
                compact = true,
                onClick = { onNavigate(destination.route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Side navigation rail for medium and expanded widths; expanded windows widen the rail
 * and show text labels next to the icons.
 */
@Composable
private fun TouchNavigationRail(
    destinations: List<ShellDestination>,
    currentRoute: String?,
    expanded: Boolean,
    onNavigate: (String) -> Unit,
) {
    Column(
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(if (expanded) 184.dp else 88.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .padding(horizontal = if (expanded) 12.dp else 8.dp, vertical = 18.dp),
    ) {
        destinations.forEachIndexed { index, destination ->
            ShellButton(
                destination = destination,
                selected = TopLevelNavigationPolicy.isSelected(currentRoute, destination.route),
                showLabel = expanded,
                compact = false,
                onClick = { onNavigate(destination.route) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (index != destinations.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * One shell destination button. Its parent supplies one combined accessibility
 * label and selected state, while the icon is decorative, avoiding duplicate speech.
 */
@Composable
private fun ShellButton(
    destination: ShellDestination,
    selected: Boolean,
    showLabel: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.labelRes)
    NovaClickable(
        onClick = onClick,
        modifier = modifier
            .height(if (compact) 58.dp else 56.dp)
            .semantics { this.selected = selected },
        containerColor = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        focusedScale = 1.03f,
        accessibilityLabel = label,
    ) {
        if (compact) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(25.dp),
                )
                if (showLabel) {
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
