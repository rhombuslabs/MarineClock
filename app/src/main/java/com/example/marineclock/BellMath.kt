package com.example.marineclock

import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Pure ship's-bell arithmetic. No Android dependencies. */
object BellMath {
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
}
