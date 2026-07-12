package com.novaplay.tv.data.epg

import com.novaplay.tv.data.db.EpgProgramme
import java.util.Locale

/**
 * Normalizes provider guide identifiers so the three sources of the same key —
 * Xtream `epg_channel_id`, M3U `tvg-id` and the XMLTV `channel` attribute —
 * match each other despite the casing and whitespace panels mix freely.
 */
object EpgChannelKey {
    /** Trimmed, lowercased key, or null when there is nothing usable to match on. */
    fun normalize(raw: String?): String? = raw
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }
}

/**
 * Guide retention window around the refresh time. The past tail keeps the
 * currently airing programme visible even when it started hours ago; the
 * forward window bounds the table for the coming grid UI so a provider's
 * multi-week XMLTV dump cannot bloat storage on low-end devices.
 */
object EpgRetentionPolicy {
    const val PAST_RETENTION_MS: Long = 12L * 60 * 60 * 1000
    const val FUTURE_RETENTION_MS: Long = 48L * 60 * 60 * 1000

    /** Whether a programme belongs in local storage: well-formed and inside the window. */
    fun shouldStore(startMs: Long, endMs: Long, nowMs: Long): Boolean =
        endMs > startMs &&
            endMs > nowMs - PAST_RETENTION_MS &&
            startMs < nowMs + FUTURE_RETENTION_MS
}

/** Current and upcoming programme of one channel; either side may be missing. */
data class EpgNowNext(
    val now: EpgProgramme? = null,
    val next: EpgProgramme? = null,
) {
    companion object {
        val EMPTY = EpgNowNext()
    }
}

/**
 * Interprets EpgDao.observeUpcoming output — the two earliest programmes with
 * endMs > now in start order. The first entry counts as "now" only when it has
 * actually started, so a guide gap never presents a future programme as airing.
 */
object EpgNowNextPolicy {
    fun fromUpcoming(upcoming: List<EpgProgramme>, nowMs: Long): EpgNowNext {
        val first = upcoming.firstOrNull() ?: return EpgNowNext.EMPTY
        return if (first.startMs <= nowMs) {
            EpgNowNext(now = first, next = upcoming.getOrNull(1))
        } else {
            EpgNowNext(now = null, next = first)
        }
    }
}
