package com.example.marineclock

import android.app.NotificationManager
import android.content.Context

/** Decides whether a scheduled bell may ring. */
object BellGates {
    /** Bells delivered later than this are skipped rather than ringing the wrong time. */
    const val MAX_LATENESS_MS = 120_000L

    fun shouldRing(
        notificationsEnabled: Boolean,
        channelEnabled: Boolean,
        dndActive: Boolean,
        scheduledAtMs: Long,
        nowMs: Long,
    ): Boolean =
        notificationsEnabled &&
            channelEnabled &&
            !dndActive &&
            nowMs - scheduledAtMs <= MAX_LATENESS_MS

    /** Reads the live system state and applies [shouldRing]. */
    fun shouldRingNow(context: Context, bells: Int, scheduledAtMs: Long, nowMs: Long): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return shouldRing(
            notificationsEnabled = nm.areNotificationsEnabled(),
            channelEnabled = Channels.isEnabled(context, bells),
            dndActive = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
            scheduledAtMs = scheduledAtMs,
            nowMs = nowMs,
        )
    }
}
