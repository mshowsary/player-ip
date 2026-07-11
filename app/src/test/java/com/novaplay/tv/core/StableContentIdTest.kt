package com.novaplay.tv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableContentIdTest {

    @Test
    fun sameSourceAlwaysProducesSameId() {
        val first = StableContentId.forM3u(null, "https://example.com/live/42.ts")
        val second = StableContentId.forM3u(null, "https://example.com/live/42.ts")

        assertEquals(first, second)
        assertTrue(first > 0L)
    }

    @Test
    fun tvgIdKeepsIdentityWhenStreamUrlChanges() {
        val before = StableContentId.forM3u("channel.fr.1", "https://old.example/live.ts")
        val after = StableContentId.forM3u("CHANNEL.FR.1", "https://new.example/live.ts")

        assertEquals(before, after)
    }

    @Test
    fun urlIsFallbackIdentityWithoutTvgId() {
        val first = StableContentId.forM3u(null, "https://example.com/live/1.ts")
        val second = StableContentId.forM3u(null, "https://example.com/live/2.ts")

        assertNotEquals(first, second)
    }
}
