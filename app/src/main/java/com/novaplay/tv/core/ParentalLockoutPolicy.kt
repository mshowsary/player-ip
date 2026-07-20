package com.novaplay.tv.core

/**
 * Throttle for parental-PIN guessing. A four-digit PIN has only 10,000
 * combinations, so after a handful of free attempts every further failure
 * starts a lockout that doubles up to a cap — enough to turn a patient
 * button-masher's evening into weeks, without ever punishing a parent who
 * fat-fingers the code once or twice.
 */
object ParentalLockoutPolicy {
    /** Failures allowed before lockouts start. */
    const val FREE_ATTEMPTS = 5

    private const val BASE_LOCK_MS = 30_000L
    private const val MAX_DOUBLINGS = 4 // 30 s, 1 m, 2 m, 4 m, then 8 m flat.

    /**
     * Milliseconds the caller must still wait before the next attempt is
     * allowed; 0 means an attempt may proceed now.
     */
    fun remainingLockMs(failedAttempts: Int, lastFailAtMs: Long, nowMs: Long): Long {
        if (failedAttempts < FREE_ATTEMPTS) return 0L
        val doublings = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS)
        val lockMs = BASE_LOCK_MS shl doublings
        return (lastFailAtMs + lockMs - nowMs).coerceAtLeast(0L)
    }
}
