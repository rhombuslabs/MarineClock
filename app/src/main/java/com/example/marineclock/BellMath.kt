package com.example.marineclock

import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Pure ship's-bell arithmetic. No Android dependencies. */
object BellMath {
    /** Delay between the two strikes of a pair. */
    const val PAIR_GAP_MS = 400L

    /** Delay from the first strike of one pair to the first strike of the next. */
    const val PAIR_PERIOD_MS = 1200L

    /** How long the last strike rings out (length of the trimmed sample). */
    const val RING_OUT_MS = 2000L

    /** Bells for a half-hour boundary: 00:30 → 1 … 04:00 → 8, repeating every 4 hours. */
    fun bellsAt(time: LocalTime): Int {
        val halfHours = time.hour * 2 + time.minute / 30
        return Math.floorMod(halfHours - 1, 8) + 1
    }

    /** The first :00 or :30 local time strictly after [now]. */
    fun nextBoundary(now: ZonedDateTime): ZonedDateTime {
        val hour = now.truncatedTo(ChronoUnit.HOURS)
        val halfPast = hour.plusMinutes(30)
        return if (now.isBefore(halfPast)) halfPast else hour.plusHours(1)
    }

    /** Millisecond offset of each strike, grouped in pairs. */
    fun strikeOffsetsMs(bells: Int): List<Long> {
        require(bells in 1..8) { "bells must be 1..8, was $bells" }
        return (0 until bells).map { i -> (i / 2) * PAIR_PERIOD_MS + (i % 2) * PAIR_GAP_MS }
    }

    /** Total chime length: last strike plus its ring-out. */
    fun chimeDurationMs(bells: Int): Long = strikeOffsetsMs(bells).last() + RING_OUT_MS
}
