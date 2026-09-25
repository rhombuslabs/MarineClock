# Ship's Bell Revision 2 (Notification-Sound Playback) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the in-app SoundPool bell player, which Android 17 audio hardening mutes, with notifications whose channel sounds are pre-rendered chimes, so Android itself plays the bell at notification volume.

**Architecture:** The alarm chain and gates stay. The alarm now fires 2 s after each boundary. The receiver posts one notification on one of 8 per-count channels ("1 bell" … "8 bells", grouped). Each channel's sound is a pre-mixed OGG chime rendered offline by a SoX script. `BellService` and all in-app audio code are deleted.

**Tech Stack:** Kotlin (AGP 9.4.1 built-in Kotlin), framework APIs only, JUnit 4, SoX 14.4.2 (PowerShell script).

**Spec:** `docs/superpowers/specs/2026-09-24-ships-bell-design.md`, section "Revision 2".

## Global Constraints

- Package `com.example.marineclock`. minSdk 35, targetSdk 37, compileSdk `release(37)`, Java/JVM 11. No new dependencies; framework APIs only.
- Log tag `"ShipsBell"`.
- Channel group id `ships_bell_group`, name "Ship's bell". Channel ids `bells_<n>_v1` for n = 1..8, named via `R.plurals.bells` ("1 bell", "3 bells"). `IMPORTANCE_DEFAULT`, no vibration, no lights, no badge. The legacy channel id `ships_bell` is deleted.
- Channel sound URI: `android.resource://<packageName>/raw/bells_<n>` (use the name form, never a numeric resource id), with `USAGE_NOTIFICATION` + `CONTENT_TYPE_SONIFICATION`.
- The alarm fires at boundary + `POST_DELAY_MS = 2_000L`. `EXTRA_SCHEDULED_AT` carries the boundary millis.
- Notification: fixed id 1, `setTimeoutAfter(10_000L)`, `setLocalOnly(true)`, title `R.string.notification_title`, text `R.plurals.bells`, icon `R.drawable.ic_bell`.
- Chime timing: strike i at `(i / 2) * 1.2 s + (i % 2) * 0.4 s`, sample length 2.0 s. Expected file durations (s): 1→2.0, 2→2.4, 3→3.2, 4→3.6, 5→4.4, 6→4.8, 7→5.6, 8→6.0.
- Shell is PowerShell. Gradle: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat <tasks> --console=plain`. SoX: `C:\Program Files (x86)\sox-14-4-2\sox.exe`.
- Commit trailer, exactly: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

### Task 1: Switch playback from BellService to notification channels

**Files:**
- Modify: `app/src/main/java/com/example/marineclock/Channels.kt` (rewrite)
- Create: `app/src/main/java/com/example/marineclock/BellNotifier.kt`
- Modify: `app/src/main/java/com/example/marineclock/BellGates.kt` (the `shouldRingNow` signature)
- Modify: `app/src/main/java/com/example/marineclock/BellScheduler.kt`
- Modify: `app/src/main/java/com/example/marineclock/BellAlarmReceiver.kt`
- Modify: `app/src/main/java/com/example/marineclock/BellMath.kt` (remove strike timing)
- Delete: `app/src/main/java/com/example/marineclock/BellService.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Test: create `app/src/test/java/com/example/marineclock/ChannelsTest.kt` and `BellSchedulerTest.kt`; modify `BellMathTest.kt`

**Interfaces produced:**
- `Channels.GROUP_ID`
- `Channels.channelId(bells: Int): String`
- `Channels.soundUri(packageName: String, bells: Int): String`
- `Channels.ensure(context: Context)`
- `Channels.isEnabled(context: Context, bells: Int): Boolean`
- `BellNotifier.ring(context: Context, bells: Int)`
- `BellNotifier.TIMEOUT_MS`
- `BellScheduler.POST_DELAY_MS`
- `BellScheduler.triggerAtMs(boundary: ZonedDateTime): Long`
- `BellGates.shouldRingNow(context: Context, bells: Int, scheduledAtMs: Long, nowMs: Long): Boolean`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/example/marineclock/ChannelsTest.kt`:

```kotlin
package com.example.marineclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChannelsTest {

    @Test
    fun channelId_isVersionedPerCount() {
        assertEquals("bells_1_v1", Channels.channelId(1))
        assertEquals("bells_8_v1", Channels.channelId(8))
    }

    @Test
    fun soundUri_usesRawResourceName() {
        assertEquals(
            "android.resource://com.example.marineclock/raw/bells_3",
            Channels.soundUri("com.example.marineclock", 3),
        )
    }

    @Test
    fun rejectsOutOfRangeCounts() {
        assertThrows(IllegalArgumentException::class.java) { Channels.channelId(0) }
        assertThrows(IllegalArgumentException::class.java) { Channels.channelId(9) }
        assertThrows(IllegalArgumentException::class.java) { Channels.soundUri("p", 0) }
    }
}
```

`app/src/test/java/com/example/marineclock/BellSchedulerTest.kt`:

```kotlin
package com.example.marineclock

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BellSchedulerTest {

    @Test
    fun triggerAt_isTwoSecondsAfterBoundary() {
        val boundary = ZonedDateTime.of(2026, 9, 24, 19, 30, 0, 0, ZoneId.of("UTC"))
        assertEquals(boundary.toInstant().toEpochMilli() + 2_000L, BellScheduler.triggerAtMs(boundary))
    }
}
```

In `app/src/test/java/com/example/marineclock/BellMathTest.kt`, delete these four test functions and nothing else: `strikeOffsets_smallCounts`, `strikeOffsets_eightBells`, `chimeDuration_eightBellsIsSixSeconds`, `strikeOffsets_rejectsOutOfRange`. Remove the `assertThrows` import if it is now unused.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: compile FAIL (`Unresolved reference 'channelId'` / `'triggerAtMs'`).

- [ ] **Step 3: Rewrite Channels.kt**

```kotlin
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
 * Channel sounds can't change after creation: bump [SOUND_VERSION] whenever the audio changes.
 */
object Channels {
    const val GROUP_ID = "ships_bell_group"
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
        nm.deleteNotificationChannel(LEGACY_CHANNEL_ID)
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
```

- [ ] **Step 4: Create BellNotifier.kt**

```kotlin
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
```

- [ ] **Step 5: Update BellGates.shouldRingNow**

Change only `shouldRingNow`. `shouldRing` is unchanged:

```kotlin
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
```

- [ ] **Step 6: Update BellScheduler.kt**

Replace the object body with the following. The imports are unchanged:

```kotlin
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
```

- [ ] **Step 7: Update BellAlarmReceiver.kt**

Replace the whole file with:

```kotlin
package com.example.marineclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Fires just after each half hour: re-arm first, then ring if the gates allow. */
class BellAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val testBells = intent.getIntExtra(EXTRA_TEST_BELLS, 0)
        if (testBells in 1..8) {
            BellNotifier.ring(context, testBells)
            return
        }

        val nowMs = System.currentTimeMillis()
        val scheduledAtMs = intent.getLongExtra(BellScheduler.EXTRA_SCHEDULED_AT, 0L)
        val zone = ZoneId.systemDefault()

        // Reschedule before anything else so a later failure never breaks the chain.
        // Using max() guards against re-arming the same boundary if delivery is early.
        val from = Instant.ofEpochMilli(maxOf(nowMs, scheduledAtMs))
        BellScheduler.scheduleNext(context, ZonedDateTime.ofInstant(from, zone))

        if (scheduledAtMs == 0L) return
        val bells = BellMath.bellsAt(Instant.ofEpochMilli(scheduledAtMs).atZone(zone).toLocalTime())
        if (!BellGates.shouldRingNow(context, bells, scheduledAtMs, nowMs)) {
            Log.i(TAG, "Bell skipped by gates")
            return
        }
        BellNotifier.ring(context, bells)
    }

    companion object {
        private const val TAG = "ShipsBell"
        const val EXTRA_TEST_BELLS = "com.example.marineclock.TEST_BELLS"
    }
}
```

- [ ] **Step 8: Trim BellMath.kt**

Delete `PAIR_GAP_MS`, `PAIR_PERIOD_MS`, `RING_OUT_MS`, `strikeOffsetsMs` and `chimeDurationMs`, along with their doc comments. Keep `bellsAt` and `nextBoundary` exactly as they are.

- [ ] **Step 9: Delete BellService and update the manifest and strings**

```powershell
git rm app/src/main/java/com/example/marineclock/BellService.kt
```

In `app/src/main/AndroidManifest.xml`, delete:
- the `FOREGROUND_SERVICE` `<uses-permission>` line
- the `WAKE_LOCK` `<uses-permission>` line
- the whole `<service android:name=".BellService" … />` element

Change nothing else.

Replace `app/src/main/res/values/strings.xml` with:

```xml
<resources>
    <string name="app_name">Ship\'s Bell</string>
    <string name="channel_group_name">Ship\'s bell</string>
    <string name="channel_description">The ship\'s bell chime for this number of bells. Turn off to silence it.</string>
    <string name="notification_title">Ship\'s bell</string>
    <plurals name="bells">
        <item quantity="one">%d bell</item>
        <item quantity="other">%d bells</item>
    </plurals>
</resources>
```

Leave `app/src/main/res/raw/ships_bell.wav` where it is. Task 2 moves it.

- [ ] **Step 10: Run the tests and build**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, with no compiler warnings. The JUnit XML shows:
- BellMathTest `tests="9"`
- BellGatesTest `tests="7"`
- ChannelsTest `tests="3"`
- BellSchedulerTest `tests="1"`

All with `failures="0"`.

Also run `Select-String -Path app\src -Pattern "BellService|strikeOffsetsMs|chimeDurationMs|BELL_CHANNEL_ID|channel_name\b" -Recurse`. Expected: no matches.

- [ ] **Step 11: Commit**

```powershell
git add -A app
git commit -m @'
feat: ring via per-count notification channels instead of BellService

Android 17 background audio hardening mutes app-played notification audio
from an alarm-started service. The system now plays each chime as a channel
sound; the alarm fires 2 s after the boundary.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 2: Render the chimes

**Files:**
- Move: `app/src/main/res/raw/ships_bell.wav` → `tools/audio/ships_bell.wav`
- Create: `tools/render-chimes.ps1`
- Create (generated): `app/src/main/res/raw/bells_1.ogg` … `bells_8.ogg`

- [ ] **Step 1: Move the source sample**

```powershell
New-Item -ItemType Directory -Force tools\audio | Out-Null
git mv app/src/main/res/raw/ships_bell.wav tools/audio/ships_bell.wav
```

- [ ] **Step 2: Create `tools/render-chimes.ps1`**

```powershell
# Renders app/src/main/res/raw/bells_1.ogg … bells_8.ogg from tools/audio/ships_bell.wav.
# Timing: strike i at (i / 2) * 1.2 s + (i % 2) * 0.4 s; the 2.0 s strike sample is the ring-out.
# Channel sounds are fixed once created: after changing the audio, bump SOUND_VERSION in Channels.kt.
param(
    [string]$Sox = "C:\Program Files (x86)\sox-14-4-2\sox.exe"
)
$ErrorActionPreference = "Stop"
$inv = [Globalization.CultureInfo]::InvariantCulture
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $PSScriptRoot "audio\ships_bell.wav"
$outDir = Join-Path $root "app\src\main\res\raw"
$work = Join-Path ([IO.Path]::GetTempPath()) "ships-bell-render"
New-Item -ItemType Directory -Force $work | Out-Null

$pairGapS = 0.4
$pairPeriodS = 1.2

function Invoke-Sox {
    & $Sox @args
    if ($LASTEXITCODE -ne 0) { throw "sox failed: $args" }
}

$mono = Join-Path $work "strike_mono.wav"
Invoke-Sox $source $mono channels 1

foreach ($bells in 1..8) {
    $out = Join-Path $outDir "bells_$bells.ogg"
    if ($bells -eq 1) {
        Invoke-Sox $mono $out
    } else {
        $mixArgs = @("-m")
        for ($i = 0; $i -lt $bells; $i++) {
            $offset = [math]::Floor($i / 2) * $pairPeriodS + ($i % 2) * $pairGapS
            $strike = Join-Path $work "strike_$i.wav"
            Invoke-Sox $mono $strike pad $offset.ToString($inv)
            $mixArgs += @("-v", "1", $strike)
        }
        Invoke-Sox @mixArgs $out
    }
    $duration = & $Sox --i -D $out
    "bells_$bells.ogg  $duration s"
}
Remove-Item -Recurse -Force $work
```

- [ ] **Step 3: Run it**

Run: `pwsh -NoProfile -File tools\render-chimes.ps1`
Expected: 8 lines. The durations are within ±0.02 s of 2.0, 2.4, 3.2, 3.6, 4.4, 4.8, 5.6 and 6.0, and there are no `clipped` warnings. Then check the peak level and the size:

```powershell
& "C:\Program Files (x86)\sox-14-4-2\sox.exe" app\src\main\res\raw\bells_8.ogg -n stat 2>&1 | Select-String "Maximum amplitude|Minimum amplitude"
(Get-ChildItem app\src\main\res\raw\bells_*.ogg | Measure-Object Length -Sum).Sum
```

Expected: amplitudes within ±1.0, and a total size under 600 KB.

- [ ] **Step 4: Build**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 20 tests passing.

- [ ] **Step 5: Commit**

```powershell
git add -A tools app/src/main/res/raw
git commit -m @'
feat: add pre-rendered 1-8 bell chimes and SoX render script

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 3: On-device verification (controller, Pixel 9 Pro over adb)

`$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"`. Install with `.\gradlew.bat :app:installDebug`, then launch once with `& $adb shell am start -n com.example.marineclock/.LaunchActivity`. The launch creates the channels and re-arms the alarm after Studio's force-stop.

- [ ] Channels: `& $adb shell dumpsys notification | Select-String "bells_[1-8]_v1"` shows 8 channels with `importance=3` and sound `android.resource://com.example.marineclock/raw/bells_N`. The `ships_bell` channel is gone.
- [ ] Ring 1, 5 and 8 via `TEST_RING --ei bells N`, leaving ≥15 s between tries (cooldown). Each chime plays in full. Logcat shows `Ringing N bells` and **no** `AudioHardening` lines for the app.
- [ ] Gated ring (`--ez gated true`) rings the count for the current time.
- [ ] DND: `& $adb shell cmd notification set_dnd on` → gated ring is skipped (`Bell skipped by gates`). Then `set_dnd off`.
- [ ] The user checks the watch doesn't buzz and the notification removes itself after about 10 s.
- [ ] `dumpsys alarm` shows the app alarm at the next boundary + 2 s.
- [ ] The user checks the toggles in Settings: a single channel off → that count is skipped; the group off, or app notifications off → everything is skipped.
- [ ] Collision: while an 8-bell chime plays, post another app's notification (or a second test ring) to observe the "latest wins" behaviour.
