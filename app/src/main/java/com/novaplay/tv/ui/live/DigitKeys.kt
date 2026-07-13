package com.novaplay.tv.ui.live

import androidx.compose.ui.input.key.Key

/**
 * Maps number-row and numpad keys to their digit for channel-number entry.
 * Shared by the Live browser and the live player so remotes behave the same
 * everywhere.
 */
internal fun Key.liveDigitOrNull(): Char? = when (this) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}
