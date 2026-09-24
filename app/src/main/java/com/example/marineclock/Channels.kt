package com.example.marineclock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** The single "Ship's bell" channel; turning it off silences the bell. */
object Channels {
    const val BELL_CHANNEL_ID = "ships_bell"

    /** Creates the channel if missing. Safe to call repeatedly (user settings are preserved). */
    fun ensure(context: Context) {
        val channel = NotificationChannel(
            BELL_CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** False only when the user has switched the channel off. */
    fun isEnabled(context: Context): Boolean {
        ensure(context)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(BELL_CHANNEL_ID)
        return channel?.importance != NotificationManager.IMPORTANCE_NONE
    }
}
