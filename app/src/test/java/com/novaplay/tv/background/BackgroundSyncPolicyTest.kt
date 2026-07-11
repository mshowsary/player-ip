package com.novaplay.tv.background

import com.novaplay.tv.data.prefs.BackgroundSyncMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundSyncPolicyTest {

    @Test
    fun offDoesNotCreatePeriodicWork() {
        assertNull(BackgroundSyncPolicy.plan(BackgroundSyncMode.OFF))
    }

    @Test
    fun dailyUsesAProtectedFlexWindow() {
        val plan = requireNotNull(BackgroundSyncPolicy.plan(BackgroundSyncMode.DAILY))

        assertEquals(24L, plan.intervalHours)
        assertEquals(4L, plan.flexHours)
        assertEquals(45L, plan.initialDelayMinutes)
    }

    @Test
    fun twiceDailyUsesTwelveHourInterval() {
        val plan = requireNotNull(BackgroundSyncPolicy.plan(BackgroundSyncMode.TWICE_DAILY))

        assertEquals(12L, plan.intervalHours)
        assertEquals(2L, plan.flexHours)
    }
}
