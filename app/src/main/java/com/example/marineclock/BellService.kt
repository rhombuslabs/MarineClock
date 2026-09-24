package com.example.marineclock

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/** Short-lived foreground service that strikes the bell [EXTRA_BELLS] times, then stops. */
class BellService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var soundPool: SoundPool? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var ringing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bells = intent?.getIntExtra(EXTRA_BELLS, 0) ?: 0
        Channels.ensure(this)
        // Every startForegroundService() call must be answered with startForeground().
        startForeground(
            NOTIFICATION_ID,
            buildNotification(bells.coerceIn(1, 8)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
        )
        when {
            ringing -> Log.i(TAG, "Already ringing; ignoring request for $bells bells")
            bells !in 1..8 -> {
                Log.w(TAG, "Invalid bell count $bells")
                finish()
            }
            else -> {
                ringing = true
                ring(bells)
            }
        }
        return START_NOT_STICKY
    }

    private fun ring(bells: Int) {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShipsBell:ring")
            .apply { acquire(SAFETY_TIMEOUT_MS) }
        handler.postDelayed({ finish() }, SAFETY_TIMEOUT_MS)

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        requestFocus(attributes)

        val pool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attributes)
            .build()
        soundPool = pool
        pool.setOnLoadCompleteListener { loaded, sampleId, status ->
            if (status != 0) {
                Log.w(TAG, "Bell sample failed to load (status $status)")
                finish()
                return@setOnLoadCompleteListener
            }
            val start = SystemClock.uptimeMillis()
            for (offset in BellMath.strikeOffsetsMs(bells)) {
                handler.postAtTime({ loaded.play(sampleId, 1f, 1f, 1, 0, 1f) }, start + offset)
            }
            handler.postAtTime({ finish() }, start + BellMath.chimeDurationMs(bells))
            Log.i(TAG, "Ringing $bells bells")
        }
        pool.load(this, R.raw.ships_bell, 1)
    }

    private fun requestFocus(attributes: AudioAttributes) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()
        focusRequest = request
        // Ring even if focus is denied: the gates already decided the bell should sound.
        getSystemService(AudioManager::class.java).requestAudioFocus(request)
    }

    private fun buildNotification(bells: Int): Notification =
        Notification.Builder(this, Channels.BELL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(resources.getQuantityString(R.plurals.bells, bells, bells))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    /** Releases everything and stops. Idempotent. */
    private fun finish() {
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun release() {
        handler.removeCallbacksAndMessages(null)
        soundPool?.release()
        soundPool = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // shortService timeout callback; the two-arg form (API 35) is overridden too so
    // cleanup happens whichever one the platform calls.
    override fun onTimeout(startId: Int) {
        Log.w(TAG, "Short-service timeout; stopping")
        finish()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground-service timeout; stopping")
        finish()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ShipsBell"
        private const val NOTIFICATION_ID = 1
        private const val SAFETY_TIMEOUT_MS = 10_000L
        const val EXTRA_BELLS = "com.example.marineclock.BELLS"

        fun start(context: Context, bells: Int) {
            val intent = Intent(context, BellService::class.java).putExtra(EXTRA_BELLS, bells)
            context.startForegroundService(intent)
        }
    }
}
