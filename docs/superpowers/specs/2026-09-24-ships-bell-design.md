# Ship's Bell — Design

**Date:** 2026-09-24
**Status:** Approved (pending written-spec review)

## Purpose

An Android app that rings a ship's bell on every half hour, using the traditional
four-hour watch cycle. It has no app screen and runs entirely in the background.
Loudness follows the system notification volume. The app's standard Android
notification toggle turns it on and off.

## Requirements

1. Rings at every :00 and :30 local time.
2. Bell count follows the 4-hour cycle: 00:30 → 1, 01:00 → 2, … 04:00 → 8, 04:30 → 1,
   and so on around the clock (00:00, 04:00, 08:00, 12:00, 16:00, 20:00 → 8).
3. Strikes are grouped in traditional pairs. An odd count ends on a single strike.
4. The full 8-bell chime lasts no more than 6.0 s.
5. Volume is controlled by the system notification volume.
6. On/off is the app's standard Notifications setting in the system App info page
   (app-level toggle, or the "Ship's bell" channel).
7. No bell rings while Do Not Disturb is active for any reason: manual, scheduled
   (Android's "quiet hours"), or Bedtime mode. The count stays tied to the clock,
   so the first bell after DND ends is correct.
8. No app screen. A launcher icon exists only to grant notification permission and
   start the schedule; it shows nothing but the system permission dialog.
9. Survives reboot, app update, manual clock changes, time-zone changes, and DST.

## Non-goals

- No in-app settings, quiet-hours window, clock face, or history.
- No alternative sounds, and no volume control separate from notification volume.
- No home-screen widget.

## Platform

- Kotlin, Views/AppCompat template (no Compose), minSdk 35, targetSdk/compileSdk 37.
- Package / applicationId renamed from `com.example.myapplication` to
  `com.example.marineclock`. App label: "Ship's Bell".

## Architecture

The app schedules a single exact alarm for the next half-hour boundary. When it
fires, a receiver schedules the next alarm, checks the gates, and (if they all
pass) starts a short-lived foreground service that plays the strikes and then
stops itself. Nothing runs between bells.

```
LaunchActivity ──(once)──► BellScheduler.scheduleNext()
                                   │
                          AlarmManager exact alarm
                                   ▼
                          BellAlarmReceiver
                           1. scheduleNext()
                           2. gates pass? ──no──► done (silent)
                                   │ yes
                                   ▼
                          BellService (shortService FGS)
                           play strike pattern via SoundPool → stopSelf()

RescheduleReceiver (boot / update / time / timezone) ──► BellScheduler.scheduleNext()
```

### Components

| Unit | Responsibility | Depends on |
|---|---|---|
| `BellMath` | Pure Kotlin. `bellsAt(LocalTime): Int`, `nextBoundary(ZonedDateTime): ZonedDateTime`, `strikeOffsetsMs(bells: Int): List<Long>`. | `java.time` only |
| `BellScheduler` | Schedules one exact alarm (`setExactAndAllowWhileIdle`, `RTC_WAKEUP`) for `nextBoundary(now)`. The target epoch millis travel as an intent extra. Idempotent: it reuses one `PendingIntent` (`FLAG_UPDATE_CURRENT`), so calling it again replaces the pending alarm. | `AlarmManager`, `BellMath` |
| `BellAlarmReceiver` | Handles the alarm: reschedules first, then evaluates the gates, then starts `BellService` with the bell count. | `BellScheduler`, `BellGates`, `BellMath` |
| `BellGates` | Returns whether a bell should ring now (see Gates). | `NotificationManager` |
| `BellService` | Foreground service, type `shortService`. Posts the ongoing notification, requests transient audio focus, plays the strikes, releases everything, and calls `stopSelf()`. Implements `onTimeout` to stop cleanly. | `SoundPool`, `AudioManager`, `BellMath` |
| `RescheduleReceiver` | On `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`: calls `scheduleNext()`. | `BellScheduler` |
| `LaunchActivity` | Launcher entry with a no-display/translucent theme and no layout. It creates the notification channel, requests `POST_NOTIFICATIONS` if not granted, calls `scheduleNext()` whatever the result, then calls `finish()`. | `BellScheduler` |
| `Channels` | Creates the "Ship's bell" notification channel (idempotent, called from the activity, the receivers, and the service). | `NotificationManager` |

### Bell count

With `h` = hour (0–23) and `m` = minute (0 or 30):

```
halfHours = h * 2 + (m / 30)
bells     = ((halfHours - 1) mod 8) + 1      // floor-mod; 00:00 → 8
```

### Strike timing

- Gap within a pair: **400 ms**.
- Pair period (start of one pair to start of the next): **1200 ms**.
- Strike `i` (0-based) plays at `(i / 2) * 1200 + (i % 2) * 400` ms.
- 8 bells: 0, 400, 1200, 1600, 2400, 2800, 3600, 4000 ms. The last strike rings
  out for 2.0 s, so the chime ends at **6.0 s**.
- Both gaps are named constants in `BellMath`.

### Sound asset

- `app/src/main/res/raw/ships_bell.wav`. It comes from
  `32304__acclivity__shipsbell.wav`, processed with
  `sox <src> ships_bell.wav trim 0 2.0 fade q 0 2.0 1.5`.
- The result is one strike: 2.0 s, 16-bit stereo, 44.1 kHz, full attack, with a
  quarter-sine fade from 0.5 s to 2.0 s.
- A mixed 8-bell reference is at `docs/audio/preview_8_bells.wav` (6.0 s, peak 0.90,
  no clipping). It is not bundled in the APK.

### Playback

- `SoundPool` with `AudioAttributes`: `USAGE_NOTIFICATION`,
  `CONTENT_TYPE_SONIFICATION`, max streams 8, so overlapping strikes all sound.
- The service loads the sample and waits for `setOnLoadCompleteListener`. It then
  posts each strike with a `Handler` relative to one start time (no accumulated
  drift), and stops after the last offset plus 2.0 s.
- The service holds a `PARTIAL_WAKE_LOCK` (10 s timeout) from start to stop, so
  the strike `Handler` keeps accurate time with the screen off.
- Audio focus: `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`. The service plays even if
  focus is denied, because the gates already decided the bell should ring.
  Focus is abandoned on stop.
- Volume comes from the notification stream. On devices that link ring and
  notification volume, the ring slider also affects it. That is device
  behaviour, not app behaviour.

### Gates (evaluated in `BellAlarmReceiver`)

A bell is **skipped** if any of these is true:

1. `NotificationManager.areNotificationsEnabled()` is false, or the "Ship's bell"
   channel importance is `IMPORTANCE_NONE`.
2. `NotificationManager.currentInterruptionFilter != INTERRUPTION_FILTER_ALL`
   (DND active: manual, schedule, or Bedtime).
3. The alarm is stale: `now - scheduledTime > 2 min` (phone was off, or delivery
   was deferred). This avoids ringing the wrong count.

Notification volume at zero is not a gate. The bell simply plays silently.

### Notification

- Channel id `ships_bell`, name "Ship's bell", `IMPORTANCE_LOW`, no sound, no
  vibration, no badge.
- Ongoing notification while ringing: title "Ship's bell", text e.g. "3 bells".
  It is removed when the service stops (about 2–6 s).
- Small icon: a simple bell vector drawable.

### Manifest

- Permissions: `POST_NOTIFICATIONS`, `USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`,
  `FOREGROUND_SERVICE`, `WAKE_LOCK`.
- `BellService`: `android:foregroundServiceType="shortService"`, not exported.
- `BellAlarmReceiver`: not exported (explicit `PendingIntent` only).
- `RescheduleReceiver`: exported, with intent filters for the four system actions.
- `LaunchActivity`: `MAIN`/`LAUNCHER`, translucent theme, `excludeFromRecents`.
  No `noHistory`: the activity must survive while the permission dialog is shown.
- Debug-only (`src/debug/AndroidManifest.xml`): an exported `TestRingReceiver` for
  action `com.example.marineclock.TEST_RING` with int extra `bells` (default 8).
  Background receivers can't start foreground services, so it sets a one-shot
  exact alarm 1 s out (separate request code) that rings `bells` and bypasses the
  gates. With `--ez gated true` it instead goes through the real gates and rings
  the count for the current time.

## Error handling

- **Reschedule before anything else** in `BellAlarmReceiver`, so a crash in the
  gates or the service never breaks the chain.
- **Foreground-service start refused** (should not happen, because exact alarms
  are exempt from FGS start restrictions): catch the exception, log it, and skip
  this bell. The next alarm is already set.
- **Sample fails to load**: log it and stop the service; skip the bell.
- **`onTimeout` (short-service limit)**: release `SoundPool`, abandon focus, stop.
  A chime lasts at most about 6 s, so this is a safety net only.
- **Permission denied at launch**: the schedule is still armed, and gate 1 keeps
  it silent until the user enables notifications in Settings. It then works with
  no relaunch.

## Testing

**Unit (JUnit, `BellMath`):**
- All 48 half-hours of a day map to the correct count. Explicit checks for
  00:00 → 8, 00:30 → 1, 04:00 → 8, 04:30 → 1, 12:00 → 8.
- `nextBoundary`: from :00:00 exactly (moves on to :30), from :29:59, from :59:59,
  across midnight, and across a DST spring-forward and fall-back in a real zone
  (e.g. `Europe/London`).
- `strikeOffsetsMs`: correct lists for 1–8 bells. The 8-bell last offset is 4000.

**Manual (device):**
- `adb shell am broadcast -a com.example.marineclock.TEST_RING --ei bells 5 -p com.example.marineclock --include-stopped-packages`
  rings 5 bells immediately (debug build).
- Notification volume slider changes loudness.
- App notifications toggled off → no bell at the next half hour; on → rings.
- DND on (manual and scheduled) → silent; off → the next bell is correct.
- Reboot → rings at the next half hour with no relaunch.
- Change the time zone → the next bell follows the new local time.
