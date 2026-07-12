package com.novaplay.tv.data.epg

import com.novaplay.tv.data.db.EpgProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTimelineTest {

    // 2026-07-12 18:47:00 UTC — deliberately not slot-aligned.
    private val now = 1_783_881_000_000L + 17 * MINUTE
    private val windowStart = GuideTimeline.windowStartMs(now)

    @Test
    fun windowStartsOneSlotBeforeTheCurrentOne() {
        // 18:47 floors to 18:30; one slot back is 18:00.
        assertEquals(0L, windowStart % (30 * MINUTE))
        assertEquals(30 * MINUTE, GuideTimeline.floorToSlotMs(now) - windowStart)
        assertTrue(windowStart <= now)
    }

    @Test
    fun rulerCoversTheWholeWindowInSlotSteps() {
        val slots = GuideTimeline.slotTimesMs(windowStart)
        assertEquals(GuideTimeline.WINDOW_MINUTES / GuideTimeline.SLOT_MINUTES, slots.size)
        assertEquals(windowStart, slots.first())
        assertEquals(windowStart + (slots.size - 1) * 30 * MINUTE, slots.last())
    }

    @Test
    fun nowOffsetIsClampedInsideTheWindow() {
        assertEquals(47, GuideTimeline.nowOffsetMinutes(windowStart, now))
        assertEquals(0, GuideTimeline.nowOffsetMinutes(windowStart, windowStart - HOUR))
        assertEquals(
            GuideTimeline.WINDOW_MINUTES,
            GuideTimeline.nowOffsetMinutes(windowStart, windowStart + 100 * HOUR),
        )
    }

    @Test
    fun airingUsesHalfOpenInterval() {
        val p = programme(start = now - 10 * MINUTE, end = now + 10 * MINUTE)
        assertTrue(GuideTimeline.isAiring(p, now))
        assertTrue(GuideTimeline.isAiring(p, p.startMs))
        assertFalse(GuideTimeline.isAiring(p, p.endMs))
    }

    @Test
    fun laneInsertsLeadingAndInterProgrammeGaps() {
        val a = programme(start = windowStart + 30 * MINUTE, end = windowStart + 60 * MINUTE)
        val b = programme(start = windowStart + 90 * MINUTE, end = windowStart + 120 * MINUTE)

        val lane = GuideTimeline.laneItems(listOf(a, b), windowStart)

        assertEquals(
            listOf(30, 30, 30, 30),
            lane.map { it.widthMinutes },
        )
        assertTrue(lane[0] is GuideLaneItem.Gap)
        assertTrue(lane[1] is GuideLaneItem.Cell)
        assertTrue(lane[2] is GuideLaneItem.Gap)
        assertTrue(lane[3] is GuideLaneItem.Cell)
    }

    @Test
    fun laneClipsCellsAtBothWindowEdges() {
        val early = programme(start = windowStart - HOUR, end = windowStart + 30 * MINUTE)
        val windowEnd = GuideTimeline.windowEndMs(windowStart)
        val late = programme(start = windowEnd - 30 * MINUTE, end = windowEnd + 2 * HOUR)

        val lane = GuideTimeline.laneItems(listOf(early, late), windowStart)

        val cells = lane.filterIsInstance<GuideLaneItem.Cell>()
        assertEquals(2, cells.size)
        assertEquals(30, cells[0].widthMinutes)
        assertTrue(cells[0].clipped)
        assertEquals(30, cells[1].widthMinutes)
        assertTrue(cells[1].clipped)
        // Total lane width never exceeds the window.
        assertTrue(lane.sumOf { it.widthMinutes } <= GuideTimeline.WINDOW_MINUTES)
    }

    @Test
    fun laneDropsEntriesOutsideTheWindowOrCoveredByOverlaps() {
        val before = programme(start = windowStart - 2 * HOUR, end = windowStart - HOUR)
        val base = programme(start = windowStart, end = windowStart + HOUR)
        val covered = programme(start = windowStart + 10 * MINUTE, end = windowStart + 50 * MINUTE)
        val overlapping = programme(start = windowStart + 30 * MINUTE, end = windowStart + 90 * MINUTE)

        val lane = GuideTimeline.laneItems(listOf(before, base, covered, overlapping), windowStart)

        val cells = lane.filterIsInstance<GuideLaneItem.Cell>()
        assertEquals(2, cells.size)
        assertEquals(60, cells[0].widthMinutes)
        // The overlapping entry is clipped to start where the base cell ends.
        assertEquals(30, cells[1].widthMinutes)
        assertTrue(cells[1].clipped)
        // Lane stays contiguous: widths sum to the overlapping entry's end offset.
        assertEquals(90, lane.sumOf { it.widthMinutes })
    }

    private fun programme(start: Long, end: Long) = EpgProgramme(
        playlistId = 1L,
        epgChannelId = "chan.one",
        startMs = start,
        endMs = end,
        title = "P",
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
