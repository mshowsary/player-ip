package com.novaplay.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRetryPolicyTest {

    @Test
    fun retriesCurrentSourceTwiceBeforeUsingFallback() {
        val first = PlaybackRetryPolicy.afterFailure(0, 1, 2)
        val second = PlaybackRetryPolicy.afterFailure(0, 2, 2)
        val fallback = PlaybackRetryPolicy.afterFailure(0, 3, 2)

        assertTrue(first is PlaybackRetryDecision.RetryCurrent)
        assertTrue(second is PlaybackRetryDecision.RetryCurrent)
        assertTrue(fallback is PlaybackRetryDecision.TryNextSource)
    }

    @Test
    fun exhaustsAfterFallbackSourceAlsoFails() {
        val result = PlaybackRetryPolicy.afterFailure(1, 3, 2)
        assertTrue(result is PlaybackRetryDecision.Exhausted)
    }

    @Test
    fun backoffIsShortAndBounded() {
        assertEquals(800L, PlaybackRetryPolicy.retryDelayMs(1))
        assertEquals(1_800L, PlaybackRetryPolicy.retryDelayMs(2))
        assertEquals(2_500L, PlaybackRetryPolicy.retryDelayMs(20))
    }

    @Test
    fun invalidSourceStateFailsClosed() {
        assertTrue(
            PlaybackRetryPolicy.afterFailure(0, 1, 0) is PlaybackRetryDecision.Exhausted,
        )
        assertTrue(
            PlaybackRetryPolicy.afterFailure(4, 1, 2) is PlaybackRetryDecision.Exhausted,
        )
    }
}
