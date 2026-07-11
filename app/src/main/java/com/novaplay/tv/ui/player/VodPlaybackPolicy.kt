package com.novaplay.tv.ui.player

/** Pure playback rules kept separate so resume and retry behaviour is testable. */
object VodResumePolicy {
    private const val MIN_SAVED_POSITION_MS = 30_000L
    private const val RESUME_REWIND_MS = 10_000L
    private const val COMPLETION_REMAINING_MS = 60_000L
    private const val COMPLETION_PERCENT = 0.95

    fun resumeStart(positionMs: Long, durationMs: Long): Long {
        if (positionMs < MIN_SAVED_POSITION_MS) return 0L
        if (isComplete(positionMs, durationMs)) return 0L
        return (positionMs - RESUME_REWIND_MS).coerceAtLeast(0L)
    }

    fun shouldPersist(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0L && positionMs >= MIN_SAVED_POSITION_MS

    fun normalizedSavedPosition(positionMs: Long, durationMs: Long): Long {
        if (durationMs <= 0L) return positionMs.coerceAtLeast(0L)
        return if (isComplete(positionMs, durationMs)) {
            durationMs
        } else {
            positionMs.coerceIn(0L, durationMs)
        }
    }

    fun isComplete(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        val boundedPosition = positionMs.coerceIn(0L, durationMs)
        val remaining = durationMs - boundedPosition
        return boundedPosition.toDouble() / durationMs.toDouble() >= COMPLETION_PERCENT ||
            remaining <= COMPLETION_REMAINING_MS
    }
}

object VodRecoveryPolicy {
    const val MAX_AUTOMATIC_RECOVERIES = 3
    const val BUFFERING_TIMEOUT_MS = 20_000L
    const val STABLE_PLAYBACK_RESET_MS = 15_000L

    private val DELAYS_MS = longArrayOf(1_000L, 2_500L, 5_000L)

    fun canRecover(completedAttempts: Int, recoverableFailure: Boolean): Boolean =
        recoverableFailure && completedAttempts < MAX_AUTOMATIC_RECOVERIES

    fun delayForAttempt(attemptNumber: Int): Long =
        DELAYS_MS[(attemptNumber - 1).coerceIn(0, DELAYS_MS.lastIndex)]

    fun messageForAttempt(attemptNumber: Int): String = when (attemptNumber) {
        1 -> "Reconnecting…"
        2 -> "Restoring playback…"
        else -> "One more recovery attempt…"
    }
}
