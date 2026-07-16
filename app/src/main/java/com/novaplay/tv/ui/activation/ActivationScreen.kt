package com.novaplay.tv.ui.activation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.core.PortalEndpointPolicy
import com.novaplay.tv.ui.components.QrImage
import com.novaplay.tv.ui.playlists.PhoneEntryLaunch
import com.novaplay.tv.R
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.PulsingDot
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding
import kotlinx.coroutines.delay

/**
 * Device pairing screen: shows the one-time user code and portal address so
 * the viewer can approve this device from a phone or computer, plus an escape
 * hatch for personal playlists. Calls [onActivated] once the ViewModel reports
 * approval. On TV, focus snaps to the primary action button whenever the phase
 * moves past PREPARING.
 */
@Composable
fun ActivationScreen(
    onActivated: () -> Unit,
    onAddPersonalPlaylist: () -> Unit,
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val primaryActionFocus = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val isTv = isTvDevice()
    val compact = isCompactWidth()
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.activated.collect { onActivated() }
    }
    LaunchedEffect(isTv, state.phase) {
        if (isTv && state.phase != ActivationPhase.PREPARING) {
            runCatching { primaryActionFocus.requestFocus() }
        }
    }
    LaunchedEffect(copiedLabel) {
        if (copiedLabel != null) {
            delay(2_000)
            copiedLabel = null
        }
    }

    // Copies a value to the clipboard and flashes a "<label> copied" confirmation.
    fun copyValue(label: String, value: String) {
        if (value.isBlank()) return
        clipboard.setText(AnnotatedString(value))
        copiedLabel = label
    }

    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                    radius = 1_050f,
                ),
            )
            .padding(screenPadding()),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 48.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "SECURE DEVICE PAIRING",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Set up your player",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Got a provider? Give them the one-time code below — nothing to type here. Have your own playlist? Use the options further down.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 680.dp),
            )
            Spacer(Modifier.height(26.dp))

            if (compact) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                ) {
                    PairingPanel(
                        state = state,
                        copiedLabel = copiedLabel,
                        onCopyCode = { copyValue("Pairing code", state.userCode) },
                        onCopyPortal = { copyValue("Portal address", state.verificationUri) },
                        onPrimaryAction = viewModel::checkNow,
                        onRefreshCode = viewModel::refreshCode,
                        primaryActionFocus = primaryActionFocus,
                    )
                    StepsPanel()
                    // Managed-only brands hide the personal-source path entirely.
                    if (BuildConfig.ALLOW_PERSONAL_PLAYLISTS) {
                        PersonalPlaylistPanel(onAddPersonalPlaylist)
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 980.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(0.9f),
                    ) {
                        StepsPanel()
                        if (BuildConfig.ALLOW_PERSONAL_PLAYLISTS) {
                            PersonalPlaylistPanel(onAddPersonalPlaylist)
                        }
                    }
                    PairingPanel(
                        state = state,
                        copiedLabel = copiedLabel,
                        onCopyCode = { copyValue("Pairing code", state.userCode) },
                        onCopyPortal = { copyValue("Portal address", state.verificationUri) },
                        onPrimaryAction = viewModel::checkNow,
                        onRefreshCode = viewModel::refreshCode,
                        primaryActionFocus = primaryActionFocus,
                        modifier = Modifier.weight(1.1f),
                    )
                }
            }

            if (state.supportId.isNotBlank()) {
                Text(
                    text = "Support ID  ${state.supportId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }
    }
}

/**
 * Card with the pairing status, one-time code, portal address, and the
 * phase-appropriate actions (check now / new code). [primaryActionFocus] lets
 * the parent aim TV focus at the main button.
 */
@Composable
private fun PairingPanel(
    state: ActivationUiState,
    copiedLabel: String?,
    onCopyCode: () -> Unit,
    onCopyPortal: () -> Unit,
    onPrimaryAction: () -> Unit,
    onRefreshCode: () -> Unit,
    primaryActionFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .border(
                1.dp,
                MaterialTheme.colorScheme.border.copy(alpha = 0.65f),
                MaterialTheme.shapes.large,
            )
            .padding(20.dp),
    ) {
        Text(
            text = statusTitle(state),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = statusBody(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.userCode.isNotBlank()) {
            PairingCodeCard(
                code = state.userCode,
                onCopy = onCopyCode,
            )
            PortalAddressCard(
                address = state.verificationUri,
                onCopy = onCopyPortal,
            )
            if (state.verificationUri.isNotBlank()) {
                // One scan replaces reading the code out loud: the phone lands
                // on the portal with the code prefilled.
                val activateUrl = remember(state.verificationUri, state.userCode) {
                    if (state.verificationUri.contains('?')) {
                        state.verificationUri
                    } else {
                        state.verificationUri.trimEnd('/') + "?code=" + state.userCode
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    QrImage(content = activateUrl, size = 132.dp)
                    Text(
                        text = "Or scan with your phone",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            PreparingCodeCard()
        }

        PairingStatusRow(state)

        Text(
            text = copiedLabel?.let { "$it copied" } ?: "Select the code or portal address to copy it",
            style = MaterialTheme.typography.labelMedium,
            color = if (copiedLabel == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )

        state.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.phase == ActivationPhase.WAITING_FOR_APPROVAL ||
                    state.phase == ActivationPhase.WAITING_FOR_PLAYLIST
                ) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        MaterialTheme.shapes.small,
                    )
                    .padding(12.dp),
            )
        }

        when (state.phase) {
            ActivationPhase.PREPARING,
            ActivationPhase.APPROVED,
            -> Unit

            ActivationPhase.WAITING_FOR_APPROVAL -> {
                NovaButton(
                    text = if (state.checking) "Checking portal…" else "Check now",
                    onClick = onPrimaryAction,
                    prominent = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(primaryActionFocus),
                )
                NovaButton(
                    text = "Create a new code",
                    onClick = onRefreshCode,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ActivationPhase.WAITING_FOR_PLAYLIST -> {
                NovaButton(
                    text = if (state.checking) "Checking assignment…" else "Check for playlist",
                    onClick = onPrimaryAction,
                    prominent = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(primaryActionFocus),
                )
            }

            ActivationPhase.DENIED,
            ActivationPhase.EXPIRED,
            ActivationPhase.ERROR,
            -> {
                NovaButton(
                    text = "Create a new pairing code",
                    onClick = onRefreshCode,
                    prominent = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(primaryActionFocus),
                )
            }
        }
    }
}

/** Large monospace one-time code; selecting the card copies the code. */
@Composable
private fun PairingCodeCard(
    code: String,
    onCopy: () -> Unit,
) {
    NovaClickable(
        onClick = onCopy,
        focusedScale = 1.02f,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = "ONE-TIME CODE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Portal URL row with a copy icon; selecting it copies the address. */
@Composable
private fun PortalAddressCard(
    address: String,
    onCopy: () -> Unit,
) {
    NovaClickable(
        onClick = onCopy,
        focusedScale = 1.015f,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OPEN ON YOUR PHONE OR COMPUTER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = displayPortalAddress(address),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy portal address",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Pulsing placeholder shown while the pairing session is being created. */
@Composable
private fun PreparingCodeCard() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
    ) {
        PulsingDot(modifier = Modifier.size(12.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Creating a private pairing session…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One-line status indicator: pulsing dot while a phase is still in flight,
 * solid success/error dot once terminal, plus the expiry countdown while
 * waiting for approval.
 */
@Composable
private fun PairingStatusRow(state: ActivationUiState) {
    val active = state.phase == ActivationPhase.PREPARING ||
        state.phase == ActivationPhase.WAITING_FOR_APPROVAL ||
        state.phase == ActivationPhase.WAITING_FOR_PLAYLIST

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (active) {
            PulsingDot(modifier = Modifier.size(9.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (state.phase == ActivationPhase.APPROVED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    ),
            )
        }
        Text(
            text = statusLine(state),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (state.phase == ActivationPhase.WAITING_FOR_APPROVAL && state.secondsRemaining > 0L) {
            Text(
                text = formatCountdown(state.secondsRemaining),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Static three-step "How to connect" instructions panel. */
@Composable
private fun StepsPanel() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .border(
                1.dp,
                MaterialTheme.colorScheme.border.copy(alpha = 0.55f),
                MaterialTheme.shapes.large,
            )
            .padding(20.dp),
    ) {
        Text(text = "How to connect", style = MaterialTheme.typography.titleLarge)
        InstructionRow(
            number = "1",
            title = "Open the portal",
            body = "Use the address displayed beside the code on your phone or computer.",
        )
        InstructionRow(
            number = "2",
            title = "Enter the one-time code",
            body = "Sign in to your provider account, choose Add device, then enter the code.",
        )
        InstructionRow(
            number = "3",
            title = "Approve this device",
            body = "NovaPlay detects approval automatically and securely downloads assigned playlists.",
        )
    }
}

/** Single numbered instruction: circled step number beside a title and body. */
@Composable
private fun InstructionRow(
    number: String,
    title: String,
    body: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(3.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Escape hatch for users with their own Xtream credentials or M3U playlist,
 * bypassing the provider portal entirely.
 */
@Composable
private fun PersonalPlaylistPanel(onAddPersonalPlaylist: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                MaterialTheme.shapes.medium,
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.border.copy(alpha = 0.45f),
                MaterialTheme.shapes.medium,
            )
            .padding(16.dp),
    ) {
        Text(text = "Have your own playlist?", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "No provider needed. Type it on your phone (easiest) or enter it here with the remote.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val phoneEntryReachable = PortalEndpointPolicy
            .assess(BuildConfig.PORTAL_BASE_URL, BuildConfig.DEBUG)
            .let { it.configured && it.transportAllowed } && !BuildConfig.MOCK_ACTIVATION
        if (phoneEntryReachable) {
            NovaButton(
                text = "Add from your phone",
                onClick = {
                    // Jumps straight to the phone-entry code — no menu digging.
                    PhoneEntryLaunch.requested = true
                    onAddPersonalPlaylist()
                },
                prominent = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        NovaButton(
            text = "Enter it on this device",
            onClick = onAddPersonalPlaylist,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Headline of the pairing panel, per phase.
private fun statusTitle(state: ActivationUiState): String = when (state.phase) {
    ActivationPhase.PREPARING -> "Preparing your code"
    ActivationPhase.WAITING_FOR_APPROVAL -> "Pairing code ready"
    ActivationPhase.WAITING_FOR_PLAYLIST -> "Device connected"
    ActivationPhase.APPROVED -> "Connection complete"
    ActivationPhase.DENIED -> "Request declined"
    ActivationPhase.EXPIRED -> "Code expired"
    ActivationPhase.ERROR -> "Pairing unavailable"
}

// Longer explanation shown under the headline, per phase.
private fun statusBody(state: ActivationUiState): String = when (state.phase) {
    ActivationPhase.PREPARING -> "NovaPlay is creating a short-lived private session with the portal."
    ActivationPhase.WAITING_FOR_APPROVAL ->
        "Enter this code on the provider portal. It can only be used for this pairing session."
    ActivationPhase.WAITING_FOR_PLAYLIST ->
        "Approval succeeded. NovaPlay is waiting for your provider to assign at least one playlist."
    ActivationPhase.APPROVED -> "The device is connected and your assigned content is being synchronized."
    ActivationPhase.DENIED -> "The portal declined this connection request."
    ActivationPhase.EXPIRED -> "For your security, pairing codes stop working after a short time."
    ActivationPhase.ERROR -> "A secure pairing session could not be created right now."
}

// Short status text next to the indicator dot in PairingStatusRow.
private fun statusLine(state: ActivationUiState): String = when (state.phase) {
    ActivationPhase.PREPARING -> "Creating secure session"
    ActivationPhase.WAITING_FOR_APPROVAL -> if (state.checking) "Checking for approval" else "Waiting for approval"
    ActivationPhase.WAITING_FOR_PLAYLIST -> if (state.checking) "Checking playlist assignment" else "Waiting for playlist assignment"
    ActivationPhase.APPROVED -> "Approved"
    ActivationPhase.DENIED -> "Declined"
    ActivationPhase.EXPIRED -> "Expired"
    ActivationPhase.ERROR -> "Not connected"
}

/** Formats the remaining seconds as m:ss, clamping negative values to 0:00. */
internal fun formatCountdown(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val minutes = safe / 60L
    val seconds = safe % 60L
    return "%d:%02d".format(minutes, seconds)
}

/** Strips the scheme and trailing slash so the on-screen address stays short. */
internal fun displayPortalAddress(address: String): String = address
    .removePrefix("https://")
    .removePrefix("http://")
    .trimEnd('/')
    .ifBlank { "Portal address unavailable" }
