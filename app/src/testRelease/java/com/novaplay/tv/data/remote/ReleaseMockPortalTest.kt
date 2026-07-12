package com.novaplay.tv.data.remote

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseMockPortalTest {

    @Test
    fun releaseContainsNoMockAssignmentsOrPolicy() {
        assertTrue(MockPortal.playlists.isEmpty())
        assertNull(MockPortal.policy)
    }
}
