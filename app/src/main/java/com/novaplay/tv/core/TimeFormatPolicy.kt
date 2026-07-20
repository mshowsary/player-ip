package com.novaplay.tv.core

import com.novaplay.tv.data.prefs.TimeFormatPreference

/**
 * Resolves how clocks and programme times are written. Pure so the
 * AUTO-follows-the-device rule and the patterns themselves are testable.
 */
object TimeFormatPolicy {
    /** Whether to render 24-hour time given the user's choice and the device setting. */
    fun use24Hour(preference: TimeFormatPreference, deviceUses24: Boolean): Boolean =
        when (preference) {
            TimeFormatPreference.AUTO -> deviceUses24
            TimeFormatPreference.H12 -> false
            TimeFormatPreference.H24 -> true
        }

    /** Time-only pattern, e.g. "18:30" or "6:30 PM". */
    fun timePattern(use24: Boolean): String = if (use24) "HH:mm" else "h:mm a"

    /** Date + time pattern, e.g. "Mar 4, 18:32" or "Mar 4, 6:32 PM". */
    fun dateTimePattern(use24: Boolean): String =
        if (use24) "MMM d, HH:mm" else "MMM d, h:mm a"
}
