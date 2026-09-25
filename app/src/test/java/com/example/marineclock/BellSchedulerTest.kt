package com.example.marineclock

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BellSchedulerTest {

    @Test
    fun triggerAt_isTwoSecondsAfterBoundary() {
        val boundary = ZonedDateTime.of(2026, 9, 24, 19, 30, 0, 0, ZoneId.of("UTC"))
        assertEquals(boundary.toInstant().toEpochMilli() + 2_000L, BellScheduler.triggerAtMs(boundary))
    }
}
