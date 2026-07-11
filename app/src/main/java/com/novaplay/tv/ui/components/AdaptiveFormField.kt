package com.novaplay.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * One form field shared by touch devices and TV remotes. It provides a visible
 * focus ring, a 52 dp minimum target, inline validation, sensible IME actions,
 * and an optional focusable password reveal control.
 */
@Composable
fun AdaptiveFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = label,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false,
    error: String? = null,
    supportingText: String? = null,
    focusRequester: FocusRequester? = null,
    onImeAction: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(12.dp)
    val borderColor = when {
        error != null -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.border.copy(alpha = 0.7f)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    if (onImeAction != null) onImeAction() else focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                },
                onDone = {
                    if (onImeAction != null) onImeAction() else focusManager.clearFocus()
                },
                onGo = {
                    if (onImeAction != null) onImeAction() else focusManager.clearFocus()
                },
            ),
            visualTransformation = if (password && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 52.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape)
                        .border(BorderStroke(if (focused || error != null) 2.dp else 1.dp, borderColor), shape)
                        .padding(start = 14.dp, end = if (password) 6.dp else 14.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        innerTextField()
                    }
                    if (password) {
                        NovaClickable(
                            onClick = { passwordVisible = !passwordVisible },
                            shape = RoundedCornerShape(9.dp),
                            focusedScale = 1.02f,
                            restingBorder = false,
                            modifier = Modifier.sizeIn(minWidth = 58.dp, minHeight = 42.dp),
                        ) {
                            Text(
                                text = if (passwordVisible) "Hide" else "Show",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused },
        )

        val helper = error ?: supportingText
        if (!helper.isNullOrBlank()) {
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
