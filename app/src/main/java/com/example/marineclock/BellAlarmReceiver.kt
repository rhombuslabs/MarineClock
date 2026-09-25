package com.example.marineclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Fires just after each half hour: re-arm first, then ring if the gates allow. */
class BellAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val testBells = intent.getIntExtra(EXTRA_TEST_BELLS, 0)
        if (testBells in 1..8) {
            BellNotifier.ring(context, testBells)
            return
        }

        val nowMs = System.currentTimeMillis()
        val scheduledAtMs = intent.getLongExtra(BellScheduler.EXTRA_SCHEDULED_AT, 0L)
        val zone = ZoneId.systemDefault()

        // Reschedule before anything else so a later failure never breaks the chain.
        // Using max() guards against re-arming the same boundary if delivery is early.
        val from = Instant.ofEpochMilli(maxOf(nowMs, scheduledAtMs))
        BellScheduler.scheduleNext(context, ZonedDateTime.ofInstant(from, zone))

        if (scheduledAtMs == 0L) return
        val bells = BellMath.bellsAt(Instant.ofEpochMilli(scheduledAtMs).atZone(zone).toLocalTime())
        if (!BellGates.shouldRingNow(context, bells, scheduledAtMs, nowMs)) {
            Log.i(TAG, "Bell skipped by gates")
            return
        }
        BellNotifier.ring(context, bells)
    }

    companion object {
        private const val TAG = "ShipsBell"
        const val EXTRA_TEST_BELLS = "com.example.marineclock.TEST_BELLS"
    }
}
