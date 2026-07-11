package com.novaplay.tv.ui.player

/** Outcome of a live playback failure: retry the same URL, fail over, or give up. */
sealed interface PlaybackRetryDecision {
    /** Retry the current source after waiting [delayMs]. */
    data class RetryCurrent(val delayMs: Long) : PlaybackRetryDecision
    /** Fail over to the next source URL after waiting [delayMs]. */
    data class TryNextSource(val delayMs: Long) : PlaybackRetryDecision
    /** No sources left to try; the player should surface a terminal error. */
    data object Exhausted : PlaybackRetryDecision
}

/**
 * Small deterministic policy shared by the live player and its tests.
 *
 * Each source receives two short reconnect attempts before the player moves to
 * the alternate HLS/MPEG-TS URL. Backoff is deliberately bounded because a TV
 * user should never stare at a spinner for an unbounded amount of time.
 */
object PlaybackRetryPolicy {
    const val RETRIES_PER_SOURCE = 2

    /**
     * Decides the next step after a failure: retry the same source while it has
     * budget left, then fail over, then give up. Out-of-range indices are treated
     * as exhausted rather than thrown so a racing callback can never crash.
     */
    fun afterFailure(
        sourceIndex: Int,
        failuresOnCurrentSource: Int,
        sourceCount: Int,
    ): PlaybackRetryDecision {
        if (sourceCount <= 0 || sourceIndex !in 0 until sourceCount) {
            return PlaybackRetryDecision.Exhausted
        }

        if (failuresOnCurrentSource <= RETRIES_PER_SOURCE) {
            return PlaybackRetryDecision.RetryCurrent(
                delayMs = retryDelayMs(failuresOnCurrentSource),
            )
        }

        return if (sourceIndex + 1 < sourceCount) {
            PlaybackRetryDecision.TryNextSource(delayMs = 350L)
        } else {
            PlaybackRetryDecision.Exhausted
        }
    }

    /** Backoff before retrying the same source; grows per failure and caps at 2.5 s. */
    fun retryDelayMs(failureNumber: Int): Long = when (failureNumber.coerceAtLeast(1)) {
        1 -> 800L
        2 -> 1_800L
        else -> 2_500L
    }
}
