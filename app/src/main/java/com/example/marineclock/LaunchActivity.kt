package com.example.marineclock

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

/** Invisible launcher: arms the bell schedule, asks for notification permission, then closes. */
class LaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Channels.ensure(this)
        BellScheduler.scheduleNext(this)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            finish()
        } else if (savedInstanceState == null) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1
    }
}
