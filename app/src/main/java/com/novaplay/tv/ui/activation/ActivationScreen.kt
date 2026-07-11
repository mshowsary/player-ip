package com.novaplay.tv.ui.activation

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import com.novaplay.tv.R
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.components.NovaClickable
import com.novaplay.tv.ui.components.PulsingDot
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding
import kotlinx.coroutines.delay

@Composable
fun ActivationScreen(
    onActivated: () -> Unit,
    onAddPersonalPlaylist: () -> Unit,
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val checkNowFocus = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val isTv = isTvDevice()
    val compact = isCompactWidth()
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.activated.collect { onActivated() }
    }
    LaunchedEffect(Unit) { if (isTv) checkNowFocus.requestFocus() }
    LaunchedEffect(copiedLabel) {
        if (copiedLabel != null) {
            delay(2_000)
            copiedLabel = null
        }
    }

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
                    colors = listOf(accent.copy(alpha = 0.08f), Color.Transparent),
                    radius = 900f,
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
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "CONNECT THIS DEVICE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Use managed activation from a provider, or add your own Xtream or M3U playlist.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 620.dp),
            )
            Spacer(Modifier.height(24.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (compact) 520.dp else 680.dp),
            ) {
                DeviceCodeCard(
                    label = "MAC address",
                    value = state.mac.ifBlank { "··:··:··:··:··:··" },
                    large = !compact,
                    onCopy = { copyValue("MAC address", state.mac) },
                )
                DeviceCodeCard(
                    label = "Device key",
                    value = state.deviceKey.toCharArray().joinToString(" "),
                    large = false,
                    accentValue = true,
                    onCopy = { copyValue("Device key", state.deviceKey) },
                )
            }

            Text(
                text = when (copiedLabel) {
                    null -> "Tap a device code to copy it"
                    else -> "$copiedLabel copied"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (copiedLabel == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    accent
                },
                modifier = Modifier.padding(top = 10.dp),
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Managed activation: visit ${portalDisplayUrl()} and enter the MAC address and device key.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 580.dp),
            )
            Spacer(Modifier.height(22.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PulsingDot(modifier = Modifier.size(10.dp))
                Text(
                    text = if (state.checking) "Checking…" else "Waiting for managed activation…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(22.dp))

            if (compact) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 440.dp),
                ) {
                    NovaButton(
                        text = "Check managed activation",
                        onClick = viewModel::checkNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(checkNowFocus),
                    )
                    NovaButton(
                        text = "Add my own playlist",
                        onClick = onAddPersonalPlaylist,
                        prominent = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NovaButton(
                        text = "Check managed activation",
                        onClick = viewModel::checkNow,
                        modifier = Modifier.focusRequester(checkNowFocus),
                    )
                    NovaButton(
                        text = "Add my own playlist",
                        onClick = onAddPersonalPlaylist,
                        prominent = true,
                    )
                }
            }

            state.error?.let { error ->
                Spacer(Modifier.height(20.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
        }
    }
}

@Composable
private fun DeviceCodeCard(
    label: String,
    value: String,
    large: Boolean,
    accentValue: Boolean = false,
    onCopy: () -> Unit,
) {
    NovaClickable(
        onClick = onCopy,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (large) 108.dp else 88.dp),
        focusedScale = 1.02f,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = value,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (large) 32.sp else 23.sp,
                    letterSpacing = 1.sp,
                    color = if (accentValue) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun portalDisplayUrl(): String =
    BuildConfig.PORTAL_BASE_URL
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
