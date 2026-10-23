# AlarmX — Smart Alarm Booster

Native Android alarm clock with reliable scheduling, a full-screen wake-up experience, gradual volume, optional software amplification, custom sounds, and alarm-screen wallpapers. The app works fully offline after installation.

**Package ID:** `com.alarmx.smartalarm`  
**Display name:** AlarmX  
**Minimum SDK:** 26 (Android 8.0)  
**Target / compile SDK:** 36

This repository is a complete Android project. The primary deliverable is a locally built debug APK on Windows.

## Features

- Unlimited practical alarms stored in Room
- One-time and weekday-repeat schedules
- Enable, disable, edit, duplicate, and delete
- Custom labels, sounds, vibration patterns, and per-alarm wallpapers
- Alarm-stream volume, gradual ramp, and optional software gain with a limiter
- Snooze with duration and max count
- Dismiss by tap, confirmation, math, or shake, plus an emergency stop
- Full-screen ringing activity, notification actions, and lock-screen flags
- Reboot, timezone, date, and time-change rescheduling
- Reliability checklist for notifications, exact alarms, and battery restrictions
- Optional media-volume / equalizer tools that do not touch alarm volume
- No ads, analytics, accounts, or network requirement

## Technology stack

- Kotlin
- Jetpack Compose and Material 3
- Gradle Kotlin DSL and version catalog
- Android Gradle Plugin 8.13.2
- Kotlin 2.1.10
- Room 2.6.1
- Coroutines / Flow
- AlarmManager, exact alarms, and a short-lived foreground playback service

## Architecture

| Layer | Responsibility |
| --- | --- |
| UI (`ui/`) | Compose screens and navigation |
| ViewModels | Home and editor state |
| Domain (`domain/`) | Alarm model, next-trigger math, ramp, snooze, duplicate-delivery guard |
| Data (`data/`) | Room, settings SharedPreferences, imported file copies |
| Scheduling (`schedule/`) | AlarmManager, receivers, reboot recovery |
| Playback (`playback/`) | Audio, vibration, flashlight, notifications, software amp |

Manual dependency wiring lives in `AppContainer`. There is no backend.

## Prerequisites (Windows)

1. Windows 10/11
2. Android Studio **or** a standalone Android SDK
3. JDK 17 or 21 (the scripts prefer Android Studio’s bundled JBR 21)
4. Internet access on the first Gradle build so dependencies can download

This machine was verified with:

- Android Studio 2025.2.2
- SDK platforms 33, 34, and 36
- Build-tools 36.1.0
- Android Studio JBR 21.0.8

Do not point Gradle at JDK 24 if your Android Gradle Plugin rejects it. The project pins:

```
org.gradle.java.home=C:\\Program Files\\Android\\Android Studio\\jbr
```

in `gradle.properties`. Change that path if Android Studio is installed elsewhere.

## Environment verification

```bat
scripts\check-environment.bat
```

The script fails with a clear message if Java, the SDK, or the Gradle wrapper is missing.

## Android SDK setup

1. Install Android Studio.
2. Open SDK Manager and install:
   - Android SDK Platform 36
   - Android SDK Build-Tools 36.x
   - Android SDK Platform-Tools
3. Confirm `%LOCALAPPDATA%\Android\Sdk` exists, or set `ANDROID_HOME`.
4. `local.properties` already points at the default SDK location. Adjust `sdk.dir` if yours is different. That file is gitignored.

## Build commands

From the project root:

```bat
scripts\build-debug-apk.bat
```

Equivalent Gradle command:

```bat
gradlew.bat assembleDebug
```

### APK output

```
app\build\outputs\apk\debug\app-debug.apk
```

The build script refuses to report success unless that file exists.

Optional unit tests:

```bat
scripts\run-tests.bat
```

Equivalent:

```bat
gradlew.bat testDebugUnitTest
```

## Install on an Android phone

### Manual transfer (no USB debugging required)

1. Copy `app\build\outputs\apk\debug\app-debug.apk` to the phone (USB file transfer, Drive, or similar).
2. Open the APK on the phone.
3. Allow installation from that source if Android asks.
4. Install and launch **AlarmX**.
5. Allow notifications when prompted.
6. Open **Reliability guide** and grant exact-alarm access if Android requests it.
7. Create a test alarm one or two minutes in the future.
8. Review battery optimization / unrestricted background use.
9. Confirm the alarm rings, snoozes, and dismisses.
10. Confirm the chosen wallpaper and sound still apply after force-stopping the app.

### ADB (optional)

USB debugging must be enabled by you. AlarmX does not assume it is on.

1. On the phone: Settings → About → tap Build number seven times.
2. Enable **USB debugging**.
3. Connect the phone and authorize the PC.

```bat
scripts\install-debug-apk.bat
```

Or:

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

`adb` is usually at:

```
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
```

## Permissions

| Permission | Why |
| --- | --- |
| `RECEIVE_BOOT_COMPLETED` | Reschedule after reboot |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Fire at the chosen time |
| `POST_NOTIFICATIONS` | Full-screen alarm notification |
| `USE_FULL_SCREEN_INTENT` | Show the ringing screen over the lock screen |
| `VIBRATE` | Vibration patterns |
| `WAKE_LOCK` | Keep audio running while ringing |
| `FOREGROUND_SERVICE` + `MEDIA_PLAYBACK` | Playback service **only while an alarm is ringing** |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Opens the system battery dialog from the reliability screen |
| `MODIFY_AUDIO_SETTINGS` | Optional media tools only |

Flashlight assist uses `CameraManager.setTorchMode` and does not request camera permission. Accessibility services and device-admin rights are not used.

## Exact alarms and battery restrictions

If exact alarms are denied, AlarmX uses `setAndAllowWhileIdle` as the best supported fallback and **tells you** that the alarm may run late. It does not hide the limitation behind a fake timer.

Some manufacturers still kill background work after these settings. The in-app reliability guide cannot override OEM autostart / sleeping-apps menus. Delivery is not guaranteed on every device, Doze policy, or user restriction.

## Known limitations

- Software gain is digital amplification with a limiter. It cannot exceed the speaker’s physical maximum and can distort.
- Full-screen intents can be limited by Android 14+ notification full-screen rules. The high-priority notification remains the fallback.
- Shake-to-dismiss needs an accelerometer and only runs on the ringing screen.
- Flashlight blinking needs a camera flash and is skipped when unavailable.
- Equalizer tools work only when the device exposes `android.media.audiofx.Equalizer`.
- DST gaps are skipped forward to a valid local time.
- Debug and release APKs are signed differently. A debug install must be uninstalled before a differently signed release can replace it.

## Video wallpapers

Import MP4 (and other device-decodable) videos from the wallpaper screen. Preview loops the video with clock/snooze/dismiss chrome and three audio modes:

- **A Separate ringtone** (default): video muted, selected alarm sound plays
- **B Video audio**: video audio only
- **C Video + ringtone**: both, with independent gains

Videos are copied into app-private storage. If video playback fails while ringing, the saved static fallback wallpaper is shown without changing the ringtone selection.

## Monthly recurrence

Edit Alarm → **Edit recurrence** supports one-time, weekly, and monthly rules (exact weekday occurrences, inclusive ranges, every weekday, specific dates with skip/last-day policy, last calendar day, intervals 1/2/3/6/12 with anchors). Room migration `2→3` preserves existing alarms.

## Private updates

Settings → Updates:

1. Preferred: HTTPS `version.json` manifest URL from your own authenticated proxy
2. Optional: fine-grained GitHub token stored encrypted on-device only (never baked into the APK)
3. Manual **Check for updates**, download with SHA-256 validation, then Android’s package installer

Release tooling: `scripts\prepare-release.bat` then `scripts\publish-private-release.bat v1.1.0`.

## Automated tests

JVM tests cover:

- Creating / mapping alarm data
- Enable semantics via next-trigger of disabled alarms
- Weekday math, midnight rollover, snooze, duplicate-delivery protection
- Invalid volume / gain clamping
- Ramp calculations
- Missing-sound key fallback
- Editor reload policy so sound, wallpaper, and volume drafts are not overwritten
- Independent alarm-to-sound mapping after Room round-trip
- Wallpaper fit/overlay persistence and player-gain clamping (0.0–1.0)
- Monthly recurrence calendar math and update metadata validation

```bat
gradlew.bat testDebugUnitTest
```

Instrumented UI launch test (needs an emulator or phone):

```bat
gradlew.bat connectedDebugAndroidTest
```

## Release signing

Debug builds do **not** need a keystore.

1. Generate a local keystore (keep it outside git):

```bat
keytool -genkeypair -v -keystore %USERPROFILE%\alarmx-release.jks -alias alarmx -keyalg RSA -keysize 2048 -validity 10000
```

2. Copy `keystore.properties.example` to `keystore.properties` and fill in the paths and passwords. That file is gitignored.
3. Build:

```bat
gradlew.bat assembleRelease
```

Expected output when signing is configured:

```
app\build\outputs\apk\release\app-release.apk
```

If `keystore.properties` is missing, the release build may still compile but it will **not** be a store-ready signed APK. Never commit `.jks`, `.keystore`, or passwords. Build logs must not print signing passwords.

## Troubleshooting

| Problem | What to do |
| --- | --- |
| Plugin or dependency not found | Connect to the internet and rerun `gradlew.bat assembleDebug` |
| `JAVA_HOME` points at JDK 24 and AGP fails | Set `org.gradle.java.home` to Android Studio’s `jbr` folder |
| SDK not found | Set `sdk.dir` in `local.properties` |
| Exact alarm denied | Reliability guide → Open exact alarm setting |
| Alarm silent | Check notification permission, Do Not Disturb, and alarm stream volume |
| Alarm late or missing after reboot | Battery unrestricted + OEM autostart; exact alarms on |
| Custom sound missing | AlarmX falls back to the bundled Alert Beacon tone |
| `adb install` unauthorized | Unlock the phone and allow USB debugging |

## Feature checklist

| Item | Status |
| --- | --- |
| Alarm CRUD, repeats, labels, persistence | Implemented |
| AlarmManager scheduling, reboot, time changes | Implemented |
| Full-screen ringing, snooze, dismiss, resource cleanup | Implemented |
| Gradual volume and software amp | Implemented |
| Bundled + imported sounds and wallpapers | Implemented |
| Math / shake / confirm dismiss + emergency stop | Implemented |
| Flashlight / screen flash | Implemented where hardware allows |
| Media sound tools | Implemented, equalizer hardware-dependent |
| Reliability / about screens | Implemented |
| JVM unit tests | Implemented; 22 tests executed, 0 failures |
| Instrumented tests | Source present; run only with a device |
| Debug APK | Built locally when the environment allows |
| Signed release APK | Documented; requires a local keystore |

Bundled tones are original short synthesizer loops generated for this project. Bundled wallpapers are original color gradients.
