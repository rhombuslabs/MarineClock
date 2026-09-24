# Ship's Bell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a screenless Android app that rings a ship's bell (1–8 strikes in traditional pairs, 4-hour cycle) on every half hour, at notification volume, silenced by the app's notification toggle and by Do Not Disturb.

**Architecture:** One exact alarm per half hour. `BellAlarmReceiver` schedules the next alarm, checks the gates (notifications enabled, DND off, alarm not stale), and starts `BellService`, a `shortService` foreground service. The service plays the trimmed bell sample through `SoundPool` on a timed schedule and then stops. An invisible `LaunchActivity` requests notification permission once and arms the first alarm. `RescheduleReceiver` re-arms after reboot, update, or clock/time-zone changes.

**Tech Stack:** Kotlin (AGP 9.4.1 built-in Kotlin), Gradle 9.6 wrapper, Android framework APIs only (AlarmManager, SoundPool, NotificationManager), `java.time`, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-24-ships-bell-design.md`

## Global Constraints

- Package / namespace / applicationId: `com.example.marineclock`. App label: `Ship's Bell`.
- minSdk 35, targetSdk 37, compileSdk `release(37)`; Java/JVM target 11. Do not change these.
- No new third-party dependencies. Use framework APIs (`android.app.Notification.Builder`, not NotificationCompat). Remove `appcompat` and `material`.
- Sources go in `app/src/main/java/com/example/marineclock/`, unit tests in `app/src/test/java/com/example/marineclock/`, debug-only sources in `app/src/debug/java/com/example/marineclock/`.
- Log tag everywhere: `"ShipsBell"`.
- Notification channel id `ships_bell`, name "Ship's bell", `IMPORTANCE_LOW`, no sound, no vibration, no badge.
- Strike timing: gap within a pair 400 ms, pair period 1200 ms, ring-out 2000 ms; 8 bells end at 6000 ms.
- Stale-alarm limit: 2 minutes (120 000 ms).
- Audio: `res/raw/ships_bell.wav` (already committed; do not modify). `USAGE_NOTIFICATION` + `CONTENT_TYPE_SONIFICATION`.
- Shell is **PowerShell** on Windows. Every Gradle command needs JAVA_HOME set first:
  `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat <tasks> --console=plain`
- adb: `$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"` then `& $adb ...`.
- Commit messages end with a blank line then `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Use a PowerShell single-quoted here-string (`@'` … `'@` with the closing `'@` at column 0).

## File Map

| File | Responsibility |
|---|---|
| `app/build.gradle.kts` | Namespace/appId rename, remove appcompat/material |
| `gradle/libs.versions.toml` | Drop appcompat/material entries |
| `settings.gradle.kts` | Root project name `MarineClock` |
| `app/src/main/AndroidManifest.xml` | Permissions, activity, service, receivers |
| `app/src/main/res/values/strings.xml` | All user-visible strings |
| `app/src/main/res/values/themes.xml` | Invisible translucent theme |
| `app/src/main/res/drawable/ic_bell.xml` | Notification small icon |
| `.../BellMath.kt` | Pure: bell count, next boundary, strike offsets |
| `.../BellGates.kt` | Pure `shouldRing` plus Android adapter `shouldRingNow` |
| `.../Channels.kt` | Create and query the notification channel |
| `.../BellService.kt` | Short foreground service that plays the strikes |
| `.../BellScheduler.kt` | Arms the exact alarm for the next boundary |
| `.../BellAlarmReceiver.kt` | Alarm handler: reschedule → gates → start service |
| `.../RescheduleReceiver.kt` | Re-arm on boot / update / time / time-zone change |
| `.../LaunchActivity.kt` | Invisible launcher: permission request + arm |
| `app/src/debug/AndroidManifest.xml` + `.../TestRingReceiver.kt` | Debug-only adb trigger |
| `app/src/test/.../BellMathTest.kt`, `BellGatesTest.kt` | JVM unit tests |

---

### Task 1: Rename package and strip the template

**Files:**
- Modify: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Delete: `app/src/main/res/values-night/themes.xml`, `app/src/main/res/values/colors.xml`, `app/src/test/java/com/example/myapplication/ExampleUnitTest.kt`, `app/src/androidTest/java/com/example/myapplication/ExampleInstrumentedTest.kt`

**Interfaces:**
- Produces: namespace `com.example.marineclock` (so `R` is `com.example.marineclock.R`); style `@style/Theme.ShipsBell.Invisible`; string `@string/app_name`.

- [ ] **Step 1: Edit `app/build.gradle.kts`**

Replace the whole file with:

```kotlin
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.marineclock"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.marineclock"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
```

- [ ] **Step 2: Edit `gradle/libs.versions.toml`**

Delete these four lines (and nothing else):

```toml
appcompat = "1.6.1"
material = "1.10.0"
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
```

- [ ] **Step 3: Edit `settings.gradle.kts`**

Change `rootProject.name = "My Application"` to `rootProject.name = "MarineClock"`.

- [ ] **Step 4: Replace resources**

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Ship\'s Bell</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<resources>
    <!-- Fully transparent, no-title theme for the screenless LaunchActivity. -->
    <style name="Theme.ShipsBell.Invisible" parent="android:Theme.Translucent.NoTitleBar">
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:backgroundDimEnabled">false</item>
        <item name="android:windowAnimationStyle">@null</item>
    </style>
</resources>
```

Delete `app/src/main/res/values-night/themes.xml` and `app/src/main/res/values/colors.xml`.

- [ ] **Step 5: Update the manifest theme reference**

In `app/src/main/AndroidManifest.xml` change `android:theme="@style/Theme.MyApplication"` to `android:theme="@style/Theme.ShipsBell.Invisible"`. Leave everything else as it is.

- [ ] **Step 6: Delete the template example tests**

```powershell
Remove-Item -Recurse -Force app\src\test\java\com\example\myapplication, app\src\androidTest\java\com\example\myapplication
Remove-Item -Force app\src\main\res\values-night\themes.xml, app\src\main\res\values\colors.xml
Remove-Item -Force app\src\main\res\values-night -ErrorAction SilentlyContinue
```

- [ ] **Step 7: Build to verify**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`. A failure mentioning `colorPrimary` or `Theme.MaterialComponents` means a reference was missed; `Select-String -Path app\src -Pattern MyApplication,purple_ -Recurse` finds it.

- [ ] **Step 8: Commit**

```powershell
git add -A app gradle settings.gradle.kts
git commit -m @'
chore: rename package to com.example.marineclock and strip template UI

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 2: BellMath (bell count, next boundary, strike offsets)

**Files:**
- Create: `app/src/main/java/com/example/marineclock/BellMath.kt`
- Test: `app/src/test/java/com/example/marineclock/BellMathTest.kt`

**Interfaces:**
- Produces (used by Tasks 4, 5):
  - `object BellMath`
  - `const val PAIR_GAP_MS: Long = 400`, `const val PAIR_PERIOD_MS: Long = 1200`, `const val RING_OUT_MS: Long = 2000`
  - `fun bellsAt(time: java.time.LocalTime): Int` → 1..8
  - `fun nextBoundary(now: java.time.ZonedDateTime): java.time.ZonedDateTime` → strictly after `now`
  - `fun strikeOffsetsMs(bells: Int): List<Long>` → requires `bells in 1..8`
  - `fun chimeDurationMs(bells: Int): Long` → last offset + `RING_OUT_MS`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/example/marineclock/BellMathTest.kt`:

```kotlin
package com.example.marineclock

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BellMathTest {

    private val utc = ZoneId.of("UTC")
    private val london = ZoneId.of("Europe/London")

    private fun at(h: Int, m: Int, s: Int = 0, nanos: Int = 0) =
        ZonedDateTime.of(2026, 9, 24, h, m, s, nanos, utc)

    // --- bellsAt ---

    @Test
    fun bellsAt_keyTimes() {
        assertEquals(8, BellMath.bellsAt(LocalTime.of(0, 0)))
        assertEquals(1, BellMath.bellsAt(LocalTime.of(0, 30)))
        assertEquals(2, BellMath.bellsAt(LocalTime.of(1, 0)))
        assertEquals(7, BellMath.bellsAt(LocalTime.of(3, 30)))
        assertEquals(8, BellMath.bellsAt(LocalTime.of(4, 0)))
        assertEquals(1, BellMath.bellsAt(LocalTime.of(4, 30)))
        assertEquals(8, BellMath.bellsAt(LocalTime.of(12, 0)))
        assertEquals(5, BellMath.bellsAt(LocalTime.of(22, 30)))
        assertEquals(7, BellMath.bellsAt(LocalTime.of(23, 30)))
    }

    @Test
    fun bellsAt_allHalfHoursFollowEightBellCycle() {
        val cycle = listOf(8, 1, 2, 3, 4, 5, 6, 7)
        for (halfHour in 0 until 48) {
            val time = LocalTime.of(halfHour / 2, (halfHour % 2) * 30)
            assertEquals("at $time", cycle[halfHour % 8], BellMath.bellsAt(time))
        }
    }

    // --- nextBoundary ---

    @Test
    fun nextBoundary_exactlyOnTheHour_movesToHalfPast() {
        assertEquals(at(12, 30), BellMath.nextBoundary(at(12, 0)))
    }

    @Test
    fun nextBoundary_exactlyOnHalfPast_movesToNextHour() {
        assertEquals(at(13, 0), BellMath.nextBoundary(at(12, 30)))
    }

    @Test
    fun nextBoundary_justBeforeHalfPast() {
        assertEquals(at(12, 30), BellMath.nextBoundary(at(12, 29, 59, 999_000_000)))
    }

    @Test
    fun nextBoundary_justBeforeTheHour() {
        assertEquals(at(13, 0), BellMath.nextBoundary(at(12, 59, 59)))
    }

    @Test
    fun nextBoundary_acrossMidnight() {
        val expected = ZonedDateTime.of(2026, 9, 25, 0, 0, 0, 0, utc)
        assertEquals(expected, BellMath.nextBoundary(at(23, 45)))
    }

    @Test
    fun nextBoundary_springForward_skipsMissingHour() {
        // London 2026-03-29: 01:00 GMT jumps to 02:00 BST.
        val now = ZonedDateTime.ofInstant(Instant.parse("2026-03-29T00:45:00Z"), london)
        val next = BellMath.nextBoundary(now)
        assertEquals(Instant.parse("2026-03-29T01:00:00Z"), next.toInstant())
        assertEquals(LocalTime.of(2, 0), next.toLocalTime())
    }

    @Test
    fun nextBoundary_fallBack_ringsRepeatedHour() {
        // London 2026-10-25: 02:00 BST falls back to 01:00 GMT; 01:00–01:59 happens twice.
        val now = ZonedDateTime.ofLocal(
            LocalDateTime.of(2026, 10, 25, 1, 45), london, ZoneOffset.ofHours(1)
        )
        val next = BellMath.nextBoundary(now)
        assertEquals(Instant.parse("2026-10-25T01:00:00Z"), next.toInstant())
        assertEquals(LocalTime.of(1, 0), next.toLocalTime())
    }

    // --- strikeOffsetsMs / chimeDurationMs ---

    @Test
    fun strikeOffsets_smallCounts() {
        assertEquals(listOf(0L), BellMath.strikeOffsetsMs(1))
        assertEquals(listOf(0L, 400L), BellMath.strikeOffsetsMs(2))
        assertEquals(listOf(0L, 400L, 1200L), BellMath.strikeOffsetsMs(3))
    }

    @Test
    fun strikeOffsets_eightBells() {
        assertEquals(
            listOf(0L, 400L, 1200L, 1600L, 2400L, 2800L, 3600L, 4000L),
            BellMath.strikeOffsetsMs(8)
        )
    }

    @Test
    fun chimeDuration_eightBellsIsSixSeconds() {
        assertEquals(6000L, BellMath.chimeDurationMs(8))
        assertEquals(2000L, BellMath.chimeDurationMs(1))
    }

    @Test
    fun strikeOffsets_rejectsOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) { BellMath.strikeOffsetsMs(0) }
        assertThrows(IllegalArgumentException::class.java) { BellMath.strikeOffsetsMs(9) }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: FAIL (compile error `Unresolved reference 'BellMath'`).

- [ ] **Step 3: Implement BellMath**

`app/src/main/java/com/example/marineclock/BellMath.kt`:

```kotlin
package com.example.marineclock

import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Pure ship's-bell arithmetic. No Android dependencies. */
object BellMath {
    /** Delay between the two strikes of a pair. */
    const val PAIR_GAP_MS = 400L

    /** Delay from the first strike of one pair to the first strike of the next. */
    const val PAIR_PERIOD_MS = 1200L

    /** How long the last strike rings out (length of the trimmed sample). */
    const val RING_OUT_MS = 2000L

    /** Bells for a half-hour boundary: 00:30 → 1 … 04:00 → 8, repeating every 4 hours. */
    fun bellsAt(time: LocalTime): Int {
        val halfHours = time.hour * 2 + time.minute / 30
        return Math.floorMod(halfHours - 1, 8) + 1
    }

    /** The first :00 or :30 local time strictly after [now]. */
    fun nextBoundary(now: ZonedDateTime): ZonedDateTime {
        val hour = now.truncatedTo(ChronoUnit.HOURS)
        val halfPast = hour.plusMinutes(30)
        return if (now.isBefore(halfPast)) halfPast else hour.plusHours(1)
    }

    /** Millisecond offset of each strike, grouped in pairs. */
    fun strikeOffsetsMs(bells: Int): List<Long> {
        require(bells in 1..8) { "bells must be 1..8, was $bells" }
        return (0 until bells).map { i -> (i / 2) * PAIR_PERIOD_MS + (i % 2) * PAIR_GAP_MS }
    }

    /** Total chime length: last strike plus its ring-out. */
    fun chimeDurationMs(bells: Int): Long = strikeOffsetsMs(bells).last() + RING_OUT_MS
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`. Check `app\build\test-results\testDebugUnitTest\TEST-com.example.marineclock.BellMathTest.xml` shows `tests="13" failures="0"`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/example/marineclock/BellMath.kt app/src/test/java/com/example/marineclock/BellMathTest.kt
git commit -m @'
feat: add BellMath for bell count, next half-hour and strike timing

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 3: Notification channel and ring gates

**Files:**
- Create: `app/src/main/java/com/example/marineclock/Channels.kt`
- Create: `app/src/main/java/com/example/marineclock/BellGates.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/example/marineclock/BellGatesTest.kt`

**Interfaces:**
- Produces (used by Tasks 4, 5, 6):
  - `object Channels { const val BELL_CHANNEL_ID = "ships_bell"; fun ensure(context: Context); fun isEnabled(context: Context): Boolean }`
  - `object BellGates { const val MAX_LATENESS_MS = 120_000L; fun shouldRing(notificationsEnabled: Boolean, channelEnabled: Boolean, dndActive: Boolean, scheduledAtMs: Long, nowMs: Long): Boolean; fun shouldRingNow(context: Context, scheduledAtMs: Long, nowMs: Long): Boolean }`
  - Strings: `channel_name`, `channel_description`

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/example/marineclock/BellGatesTest.kt`:

```kotlin
package com.example.marineclock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BellGatesTest {

    private val scheduled = 1_000_000_000_000L

    private fun ring(
        notifications: Boolean = true,
        channel: Boolean = true,
        dnd: Boolean = false,
        now: Long = scheduled,
    ) = BellGates.shouldRing(notifications, channel, dnd, scheduled, now)

    @Test fun ringsWhenAllGatesPass() = assertTrue(ring())
    @Test fun silentWhenAppNotificationsOff() = assertFalse(ring(notifications = false))
    @Test fun silentWhenChannelOff() = assertFalse(ring(channel = false))
    @Test fun silentWhenDndActive() = assertFalse(ring(dnd = true))
    @Test fun ringsWhenExactlyTwoMinutesLate() = assertTrue(ring(now = scheduled + 120_000))
    @Test fun silentWhenMoreThanTwoMinutesLate() = assertFalse(ring(now = scheduled + 120_001))
    @Test fun ringsWhenSlightlyEarly() = assertTrue(ring(now = scheduled - 50))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: FAIL (`Unresolved reference 'BellGates'`).

- [ ] **Step 3: Add strings**

Replace `app/src/main/res/values/strings.xml` with:

```xml
<resources>
    <string name="app_name">Ship\'s Bell</string>
    <string name="channel_name">Ship\'s bell</string>
    <string name="channel_description">Rings the ship\'s bell every half hour. Turn off to silence the bell.</string>
</resources>
```

- [ ] **Step 4: Implement Channels**

`app/src/main/java/com/example/marineclock/Channels.kt`:

```kotlin
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
```

- [ ] **Step 5: Implement BellGates**

`app/src/main/java/com/example/marineclock/BellGates.kt`:

```kotlin
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
    fun shouldRingNow(context: Context, scheduledAtMs: Long, nowMs: Long): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return shouldRing(
            notificationsEnabled = nm.areNotificationsEnabled(),
            channelEnabled = Channels.isEnabled(context),
            dndActive = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
            scheduledAtMs = scheduledAtMs,
            nowMs = nowMs,
        )
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `TEST-com.example.marineclock.BellGatesTest.xml` shows `tests="7" failures="0"`, and BellMathTest still passes.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/example/marineclock/Channels.kt app/src/main/java/com/example/marineclock/BellGates.kt app/src/test/java/com/example/marineclock/BellGatesTest.kt app/src/main/res/values/strings.xml
git commit -m @'
feat: add notification channel and ring gates (toggle, DND, stale alarm)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 4: BellService (plays the strikes)

**Files:**
- Create: `app/src/main/java/com/example/marineclock/BellService.kt`
- Create: `app/src/main/res/drawable/ic_bell.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `BellMath.strikeOffsetsMs`, `BellMath.chimeDurationMs`, `Channels.ensure`, `Channels.BELL_CHANNEL_ID`
- Produces (used by Task 5): `BellService.start(context: Context, bells: Int)` in the companion. It calls `startForegroundService` and may throw `ForegroundServiceStartNotAllowedException`.

Notes for the implementer:
- `shortService` needs no type-specific permission, only `FOREGROUND_SERVICE`. Android may defer showing a short service's notification for a few seconds, so for short chimes it often never appears. That is intended; do **not** set `FOREGROUND_SERVICE_IMMEDIATE`.
- Strikes are posted with `Handler.postAtTime` relative to one `SystemClock.uptimeMillis()` start, so there is no drift. The partial wake lock keeps uptime advancing with the screen off.
- A 10 s safety timeout guarantees cleanup even if the sample never loads.

- [ ] **Step 1: Add strings**

Replace `app/src/main/res/values/strings.xml` with:

```xml
<resources>
    <string name="app_name">Ship\'s Bell</string>
    <string name="channel_name">Ship\'s bell</string>
    <string name="channel_description">Rings the ship\'s bell every half hour. Turn off to silence the bell.</string>
    <string name="notification_title">Ship\'s bell</string>
    <plurals name="bells">
        <item quantity="one">%d bell</item>
        <item quantity="other">%d bells</item>
    </plurals>
</resources>
```

- [ ] **Step 2: Add the notification icon**

`app/src/main/res/drawable/ic_bell.xml` (Material "notifications" glyph):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32L13.5,4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z" />
</vector>
```

- [ ] **Step 3: Implement BellService**

`app/src/main/java/com/example/marineclock/BellService.kt`:

```kotlin
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
```

- [ ] **Step 4: Register the service and permissions**

Replace `app/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.ShipsBell.Invisible">

        <service
            android:name=".BellService"
            android:exported="false"
            android:foregroundServiceType="shortService" />

    </application>

</manifest>
```

- [ ] **Step 5: Build and run unit tests**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, all tests pass. (The service is exercised on a device in Task 5.)

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/marineclock/BellService.kt app/src/main/res/drawable/ic_bell.xml app/src/main/res/values/strings.xml app/src/main/AndroidManifest.xml
git commit -m @'
feat: add BellService short foreground service that plays paired strikes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 5: Alarm scheduling, alarm receiver, debug test trigger

**Files:**
- Create: `app/src/main/java/com/example/marineclock/BellScheduler.kt`
- Create: `app/src/main/java/com/example/marineclock/BellAlarmReceiver.kt`
- Create: `app/src/debug/java/com/example/marineclock/TestRingReceiver.kt`
- Create: `app/src/debug/AndroidManifest.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `BellMath.nextBoundary`, `BellMath.bellsAt`, `BellGates.shouldRingNow`, `BellService.start`
- Produces (used by Task 6):
  - `object BellScheduler { const val EXTRA_SCHEDULED_AT = "com.example.marineclock.SCHEDULED_AT"; fun scheduleNext(context: Context, from: ZonedDateTime = ZonedDateTime.now()) }`
  - `BellAlarmReceiver.EXTRA_TEST_BELLS = "com.example.marineclock.TEST_BELLS"` (companion const)

Notes for the implementer:
- A broadcast receiver can't start a foreground service from the background, but an exact alarm grants a temporary exemption. So the debug trigger doesn't start the service directly: it sets a one-shot exact alarm 1 s out, and the real alarm path handles it.
- The test alarm uses request code 1 and the real bell uses request code 0, so a test never replaces the real schedule.
- `USE_EXACT_ALARM` is auto-granted to apps that target 33+ and can't be revoked by the user, so no runtime check is needed.

- [ ] **Step 1: Implement BellScheduler**

`app/src/main/java/com/example/marineclock/BellScheduler.kt`:

```kotlin
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
```

- [ ] **Step 2: Implement BellAlarmReceiver**

`app/src/main/java/com/example/marineclock/BellAlarmReceiver.kt`:

```kotlin
package com.example.marineclock

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Fires on each half hour: re-arm first, then ring if the gates allow. */
class BellAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val testBells = intent.getIntExtra(EXTRA_TEST_BELLS, 0)
        if (testBells in 1..8) {
            startBells(context, testBells)
            return
        }

        val nowMs = System.currentTimeMillis()
        val scheduledAtMs = intent.getLongExtra(BellScheduler.EXTRA_SCHEDULED_AT, 0L)
        val zone = ZoneId.systemDefault()

        // Reschedule before anything else so a later failure never breaks the chain.
        // Using max() guards against re-arming the same boundary if delivery is a few ms early.
        val from = Instant.ofEpochMilli(maxOf(nowMs, scheduledAtMs))
        BellScheduler.scheduleNext(context, ZonedDateTime.ofInstant(from, zone))

        if (scheduledAtMs == 0L) return
        if (!BellGates.shouldRingNow(context, scheduledAtMs, nowMs)) {
            Log.i(TAG, "Bell skipped by gates")
            return
        }
        val bells = BellMath.bellsAt(Instant.ofEpochMilli(scheduledAtMs).atZone(zone).toLocalTime())
        startBells(context, bells)
    }

    private fun startBells(context: Context, bells: Int) {
        try {
            BellService.start(context, bells)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Not allowed to start bell service", e)
        }
    }

    companion object {
        private const val TAG = "ShipsBell"
        const val EXTRA_TEST_BELLS = "com.example.marineclock.TEST_BELLS"
    }
}
```

- [ ] **Step 3: Register the receiver and exact-alarm permission**

In `app/src/main/AndroidManifest.xml`, add below the `WAKE_LOCK` permission:

```xml
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
```

and inside `<application>`, after the `<service>` element:

```xml
        <receiver
            android:name=".BellAlarmReceiver"
            android:exported="false" />
```

- [ ] **Step 4: Add the debug-only test trigger**

`app/src/debug/java/com/example/marineclock/TestRingReceiver.kt`:

```kotlin
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
```

`app/src/debug/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <receiver
            android:name=".TestRingReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="com.example.marineclock.TEST_RING" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

- [ ] **Step 5: Build and run unit tests**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Device test: ring on demand**

Needs a device or emulator (`& $adb devices` lists one). Run:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug --console=plain
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -c
& $adb shell am broadcast -a com.example.marineclock.TEST_RING --ei bells 5 -p com.example.marineclock --include-stopped-packages
Start-Sleep -Seconds 8
& $adb logcat -d -s ShipsBell
```

Expected: 5 strikes heard (pair, pair, single) about 1 s after the broadcast, and logcat contains `Ringing 5 bells`. Repeat with `--ei bells 8`: 4 pairs, done within about 6 s. Raising or lowering the notification volume slider changes the loudness.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/example/marineclock/BellScheduler.kt app/src/main/java/com/example/marineclock/BellAlarmReceiver.kt app/src/debug app/src/main/AndroidManifest.xml
git commit -m @'
feat: schedule exact half-hour alarms and ring through gated receiver

Adds a debug-only TEST_RING broadcast that rings via a 1 s exact alarm.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 6: Launcher activity and reschedule receiver

**Files:**
- Create: `app/src/main/java/com/example/marineclock/LaunchActivity.kt`
- Create: `app/src/main/java/com/example/marineclock/RescheduleReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `BellScheduler.scheduleNext(context)`, `Channels.ensure(context)`

Notes for the implementer:
- `LaunchActivity` is a plain `android.app.Activity` (no AppCompat). It uses the translucent theme and has no layout. It must stay alive while the permission dialog shows, which is why it does not use `Theme.NoDisplay` or `noHistory`.
- It arms the schedule **before** asking for permission, so a denial still leaves the alarm chain running. Gate 1 keeps it silent until notifications are allowed.

- [ ] **Step 1: Implement LaunchActivity**

`app/src/main/java/com/example/marineclock/LaunchActivity.kt`:

```kotlin
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
```

- [ ] **Step 2: Implement RescheduleReceiver**

`app/src/main/java/com/example/marineclock/RescheduleReceiver.kt`:

```kotlin
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
```

(`Intent.ACTION_TIME_CHANGED` is the constant for the `android.intent.action.TIME_SET` broadcast.)

- [ ] **Step 3: Final manifest**

Replace `app/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.ShipsBell.Invisible">

        <activity
            android:name=".LaunchActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:theme="@style/Theme.ShipsBell.Invisible">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".BellService"
            android:exported="false"
            android:foregroundServiceType="shortService" />

        <receiver
            android:name=".BellAlarmReceiver"
            android:exported="false" />

        <receiver
            android:name=".RescheduleReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
        </receiver>

    </application>

</manifest>
```

- [ ] **Step 4: Build and run unit tests**

Run: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Device test: first launch arms the schedule**

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:installDebug --console=plain
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell pm revoke com.example.marineclock android.permission.POST_NOTIFICATIONS
& $adb shell am start -n com.example.marineclock/.LaunchActivity
```

Expected: only the system "Allow Ship's Bell to send you notifications?" dialog appears over the current screen. Tap **Allow**; the dialog closes and no app screen remains (nothing for the app in Recents). Then:

```powershell
& $adb shell dumpsys alarm | Select-String -Pattern "marineclock" -Context 0,3
```

Expected: an `RTC_WAKEUP` alarm for `com.example.marineclock` whose trigger time is the next :00 or :30.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/marineclock/LaunchActivity.kt app/src/main/java/com/example/marineclock/RescheduleReceiver.kt app/src/main/AndroidManifest.xml
git commit -m @'
feat: add invisible launcher and reschedule on boot, update and time changes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```

---

### Task 7: On-device verification

**Files:** none (verification only; fix any failures in the task that owns the code).

Set up: `$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"`. The debug build is installed and has been launched once (Task 6). Keep `& $adb logcat -s ShipsBell` running in a second terminal.

Define a helper in the same PowerShell session (it rings through the real gates, for the current time):

```powershell
function gated { & $adb shell am broadcast -a com.example.marineclock.TEST_RING --ez gated true -p com.example.marineclock }
```

- [ ] **Step 1: Gates pass.** Notifications allowed, DND off, run `gated`. Expected: bells ring with the count for the current half hour (e.g. 3 bells between 13:30 and 13:59) and `Ringing N bells` is logged.
- [ ] **Step 2: DND.** `& $adb shell cmd notification set_dnd on`, run `gated`. Expected: silence, `Bell skipped by gates` logged. Then `& $adb shell cmd notification set_dnd off` and run `gated` again. Expected: it rings.
- [ ] **Step 3: App notification toggle.** Settings → Apps → Ship's Bell → Notifications → off; run `gated`. Expected: silence and `Bell skipped by gates`. Turn it back on. Then repeat with only the "Ship's bell" category switched off. Expected: silence.
- [ ] **Step 4: Volume.** Set notification volume to max, then low, running `--ei bells 2` tests (non-gated) each time. Expected: loudness follows the slider; at zero, nothing is heard.
- [ ] **Step 5: Real half hour.** Leave the phone locked with the screen off across a real :00 or :30. Expected: the correct count rings within a second or two of the boundary, and `Next bell scheduled for …` shows the following boundary.
- [ ] **Step 6: Reboot.** `& $adb reboot`, wait for boot, then run `& $adb shell dumpsys alarm | Select-String -Pattern "marineclock" -Context 0,3`. Expected: an alarm is armed for the next boundary without relaunching the app.
- [ ] **Step 7: Time-zone change.** Settings → System → Date & time → set a zone with a :45 offset (e.g. Nepal, Asia/Kathmandu, +5:45) — India's :30 offset leaves half-hour instants unchanged, so it wouldn't show a real shift. Expected: `Next bell scheduled for …` is logged with the new zone's next local :00 or :30, and `dumpsys alarm` shows the trigger moved. Restore the automatic time zone afterwards.
- [ ] **Step 8: Standby buckets.** `& $adb shell am set-standby-bucket com.example.marineclock rare`, confirm the next half hour still rings; repeat with `restricted`; restore with `active`. Note: set ringer mode to Normal before Steps 1 and 4 (silent/vibrate mutes the notification stream), and the "N bells" notification typically won't appear (the short-service FGS notification is deferred) — that is not a failure.
- [ ] **Step 9: Record results.** Append a short "Verification" section to the spec listing each step as pass or fail with the device model and Android version, then commit:

```powershell
git add docs/superpowers/specs/2026-09-24-ships-bell-design.md
git commit -m @'
docs: record on-device verification results

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
'@
```
