package com.alarmx.smartalarm.data

import com.alarmx.smartalarm.data.local.toDomain
import com.alarmx.smartalarm.data.local.toEntity
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.DismissMethod
import com.alarmx.smartalarm.domain.model.RepeatDays
import com.alarmx.smartalarm.domain.model.SoundRef
import com.alarmx.smartalarm.domain.model.VibrationPattern
import com.alarmx.smartalarm.domain.model.WallpaperFit
import com.alarmx.smartalarm.domain.model.WallpaperRef
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek

class AlarmMappingTest {
    @Test
    fun roomRoundTripPreservesFields() {
        val alarm = Alarm(
            id = 12,
            hour = 6,
            minute = 45,
            enabled = true,
            label = "Gym",
            repeatDays = RepeatDays.of(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            sound = SoundRef.Bundled("tone_dawn_pulse"),
            vibrate = true,
            vibrationPattern = VibrationPattern.URGENT,
            dismissMethod = DismissMethod.MATH,
            wallpaper = WallpaperRef.Bundled("sunrise_amber"),
            wallpaperFit = WallpaperFit.CENTER,
            lastScheduledEpochMs = 99L
        )
        val restored = alarm.toEntity().toDomain()
        assertEquals(alarm.id, restored.id)
        assertEquals(alarm.hour, restored.hour)
        assertEquals(alarm.minute, restored.minute)
        assertEquals(alarm.label, restored.label)
        assertEquals(alarm.repeatDays.mask, restored.repeatDays.mask)
        assertEquals(alarm.sound.storageKey, restored.sound.storageKey)
        assertEquals(alarm.vibrationPattern, restored.vibrationPattern)
        assertEquals(alarm.dismissMethod, restored.dismissMethod)
        assertEquals(alarm.wallpaper.storageKey, restored.wallpaper.storageKey)
        assertEquals(WallpaperFit.CENTER, restored.wallpaperFit)
        assertEquals(alarm.lastScheduledEpochMs, restored.lastScheduledEpochMs)
        assertEquals(alarm.videoAudioMode, restored.videoAudioMode)
    }
}
