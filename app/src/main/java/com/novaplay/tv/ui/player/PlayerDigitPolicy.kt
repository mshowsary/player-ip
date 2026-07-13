package com.novaplay.tv.ui.player

/** What a committed remote-digit buffer means inside the live player. */
sealed interface PlayerDigitAction {
    /** Jump to the channel with (or after) this number. */
    data class JumpToNumber(val num: Int) : PlayerDigitAction

    /** Return to the previously watched channel — the classic lone-zero recall. */
    data object Recall : PlayerDigitAction

    /** Nothing usable was typed. */
    data object None : PlayerDigitAction
}

/**
 * Interprets the digit buffer when the entry timer commits. A lone "0" is the
 * traditional last-channel recall; anything else must parse to a positive
 * channel number or be ignored (never a crash, never channel zero).
 */
object PlayerDigitPolicy {
    const val MAX_DIGITS = 4
    const val COMMIT_DELAY_MS = 1_400L

    fun interpret(buffer: String): PlayerDigitAction {
        if (buffer == "0") return PlayerDigitAction.Recall
        val num = buffer.toIntOrNull() ?: return PlayerDigitAction.None
        return if (num > 0) PlayerDigitAction.JumpToNumber(num) else PlayerDigitAction.None
    }
}
