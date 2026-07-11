package com.novaplay.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VodPlaybackPolicyTest {

    @Test
    fun resumeRewindsTenSecondsForContext() {
        assertEquals(80_000L, VodResumePolicy.resumeStart(90_000L, 600_000L))
    }

    @Test
    fun tinyProgressStartsFromBeginning() {
        assertEquals(0L, VodResumePolicy.resumeStart(20_000L, 600_000L))
        assertFalse(VodResumePolicy.shouldPersist(20_000L, 600_000L))
    }

    @Test
    fun almostFinishedContentStartsFromBeginningAndSavesAsComplete() {
        assertEquals(0L, VodResumePolicy.resumeStart(570_000L, 600_000L))
        assertTrue(VodResumePolicy.isComplete(570_000L, 600_000L))
        assertEquals(600_000L, VodResumePolicy.normalizedSavedPosition(570_000L, 600_000L))
    }

    @Test
    fun automaticRecoveryIsBounded() {
        assertTrue(VodRecoveryPolicy.canRecover(0, recoverableFailure = true))
        assertTrue(VodRecoveryPolicy.canRecover(2, recoverableFailure = true))
        assertFalse(VodRecoveryPolicy.canRecover(3, recoverableFailure = true))
        assertFalse(VodRecoveryPolicy.canRecover(0, recoverableFailure = false))
    }

    @Test
    fun retryDelaysIncreaseAndClamp() {
        assertEquals(1_000L, VodRecoveryPolicy.delayForAttempt(1))
        assertEquals(2_500L, VodRecoveryPolicy.delayForAttempt(2))
        assertEquals(5_000L, VodRecoveryPolicy.delayForAttempt(3))
        assertEquals(5_000L, VodRecoveryPolicy.delayForAttempt(8))
    }
}
