package com.example.marineclock

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BellMathTest {

    private val utc = ZoneId.of("UTC")
    private val london = ZoneId.of("Europe/London")

    private fun at(h: Int, m: Int, s: Int = 0, nanos: Int = 0) =
        ZonedDateTime.of(2026, 9, 24, h, m, s, nanos, utc)

    // --- bellsAt ---

    @Test
    fun bellsAt_keyTimes() {
        assertEquals(8, BellMath.bellsAt(LocalTime.of(0, 0)))
        assertEquals(1, BellMath.bellsAt(LocalTime.of(0, 30)))
        assertEquals(2, BellMath.bellsAt(LocalTime.of(1, 0)))
        assertEquals(7, BellMath.bellsAt(LocalTime.of(3, 30)))
        assertEquals(8, BellMath.bellsAt(LocalTime.of(4, 0)))
        assertEquals(1, BellMath.bellsAt(LocalTime.of(4, 30)))
        assertEquals(8, BellMath.bellsAt(LocalTime.of(12, 0)))
        assertEquals(5, BellMath.bellsAt(LocalTime.of(22, 30)))
        assertEquals(7, BellMath.bellsAt(LocalTime.of(23, 30)))
    }

    @Test
    fun bellsAt_allHalfHoursFollowEightBellCycle() {
        val cycle = listOf(8, 1, 2, 3, 4, 5, 6, 7)
        for (halfHour in 0 until 48) {
            val time = LocalTime.of(halfHour / 2, (halfHour % 2) * 30)
            assertEquals("at $time", cycle[halfHour % 8], BellMath.bellsAt(time))
        }
    }

    // --- nextBoundary ---

    @Test
    fun nextBoundary_exactlyOnTheHour_movesToHalfPast() {
        assertEquals(at(12, 30), BellMath.nextBoundary(at(12, 0)))
    }

    @Test
    fun nextBoundary_exactlyOnHalfPast_movesToNextHour() {
        assertEquals(at(13, 0), BellMath.nextBoundary(at(12, 30)))
    }

    @Test
    fun nextBoundary_justBeforeHalfPast() {
        assertEquals(at(12, 30), BellMath.nextBoundary(at(12, 29, 59, 999_000_000)))
    }

    @Test
    fun nextBoundary_justBeforeTheHour() {
        assertEquals(at(13, 0), BellMath.nextBoundary(at(12, 59, 59)))
    }

    @Test
    fun nextBoundary_acrossMidnight() {
        val expected = ZonedDateTime.of(2026, 9, 25, 0, 0, 0, 0, utc)
        assertEquals(expected, BellMath.nextBoundary(at(23, 45)))
    }

    @Test
    fun nextBoundary_springForward_skipsMissingHour() {
        // London 2026-03-29: 01:00 GMT jumps to 02:00 BST.
        val now = ZonedDateTime.ofInstant(Instant.parse("2026-03-29T00:45:00Z"), london)
        val next = BellMath.nextBoundary(now)
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), next.toInstant())
        assertEquals(LocalTime.of(2, 0), next.toLocalTime())
    }

    @Test
    fun nextBoundary_fallBack_ringsRepeatedHour() {
        // London 2026-10-25: 02:00 BST falls back to 01:00 GMT; 01:00–01:59 happens twice.
        val now = ZonedDateTime.ofLocal(
            LocalDateTime.of(2026, 10, 25, 1, 45), london, ZoneOffset.ofHours(1)
        )
        val next = BellMath.nextBoundary(now)
        assertEquals(Instant.parse("2026-10-25T01:00:00Z"), next.toInstant())
        assertEquals(LocalTime.of(1, 0), next.toLocalTime())
    }
}
