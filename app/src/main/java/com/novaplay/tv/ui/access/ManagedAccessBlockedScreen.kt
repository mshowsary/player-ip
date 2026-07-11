package com.novaplay.tv.ui.access

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.data.repo.ManagedAccessPolicy
import com.novaplay.tv.data.repo.ManagedFeature
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding

/**
 * Full-screen notice shown when the managed policy blocks the device or the
 * requested feature (Live/Movies/Series). Shows the policy message and support
 * code when present; on TV, initial focus lands on the "Open playlists" button.
 */
@Composable
fun ManagedAccessBlockedScreen(
    feature: ManagedFeature,
    policy: ManagedAccessPolicy,
    onOpenPlaylists: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    val compact = isCompactWidth()
    val isTv = isTvDevice()

    LaunchedEffect(isTv) {
        if (isTv) runCatching { firstFocus.requestFocus() }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(screenPadding()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 620.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(78.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = blockedTitle(feature, policy),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = policy.message
                    ?: "This service is not currently included in the managed access assigned to this device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            policy.supportCode?.let { supportCode ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Support code: $supportCode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(26.dp))

            if (compact) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NovaButton(
                        text = "Open playlists",
                        onClick = onOpenPlaylists,
                        prominent = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(firstFocus),
                    )
                    NovaButton(
                        text = "View managed access",
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NovaButton(
                        text = "Open playlists",
                        onClick = onOpenPlaylists,
                        prominent = true,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    NovaButton(
                        text = "View managed access",
                        onClick = onOpenSettings,
                    )
                }
            }
        }
    }
}

// Device-wide blocks use the policy's own status label; otherwise name the
// specific feature that is unavailable.
private fun blockedTitle(feature: ManagedFeature, policy: ManagedAccessPolicy): String {
    if (policy.isBlocked) return policy.statusLabel()
    return when (feature) {
        ManagedFeature.LIVE -> "Live TV is unavailable"
        ManagedFeature.MOVIES -> "Movies are unavailable"
        ManagedFeature.SERIES -> "Series are unavailable"
    }
}
