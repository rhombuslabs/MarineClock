package com.example.marineclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChannelsTest {

    @Test
    fun channelId_isVersionedPerCount() {
        assertEquals("bells_1_v1", Channels.channelId(1))
        assertEquals("bells_8_v1", Channels.channelId(8))
    }

    @Test
    fun soundUri_usesRawResourceName() {
        assertEquals(
            "android.resource://com.example.marineclock/raw/bells_3",
            Channels.soundUri("com.example.marineclock", 3),
        )
    }

    @Test
    fun rejectsOutOfRangeCounts() {
        assertThrows(IllegalArgumentException::class.java) { Channels.channelId(0) }
        assertThrows(IllegalArgumentException::class.java) { Channels.channelId(9) }
        assertThrows(IllegalArgumentException::class.java) { Channels.soundUri("p", 0) }
    }
}
