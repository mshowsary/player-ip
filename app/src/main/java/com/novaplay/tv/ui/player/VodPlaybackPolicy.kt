package com.novaplay.tv.ui.player

/** Pure playback rules kept separate so resume and retry behaviour is testable. */
object VodResumePolicy {
    private const val MIN_SAVED_POSITION_MS = 30_000L
    private const val RESUME_REWIND_MS = 10_000L
    private const val COMPLETION_REMAINING_MS = 60_000L
    private const val COMPLETION_PERCENT = 0.95

    /**
     * Position playback should start from when resuming: rewound 10 s for context,
     * or 0 when the saved position is trivial (< 30 s) or the title already counts
     * as watched — both cases mean a fresh start feels more natural than a resume.
     */
    fun resumeStart(positionMs: Long, durationMs: Long): Long {
        if (positionMs < MIN_SAVED_POSITION_MS) return 0L
        if (isComplete(positionMs, durationMs)) return 0L
        return (positionMs - RESUME_REWIND_MS).coerceAtLeast(0L)
    }

    /**
     * Progress is only worth saving once a known-duration title passes the 30 s
     * mark — earlier writes would just make everything briefly "in progress".
     */
    fun shouldPersist(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0L && positionMs >= MIN_SAVED_POSITION_MS

    /**
     * Clamps a position into [0, duration] and snaps nearly-finished titles to the
     * full duration so they are stored (and later rendered) as fully watched.
     */
    fun normalizedSavedPosition(positionMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return positionMs.coerceAtLeast(0L)
        return if (isComplete(positionMs, durationMs)) {
            durationMs
        } else {
            positionMs.coerceIn(0L, durationMs)
        }
    }

    /**
     * Watched enough to count as finished: ≥ 95 % seen or under a minute remaining
     * (credits). Unknown durations are never complete.
     */
    fun isComplete(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val boundedPosition = positionMs.coerceIn(0L, durationMs)
        val remaining = durationMs - boundedPosition
        return boundedPosition.toDouble() / durationMs.toDouble() >= COMPLETION_PERCENT ||
            remaining <= COMPLETION_REMAINING_MS
    }
}

/**
 * Bounded automatic recovery for VOD playback: recoverable failures get a few
 * increasingly spaced silent restarts before the user sees a terminal error.
 */
object VodRecoveryPolicy {
    const val MAX_AUTOMATIC_RECOVERIES = 3
    const val BUFFERING_TIMEOUT_MS = 20_000L
    const val STABLE_PLAYBACK_RESET_MS = 15_000L

    private val DELAYS_MS = longArrayOf(1_000L, 2_500L, 5_000L)

    /** Another silent restart is allowed only for recoverable failures still under the cap. */
    fun canRecover(completedAttempts: Int, recoverableFailure: Boolean): Boolean =
        recoverableFailure && completedAttempts < MAX_AUTOMATIC_RECOVERIES

    /** Backoff before the given 1-based attempt; out-of-range attempts clamp to the table edges. */
    fun delayForAttempt(attemptNumber: Int): Long =
        DELAYS_MS[(attemptNumber - 1).coerceIn(0, DELAYS_MS.lastIndex)]

    /** Status line shown under the spinner while the given 1-based attempt is in flight. */
    fun messageForAttempt(attemptNumber: Int): String = when (attemptNumber) {
        1 -> "Reconnecting…"
        2 -> "Restoring playback…"
        else -> "One more recovery attempt…"
    }
}
