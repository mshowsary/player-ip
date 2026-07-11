package com.novaplay.tv.ui.activation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.BuildConfig
import com.novaplay.tv.R
import com.novaplay.tv.ui.components.NovaButton
import com.novaplay.tv.ui.components.PulsingDot
import com.novaplay.tv.ui.theme.isCompactWidth
import com.novaplay.tv.ui.theme.isTvDevice
import com.novaplay.tv.ui.theme.screenPadding
import androidx.compose.ui.res.stringResource

@Composable
fun ActivationScreen(
    onActivated: () -> Unit,
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val checkNowFocus = remember { FocusRequester() }
    val isTv = isTvDevice()
    val compact = isCompactWidth()

    LaunchedEffect(Unit) {
        viewModel.activated.collect { onActivated() }
    }
    LaunchedEffect(Unit) { if (isTv) checkNowFocus.requestFocus() }

    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(
                // A faint accent bloom behind the centerpiece — premium, not debug.
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "THIS DEVICE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(16.dp))
            // The visual centerpiece: the address users copy to the portal.
            // Sized down on phones so all six octets stay on one line.
            Text(
                text = state.mac.ifBlank { "··:··:··:··:··:··" },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 28.sp else 58.sp,
                letterSpacing = if (compact) 1.sp else 3.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Device key",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = state.deviceKey.toCharArray().joinToString(" "),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 22.sp else 30.sp,
                    color = accent,
                )
            }
            Spacer(Modifier.height(36.dp))
            Text(
                text = "Visit ${portalDisplayUrl()} and enter your MAC address and device key to attach your playlist",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )
            Spacer(Modifier.height(40.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PulsingDot(modifier = Modifier.size(10.dp))
                Text(
                    text = if (state.checking) "Checking…" else "Waiting for activation…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            NovaButton(
                text = "Check now",
                onClick = viewModel::checkNow,
                modifier = Modifier.focusRequester(checkNowFocus),
            )
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

private fun portalDisplayUrl(): String =
    BuildConfig.PORTAL_BASE_URL
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
