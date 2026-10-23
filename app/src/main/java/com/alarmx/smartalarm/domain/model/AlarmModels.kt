package com.alarmx.smartalarm.domain.model

import java.time.DayOfWeek
import java.time.ZonedDateTime

enum class DismissMethod {
    TAP,
    TAP_CONFIRM,
    MATH,
    SHAKE
}

enum class VibrationPattern {
    NONE,
    SHORT_PULSE,
    HEARTBEAT,
    URGENT,
    CONTINUOUS
}

enum class TimeFormatMode {
    FOLLOW_SYSTEM,
    HOURS_12,
    HOURS_24
}

enum class WallpaperFit {
    CROP,
    FIT,
    CENTER
}

sealed class SoundRef(val storageKey: String) {
    data object SystemDefault : SoundRef("system:default")
    data class Bundled(val rawName: String) : SoundRef("bundled:$rawName")
    data class UserFile(val fileName: String) : SoundRef("user:$fileName")

    companion object {
        fun fromStorage(raw: String): SoundRef {
            return when {
                raw.isBlank() || raw == SystemDefault.storageKey -> SystemDefault
                raw.startsWith("bundled:") -> Bundled(raw.removePrefix("bundled:"))
                raw.startsWith("user:") -> UserFile(raw.removePrefix("user:"))
                else -> SystemDefault
            }
        }
    }
}

sealed class WallpaperRef(val storageKey: String) {
    data class Bundled(val id: String) : WallpaperRef("bundled:$id")
    data class UserFile(val fileName: String) : WallpaperRef("user:$fileName")
    data class VideoFile(val fileName: String) : WallpaperRef("video:$fileName")

    val isVideo: Boolean get() = this is VideoFile

    companion object {
        const val DEFAULT_ID = "midnight_navy"

        val Default: WallpaperRef = Bundled(DEFAULT_ID)

        fun fromStorage(raw: String): WallpaperRef {
            return when {
                raw.isBlank() -> Default
                raw.startsWith("bundled:") -> Bundled(raw.removePrefix("bundled:"))
                raw.startsWith("user:") -> UserFile(raw.removePrefix("user:"))
                raw.startsWith("video:") -> VideoFile(raw.removePrefix("video:"))
                else -> Default
            }
        }
    }
}

data class RepeatDays(val mask: Int) {
    fun contains(day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun toggle(day: DayOfWeek): RepeatDays {
        return copy(mask = mask xor bit(day))
    }

    fun toSet(): Set<DayOfWeek> = DayOfWeek.entries.filter { contains(it) }.toSet()

    val isOneTime: Boolean get() = mask == 0
    val isRepeating: Boolean get() = mask != 0

    fun summary(): String {
        if (isOneTime) return "One time"
        val days = toSet()
        if (days.size == 7) return "Every day"
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
        if (days == weekdays) return "Weekdays"
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        if (days == weekend) return "Weekends"
        return days.joinToString(" ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
    }

    companion object {
        val NONE = RepeatDays(0)
        val EVERY_DAY = RepeatDays(DayOfWeek.entries.fold(0) { acc, day -> acc or bit(day) })

        fun of(days: Iterable<DayOfWeek>): RepeatDays {
            return RepeatDays(days.fold(0) { acc, day -> acc or bit(day) })
        }

        fun bit(day: DayOfWeek): Int = 1 shl (day.value % 7)
    }
}

data class VolumeSettings(
    val volumePercent: Int = 90,
    val gradualEnabled: Boolean = true,
    val rampStartPercent: Int = 20,
    val rampTargetPercent: Int = 100,
    val rampDurationMs: Int = 20_000,
    val softwareAmpEnabled: Boolean = false,
    val softwareGainDb: Float = 0f,
    val videoVolumePercent: Int = 80,
    val ringtoneVolumePercent: Int = 90
) {
    fun sanitized(): VolumeSettings {
        return copy(
            volumePercent = volumePercent.coerceIn(0, 100),
            rampStartPercent = rampStartPercent.coerceIn(0, 100),
            rampTargetPercent = rampTargetPercent.coerceIn(0, 100),
            rampDurationMs = rampDurationMs.coerceIn(0, 180_000),
            softwareGainDb = softwareGainDb.coerceIn(0f, 12f),
            videoVolumePercent = videoVolumePercent.coerceIn(0, 100),
            ringtoneVolumePercent = ringtoneVolumePercent.coerceIn(0, 100)
        )
    }
}

data class Alarm(
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "Alarm",
    val repeatDays: RepeatDays = RepeatDays.NONE,
    val recurrence: RecurrenceSchedule = RecurrenceSchedule.oneTime(),
    val sound: SoundRef = SoundRef.Bundled("tone_alert_beacon"),
    val vibrate: Boolean = true,
    val vibrationPattern: VibrationPattern = VibrationPattern.HEARTBEAT,
    val volume: VolumeSettings = VolumeSettings(),
    val snoozeMinutes: Int = 5,
    val snoozeMaxCount: Int = 3,
    val dismissMethod: DismissMethod = DismissMethod.TAP,
    val keepRingingUntilDismissed: Boolean = true,
    val maxRingDurationMinutes: Int = 10,
    val flashlightEnabled: Boolean = false,
    val screenFlashEnabled: Boolean = false,
    val wallpaper: WallpaperRef = WallpaperRef.Default,
    val wallpaperFit: WallpaperFit = WallpaperFit.CROP,
    val overlayDarkness: Float = 0.45f,
    val videoAudioMode: VideoAudioMode = VideoAudioMode.SEPARATE_RINGTONE,
    val fallbackWallpaper: WallpaperRef = WallpaperRef.Default,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastScheduledEpochMs: Long? = null
) {
    fun sanitized(): Alarm {
        val schedule = when {
            recurrence.mode == RecurrenceMode.ONE_TIME && repeatDays.isRepeating ->
                RecurrenceSchedule.fromLegacyRepeatDays(repeatDays)
            recurrence.mode == RecurrenceMode.WEEKLY && recurrence.weeklyDays.isOneTime && repeatDays.isRepeating ->
                recurrence.copy(weeklyDays = repeatDays)
            else -> recurrence.sanitized()
        }.sanitized()
        val syncedMask = when (schedule.mode) {
            RecurrenceMode.WEEKLY -> schedule.weeklyDays
            RecurrenceMode.ONE_TIME -> RepeatDays.NONE
            RecurrenceMode.MONTHLY -> RepeatDays.NONE
        }
        return copy(
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            label = label.trim().ifBlank { "Alarm" }.take(48),
            volume = volume.sanitized(),
            snoozeMinutes = snoozeMinutes.coerceIn(1, 30),
            snoozeMaxCount = snoozeMaxCount.coerceIn(0, 10),
            maxRingDurationMinutes = maxRingDurationMinutes.coerceIn(1, 30),
            overlayDarkness = overlayDarkness.coerceIn(0f, 0.85f),
            recurrence = schedule,
            repeatDays = syncedMask
        )
    }

    fun isRepeating(): Boolean = recurrence.isRepeating() || repeatDays.isRepeating

    fun timeLabel(is24Hour: Boolean): String {
        val h = if (is24Hour) {
            "%02d".format(hour)
        } else {
            val display = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            display.toString()
        }
        val mm = "%02d".format(minute)
        return if (is24Hour) {
            "$h:$mm"
        } else {
            val suffix = if (hour < 12) "AM" else "PM"
            "$h:$mm $suffix"
        }
    }
}

data class NextAlarmOccurrence(
    val alarm: Alarm,
    val triggerAt: ZonedDateTime,
    val isSnooze: Boolean = false,
    val snoozeCount: Int = 0
)

data class SnoozeState(
    val alarmId: Long,
    val snoozeCount: Int,
    val triggerAtEpochMs: Long
)

data class AppSettings(
    val timeFormat: TimeFormatMode = TimeFormatMode.FOLLOW_SYSTEM,
    val useDarkTheme: Boolean = true,
    val followSystemTheme: Boolean = true,
    val defaultSnoozeMinutes: Int = 5,
    val hearingSafetyAcknowledged: Boolean = false,
    val updateManifestUrl: String = "",
    val githubOwner: String = "joilinsm",
    val githubRepo: String = "alarm-app",
    val lastNotifiedVersionCode: Int = 0,
    val lastUpdateCheckEpochMs: Long = 0L
)
