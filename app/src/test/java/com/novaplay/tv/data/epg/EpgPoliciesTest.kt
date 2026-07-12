package com.novaplay.tv.data.epg

import com.novaplay.tv.data.db.EpgProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgPoliciesTest {

    // ---- EpgChannelKey ----

    @Test
    fun normalizeLowercasesAndTrims() {
        assertEquals("france24.en", EpgChannelKey.normalize("  France24.En "))
        assertEquals("bbc one", EpgChannelKey.normalize("BBC One"))
    }

    @Test
    fun normalizeRejectsBlankAndNull() {
        assertNull(EpgChannelKey.normalize(null))
        assertNull(EpgChannelKey.normalize(""))
        assertNull(EpgChannelKey.normalize("   "))
    }

    @Test
    fun normalizedSourcesMatchEachOther() {
        // Xtream epg_channel_id, M3U tvg-id and XMLTV channel differ only by case.
        val xtream = EpgChannelKey.normalize("TF1.fr")
        val m3u = EpgChannelKey.normalize("tf1.FR")
        val xmltv = EpgChannelKey.normalize(" tf1.fr")
        assertEquals(xtream, m3u)
        assertEquals(m3u, xmltv)
    }

    // ---- EpgRetentionPolicy ----

    private val now = 1_000_000_000_000L

    @Test
    fun storesCurrentAndNearFutureProgrammes() {
        assertTrue(EpgRetentionPolicy.shouldStore(now - HOUR, now + HOUR, now))
        assertTrue(EpgRetentionPolicy.shouldStore(now + HOUR, now + 2 * HOUR, now))
    }

    @Test
    fun dropsProgrammesEndedBeforePastRetention() {
        val tooOldEnd = now - EpgRetentionPolicy.PAST_RETENTION_MS - 1
        assertFalse(EpgRetentionPolicy.shouldStore(tooOldEnd - HOUR, tooOldEnd, now))
        // Still inside the past tail: kept.
        val insideTail = now - EpgRetentionPolicy.PAST_RETENTION_MS + HOUR
        assertTrue(EpgRetentionPolicy.shouldStore(insideTail - HOUR, insideTail, now))
    }

    @Test
    fun dropsProgrammesStartingBeyondFutureRetention() {
        val tooFar = now + EpgRetentionPolicy.FUTURE_RETENTION_MS + 1
        assertFalse(EpgRetentionPolicy.shouldStore(tooFar, tooFar + HOUR, now))
        val insideWindow = now + EpgRetentionPolicy.FUTURE_RETENTION_MS - HOUR
        assertTrue(EpgRetentionPolicy.shouldStore(insideWindow, insideWindow + HOUR, now))
    }

    @Test
    fun dropsMalformedIntervalsFailClosed() {
        assertFalse(EpgRetentionPolicy.shouldStore(now + HOUR, now + HOUR, now)) // zero length
        assertFalse(EpgRetentionPolicy.shouldStore(now + HOUR, now, now)) // end before start
    }

    // ---- EpgNowNextPolicy ----

    @Test
    fun runningProgrammeIsNowAndFollowingIsNext() {
        val running = programme(start = now - HOUR, end = now + HOUR, title = "Running")
        val upcoming = programme(start = now + HOUR, end = now + 2 * HOUR, title = "Upcoming")

        val result = EpgNowNextPolicy.fromUpcoming(listOf(running, upcoming), now)

        assertEquals("Running", result.now?.title)
        assertEquals("Upcoming", result.next?.title)
    }

    @Test
    fun guideGapKeepsFutureProgrammeAsNextOnly() {
        val future = programme(start = now + HOUR, end = now + 2 * HOUR, title = "Later")

        val result = EpgNowNextPolicy.fromUpcoming(listOf(future), now)

        assertNull(result.now)
        assertEquals("Later", result.next?.title)
    }

    @Test
    fun emptyUpcomingListYieldsEmptyNowNext() {
        assertEquals(EpgNowNext.EMPTY, EpgNowNextPolicy.fromUpcoming(emptyList(), now))
    }

    @Test
    fun boundaryStartCountsAsAiring() {
        val exact = programme(start = now, end = now + HOUR, title = "Starting")
        val result = EpgNowNextPolicy.fromUpcoming(listOf(exact), now)
        assertEquals("Starting", result.now?.title)
        assertNull(result.next)
    }

    private fun programme(start: Long, end: Long, title: String) = EpgProgramme(
        playlistId = 1L,
        epgChannelId = "chan.one",
        startMs = start,
        endMs = end,
        title = title,
    )

    private companion object {
        const val HOUR = 60L * 60 * 1000
    }
}
