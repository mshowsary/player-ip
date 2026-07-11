package com.novaplay.tv.ui.player

sealed interface PlaybackRetryDecision {
    data class RetryCurrent(val delayMs: Long) : PlaybackRetryDecision
    data class TryNextSource(val delayMs: Long) : PlaybackRetryDecision
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

    fun retryDelayMs(failureNumber: Int): Long = when (failureNumber.coerceAtLeast(1)) {
        1 -> 800L
        2 -> 1_800L
        else -> 2_500L
    }
}
