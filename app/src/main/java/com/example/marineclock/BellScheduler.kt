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
    const val EXTRA_SCHEDULED_AT = "com.example.marineclock.SCHEDULED_AT"

    /** Arms (or replaces) the alarm for the first boundary strictly after [from]. */
    fun scheduleNext(context: Context, from: ZonedDateTime = ZonedDateTime.now()) {
        val next = BellMath.nextBoundary(from)
        val triggerAtMs = next.toInstant().toEpochMilli()
        val intent = Intent(context, BellAlarmReceiver::class.java)
            .putExtra(EXTRA_SCHEDULED_AT, triggerAtMs)
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_BELL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
        Log.i(TAG, "Next bell scheduled for $next")
    }
}
