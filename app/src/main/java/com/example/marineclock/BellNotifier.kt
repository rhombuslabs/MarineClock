package com.example.marineclock

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log

/** Rings the bell by posting a notification; Android plays the channel's pre-rendered chime. */
object BellNotifier {
    private const val TAG = "ShipsBell"
    private const val NOTIFICATION_ID = 1

    /** Longer than the longest chime (6.0 s): cancelling a notification stops its sound. */
    const val TIMEOUT_MS = 10_000L

    fun ring(context: Context, bells: Int) {
        Channels.ensure(context)
        val notification = Notification.Builder(context, Channels.channelId(bells))
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.resources.getQuantityString(R.plurals.bells, bells, bells))
            .setTimeoutAfter(TIMEOUT_MS)
            .setLocalOnly(true)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        Log.i(TAG, "Ringing $bells bells")
    }
}
