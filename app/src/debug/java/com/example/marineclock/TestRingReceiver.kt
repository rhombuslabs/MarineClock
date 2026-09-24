package com.example.marineclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug only. Sets an exact alarm 1 s out so the bell rings through the real alarm path.
 *
 *   adb shell am broadcast -a com.example.marineclock.TEST_RING --ei bells 5 \
 *       -p com.example.marineclock --include-stopped-packages
 *
 * Add `--ez gated true` to go through the real gates (toggle, DND) and ring the count
 * for the current time instead of `bells`.
 */
class TestRingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val triggerAtMs = System.currentTimeMillis() + 1_000
        val alarm = Intent(context, BellAlarmReceiver::class.java)
        if (intent.getBooleanExtra("gated", false)) {
            alarm.putExtra(BellScheduler.EXTRA_SCHEDULED_AT, triggerAtMs)
        } else {
            alarm.putExtra(BellAlarmReceiver.EXTRA_TEST_BELLS, intent.getIntExtra("bells", 8).coerceIn(1, 8))
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_TEST,
            alarm,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
    }

    private companion object {
        const val REQUEST_TEST = 1
    }
}
