package com.novaplay.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.novaplay.tv.core.ParentalPinPolicy
import com.novaplay.tv.data.repo.PinAttempt
import kotlinx.coroutines.launch

/** Parental-control snapshot a category surface renders from. */
data class ParentalUiState(
    val pinConfigured: Boolean = false,
    val sessionUnlocked: Boolean = false,
    val lockedIds: Set<Long> = emptySet(),
)

// What the pending PIN dialog is for: entering a locked category, changing
// locks behind the PIN, or creating the PIN on first lock.
internal enum class GateMode { UNLOCK_TO_OPEN, UNLOCK_TO_TOGGLE, CREATE_TO_TOGGLE }

internal data class GateRequest(
    val mode: GateMode,
    val categoryId: Long?,
    val proceed: (() -> Unit)? = null,
)

/**
 * Screen-level controller for parental category gating. [open] runs an action
 * immediately or after a successful PIN entry; [toggleLock] locks/unlocks a
 * category, creating the PIN in place the very first time. Render
 * [ParentalGateDialogs] once on the same screen to show the pending dialog.
 */
@Stable
class ParentalGateState internal constructor(
    private val state: State<ParentalUiState>,
    internal val onUnlock: suspend (String) -> PinAttempt,
    internal val onSetPin: suspend (String) -> Boolean,
    internal val onToggleLock: (Long) -> Unit,
) {
    internal var request by mutableStateOf<GateRequest?>(null)

    /** Runs [proceed] now, or after the PIN unlocks the session when [categoryId] is locked. */
    fun open(categoryId: Long?, proceed: () -> Unit) {
        val s = state.value
        val gated = categoryId != null && categoryId in s.lockedIds &&
            !s.sessionUnlocked && s.pinConfigured
        if (gated) {
            request = GateRequest(GateMode.UNLOCK_TO_OPEN, categoryId, proceed)
        } else {
            proceed()
        }
    }

    /** Locks or unlocks [categoryId], asking to create or enter the PIN when needed. */
    fun toggleLock(categoryId: Long) {
        val s = state.value
        when {
            !s.pinConfigured -> request = GateRequest(GateMode.CREATE_TO_TOGGLE, categoryId)
            !s.sessionUnlocked -> request = GateRequest(GateMode.UNLOCK_TO_TOGGLE, categoryId)
            else -> onToggleLock(categoryId)
        }
    }

    internal fun dismiss() {
        request = null
    }
}

/** Remembers a [ParentalGateState] bound to the screen's ViewModel callbacks. */
@Composable
fun rememberParentalGate(
    state: ParentalUiState,
    onUnlock: suspend (String) -> PinAttempt,
    onSetPin: suspend (String) -> Boolean,
    onToggleLock: (Long) -> Unit,
): ParentalGateState {
    val latestState = rememberUpdatedState(state)
    val latestUnlock = rememberUpdatedState(onUnlock)
    val latestSetPin = rememberUpdatedState(onSetPin)
    val latestToggle = rememberUpdatedState(onToggleLock)
    return remember {
        ParentalGateState(
            state = latestState,
            onUnlock = { latestUnlock.value(it) },
            onSetPin = { latestSetPin.value(it) },
            onToggleLock = { latestToggle.value(it) },
        )
    }
}

/** Renders the PIN dialog for the gate's pending request, if any. */
@Composable
fun ParentalGateDialogs(gate: ParentalGateState) {
    val request = gate.request ?: return
    var error by remember(request) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val creating = request.mode == GateMode.CREATE_TO_TOGGLE
    PinDialog(
        title = if (creating) "Set parental PIN" else "Parental PIN",
        subtitle = when (request.mode) {
            GateMode.CREATE_TO_TOGGLE ->
                "Choose a 4-digit PIN. Locked categories and everything in them stay " +
                    "hidden until it is entered."
            GateMode.UNLOCK_TO_TOGGLE -> "Enter your PIN to change category locks."
            GateMode.UNLOCK_TO_OPEN -> "This category is locked. Enter your PIN to open it."
        },
        confirmLabel = if (creating) "Save PIN" else "Unlock",
        maskInput = !creating,
        error = error,
        onSubmit = { pin ->
            scope.launch {
                val result = if (creating) {
                    if (gate.onSetPin(pin)) PinAttempt.Ok else PinAttempt.Wrong
                } else {
                    gate.onUnlock(pin)
                }
                when (result) {
                    PinAttempt.Ok -> {
                        when (request.mode) {
                            GateMode.UNLOCK_TO_OPEN -> request.proceed?.invoke()
                            else -> request.categoryId?.let(gate.onToggleLock)
                        }
                        gate.dismiss()
                    }
                    PinAttempt.Wrong -> error = "Wrong PIN — try again"
                    is PinAttempt.Locked ->
                        error = "Too many attempts — try again in ${result.waitSeconds} s"
                }
            }
        },
        onDismiss = gate::dismiss,
    )
}

/**
 * Four-digit PIN entry dialog for remote, keyboard and touch. Non-digits are
 * dropped, input caps at four characters, and a wrong attempt clears the
 * field. Creation flows show the digits; unlock flows mask them.
 */
@Composable
fun PinDialog(
    title: String,
    subtitle: String?,
    confirmLabel: String,
    maskInput: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    LaunchedEffect(error) {
        if (error != null) pin = ""
    }

    NovaDialog(title = title, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = pin,
                onValueChange = { raw ->
                    pin = raw.filter { it in '0'..'9' }.take(ParentalPinPolicy.PIN_LENGTH)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (pin.length == ParentalPinPolicy.PIN_LENGTH) onSubmit(pin) },
                ),
                visualTransformation = if (maskInput) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    letterSpacing = 8.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(vertical = 14.dp)
                    .focusRequester(focusRequester),
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NovaButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                NovaButton(
                    text = confirmLabel,
                    onClick = { if (pin.length == ParentalPinPolicy.PIN_LENGTH) onSubmit(pin) },
                    modifier = Modifier.weight(1f),
                    prominent = true,
                )
            }
        }
    }
}
