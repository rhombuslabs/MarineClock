package com.example.marineclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the bell after reboot, app update, or a clock / time-zone change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in HANDLED_ACTIONS) {
            BellScheduler.scheduleNext(context)
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
