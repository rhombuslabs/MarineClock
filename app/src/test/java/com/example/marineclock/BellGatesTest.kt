package com.example.marineclock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BellGatesTest {

    private val scheduled = 1_000_000_000_000L

    private fun ring(
        notifications: Boolean = true,
        channel: Boolean = true,
        dnd: Boolean = false,
        now: Long = scheduled,
    ) = BellGates.shouldRing(notifications, channel, dnd, scheduled, now)

    @Test fun ringsWhenAllGatesPass() = assertTrue(ring())
    @Test fun silentWhenAppNotificationsOff() = assertFalse(ring(notifications = false))
    @Test fun silentWhenChannelOff() = assertFalse(ring(channel = false))
    @Test fun silentWhenDndActive() = assertFalse(ring(dnd = true))
    @Test fun ringsWhenExactlyTwoMinutesLate() = assertTrue(ring(now = scheduled + 120_000))
    @Test fun silentWhenMoreThanTwoMinutesLate() = assertFalse(ring(now = scheduled + 120_001))
    @Test fun ringsWhenSlightlyEarly() = assertTrue(ring(now = scheduled - 50))
}
