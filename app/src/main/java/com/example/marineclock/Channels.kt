package com.example.marineclock

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri

/**
 * One notification channel per bell count, grouped under "Ship's bell". Each channel's sound is
 * the pre-rendered chime for that count, so Android itself plays the bell at notification volume.
 * The channel stores the sound URI by resource name and resolves it at play time, so re-rendering
 * `bells_N.ogg` in place takes effect without a bump; bump [SOUND_VERSION] only when the URI
 * (resource name) or the channel's sound/attribute/importance defaults must change — `ensure()`
 * then deletes the old-version channels (which also discards users' per-count settings).
 */
object Channels {
    const val GROUP_ID = "ships_bell_group"

    /** Bump only when the sound URI (resource name) or a channel's sound/attribute/importance defaults change. */
    private const val SOUND_VERSION = 1
    private const val LEGACY_CHANNEL_ID = "ships_bell"

    fun channelId(bells: Int): String {
        require(bells in 1..8) { "bells must be 1..8, was $bells" }
        return "bells_${bells}_v$SOUND_VERSION"
    }

    /** By resource name, not id: the channel stores this URI permanently. */
    fun soundUri(packageName: String, bells: Int): String {
        require(bells in 1..8) { "bells must be 1..8, was $bells" }
        return "android.resource://$packageName/raw/bells_$bells"
    }

    /** Creates the group and channels if missing. Safe to call repeatedly (user settings are preserved). */
    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val current = (1..8).map(::channelId).toSet()
        nm.notificationChannels
            .filter { it.id == LEGACY_CHANNEL_ID || (it.id.startsWith("bells_") && it.id !in current) }
            .forEach { nm.deleteNotificationChannel(it.id) }
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ID, context.getString(R.string.channel_group_name)),
        )
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channels = (1..8).map { bells ->
            NotificationChannel(
                channelId(bells),
                context.resources.getQuantityString(R.plurals.bells, bells, bells),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                group = GROUP_ID
                description = context.getString(R.string.channel_description)
                setSound(Uri.parse(soundUri(context.packageName, bells)), attributes)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
        }
        nm.createNotificationChannels(channels)
    }

    /** False when the user has switched off this count's channel or the whole group. */
    fun isEnabled(context: Context, bells: Int): Boolean {
        ensure(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val groupBlocked = nm.getNotificationChannelGroup(GROUP_ID)?.isBlocked == true
        val channel = nm.getNotificationChannel(channelId(bells))
        return !groupBlocked && channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
