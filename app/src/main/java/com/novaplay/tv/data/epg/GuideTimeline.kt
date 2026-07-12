package com.novaplay.tv.data.epg

import com.novaplay.tv.data.db.EpgProgramme

/** One horizontal element of a channel's guide lane, sized in timeline minutes. */
sealed interface GuideLaneItem {
    val widthMinutes: Int

    /** Empty space between programmes (or before the first one). */
    data class Gap(override val widthMinutes: Int) : GuideLaneItem

    /** A programme cell; [clipped] marks cells cut at the window edge or by an overlap. */
    data class Cell(
        val programme: EpgProgramme,
        override val widthMinutes: Int,
        val clipped: Boolean,
    ) : GuideLaneItem
}

/**
 * Pure timeline math for the grid guide. All horizontal positions are expressed
 * in whole minutes from the window start, so every lane and the time ruler stay
 * pixel-aligned regardless of programme boundaries. The window is frozen when
 * the guide opens; only the airing highlight follows the clock.
 */
object GuideTimeline {
    /** Ruler/label granularity. */
    const val SLOT_MINUTES = 30

    /** Horizontal scale: one timeline minute in dp (a 30-minute slot is 120 dp). */
    const val DP_PER_MINUTE = 4

    // One slot back so the tail of the airing programme stays visible, then a
    // 12-hour forward window — well inside the 48 h the sync retains.
    private const val PAST_SLOTS = 1
    private const val FORWARD_MINUTES = 12 * 60
    const val WINDOW_MINUTES = PAST_SLOTS * SLOT_MINUTES + FORWARD_MINUTES

    private const val MINUTE_MS = 60_000L
    private const val SLOT_MS = SLOT_MINUTES * MINUTE_MS

    /** Rounds down to the enclosing half-hour slot boundary. */
    fun floorToSlotMs(timeMs: Long): Long = timeMs - timeMs.mod(SLOT_MS)

    /** Window start for a guide opened at [nowMs]: one slot before the current one. */
    fun windowStartMs(nowMs: Long): Long = floorToSlotMs(nowMs) - PAST_SLOTS * SLOT_MS

    /** Exclusive end of the visible window. */
    fun windowEndMs(windowStartMs: Long): Long = windowStartMs + WINDOW_MINUTES * MINUTE_MS

    /** Ruler tick timestamps, one per slot across the whole window. */
    fun slotTimesMs(windowStartMs: Long): List<Long> =
        (0 until WINDOW_MINUTES / SLOT_MINUTES).map { windowStartMs + it * SLOT_MS }

    /** Whole minutes between two instants (truncating; guide feeds are minute-aligned). */
    fun minutesBetween(fromMs: Long, toMs: Long): Int = ((toMs - fromMs) / MINUTE_MS).toInt()

    /** Whether [programme] is on air at [nowMs]. */
    fun isAiring(programme: EpgProgramme, nowMs: Long): Boolean =
        programme.startMs <= nowMs && nowMs < programme.endMs

    /** Timeline offset of [nowMs] in minutes, clamped inside the window — the initial scroll target. */
    fun nowOffsetMinutes(windowStartMs: Long, nowMs: Long): Int =
        minutesBetween(windowStartMs, nowMs).coerceIn(0, WINDOW_MINUTES)

    /**
     * Converts one channel's start-ordered programmes into gap/cell lane items.
     * Cells are clipped to the window and to the previous cell's end (overlapping
     * feed entries must never desynchronize the lane from the ruler); entries
     * fully outside the window or fully covered by an overlap are dropped.
     */
    fun laneItems(
        programmes: List<EpgProgramme>,
        windowStartMs: Long,
    ): List<GuideLaneItem> {
        val items = mutableListOf<GuideLaneItem>()
        var cursorMinutes = 0
        for (programme in programmes) {
            val startMinutes = minutesBetween(windowStartMs, programme.startMs)
                .coerceAtLeast(cursorMinutes)
            val endMinutes = minutesBetween(windowStartMs, programme.endMs)
                .coerceAtMost(WINDOW_MINUTES)
            if (endMinutes <= startMinutes) continue

            if (startMinutes > cursorMinutes) {
                items += GuideLaneItem.Gap(startMinutes - cursorMinutes)
            }
            val naturalStart = minutesBetween(windowStartMs, programme.startMs)
            val naturalEnd = minutesBetween(windowStartMs, programme.endMs)
            items += GuideLaneItem.Cell(
                programme = programme,
                widthMinutes = endMinutes - startMinutes,
                clipped = startMinutes > naturalStart || endMinutes < naturalEnd,
            )
            cursorMinutes = endMinutes
        }
        return items
    }
}
