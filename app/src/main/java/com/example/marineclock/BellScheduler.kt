package com.example.marineclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.ZonedDateTime

/** Keeps exactly one exact alarm armed for the next half-hour boundary. */
object BellScheduler {
    private const val TAG = "ShipsBell"
    private const val REQUEST_BELL = 0

    /** Epoch millis of the boundary the alarm is for (not the trigger time). */
    const val EXTRA_SCHEDULED_AT = "com.example.marineclock.SCHEDULED_AT"

    /**
     * Ring this long after the boundary. The newest notification sound stops the one playing,
     * so this keeps the chime clear of other apps' alerts timed exactly on :00 / :30.
     */
    const val POST_DELAY_MS = 2_000L

    fun triggerAtMs(boundary: ZonedDateTime): Long = boundary.toInstant().toEpochMilli() + POST_DELAY_MS

    /** Arms (or replaces) the alarm for the first boundary strictly after [from]. */
    fun scheduleNext(context: Context, from: ZonedDateTime = ZonedDateTime.now()) {
        val next = BellMath.nextBoundary(from)
        val intent = Intent(context, BellAlarmReceiver::class.java)
            .putExtra(EXTRA_SCHEDULED_AT, next.toInstant().toEpochMilli())
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_BELL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            context.getSystemService(AlarmManager::class.java)
                .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs(next), pending)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot schedule exact alarm", e)
            return
        }
        Log.i(TAG, "Next bell scheduled for $next")
    }
}
