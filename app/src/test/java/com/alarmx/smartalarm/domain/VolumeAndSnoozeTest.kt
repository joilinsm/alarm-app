package com.alarmx.smartalarm.domain

import com.alarmx.smartalarm.domain.logic.AlarmValidator
import com.alarmx.smartalarm.domain.logic.DeliveryGuard
import com.alarmx.smartalarm.domain.logic.SnoozeCalculator
import com.alarmx.smartalarm.domain.logic.VolumeRampCalculator
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.VolumeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeAndSnoozeTest {
    @Test
    fun clampsInvalidPercents() {
        assertEquals(0, VolumeRampCalculator.clampedPercent(-12))
        assertEquals(100, VolumeRampCalculator.clampedPercent(140))
        assertEquals(6f, VolumeRampCalculator.clampedGainDb(6f), 0.01f)
        assertEquals(12f, VolumeRampCalculator.clampedGainDb(99f), 0.01f)
    }

    @Test
    fun rampReachesTargetAtDuration() {
        val value = VolumeRampCalculator.rampedPercent(10, 90, 20_000, 20_000)
        assertEquals(90, value)
    }

    @Test
    fun rampStartsNearStartValue() {
        val value = VolumeRampCalculator.rampedPercent(10, 90, 0, 20_000)
        assertEquals(10, value)
    }

    @Test
    fun zeroDurationJumpsToTarget() {
        assertEquals(80, VolumeRampCalculator.rampedPercent(10, 80, 1, 0))
    }

    @Test
    fun streamVolumeMapsToDeviceMax() {
        assertEquals(7, VolumeRampCalculator.streamVolume(50, 15))
        assertEquals(0, VolumeRampCalculator.streamVolume(40, 0))
        assertEquals(15, VolumeRampCalculator.streamVolume(100, 15))
    }

    @Test
    fun limiterNeverExceedsFullScale() {
        assertTrue(VolumeRampCalculator.limitSample(8f) <= 1f)
        assertTrue(VolumeRampCalculator.limitSample(-8f) >= -1f)
        assertEquals(0.5f, VolumeRampCalculator.limitSample(0.5f), 0.0001f)
    }

    @Test
    fun snoozeHonorsMaxCount() {
        val alarm = Alarm(id = 2, hour = 6, minute = 0, snoozeMaxCount = 2, snoozeMinutes = 5)
        assertTrue(SnoozeCalculator.canSnooze(alarm, 0))
        assertTrue(SnoozeCalculator.canSnooze(alarm, 1))
        assertFalse(SnoozeCalculator.canSnooze(alarm, 2))
        val next = SnoozeCalculator.nextState(alarm, 0, 1_000_000L)
        assertNotNull(next)
        assertEquals(1, next!!.snoozeCount)
        assertEquals(1_000_000L + 5 * 60_000L, next.triggerAtEpochMs)
        assertNull(SnoozeCalculator.nextState(alarm, 2, 1_000_000L))
        assertEquals(0, SnoozeCalculator.remaining(alarm, 2))
    }

    @Test
    fun validatorRejectsOutOfRangeValues() {
        val bad = Alarm(
            hour = 25,
            minute = 80,
            label = " ",
            volume = VolumeSettings(volumePercent = 200, softwareGainDb = 40f)
        )
        val errors = AlarmValidator.validate(bad)
        assertTrue(errors.isNotEmpty())
        assertTrue(AlarmValidator.isValid(Alarm(hour = 7, minute = 15)))
    }

    @Test
    fun sanitizedAlarmRepairsInvalidFields() {
        val clean = Alarm(hour = 99, minute = -3, label = "   ").sanitized()
        assertEquals(23, clean.hour)
        assertEquals(0, clean.minute)
        assertEquals("Alarm", clean.label)
    }

    @Test
    fun deliveryGuardBlocksDuplicateInsideWindow() {
        val guard = DeliveryGuard(windowMs = 10_000)
        val key = guard.eventKey(9L, 1234L)
        assertTrue(guard.shouldHandle(key, 50_000))
        assertFalse(guard.shouldHandle(key, 55_000))
        assertTrue(guard.shouldHandle(key, 70_001))
    }

    @Test
    fun missingCustomAudioFallsBackInSoundRefParsing() {
        val parsed = com.alarmx.smartalarm.domain.model.SoundRef.fromStorage("broken-value")
        assertTrue(parsed is com.alarmx.smartalarm.domain.model.SoundRef.SystemDefault)
    }
}

class RepeatDaysAndModelTest {
    @Test
    fun twelveHourLabels() {
        val morning = Alarm(hour = 0, minute = 5)
        assertEquals("12:05 AM", morning.timeLabel(false))
        val afternoon = Alarm(hour = 15, minute = 4)
        assertEquals("3:04 PM", afternoon.timeLabel(false))
        assertEquals("15:04", afternoon.timeLabel(true))
    }

    @Test
    fun wallpaperStorageRoundTrip() {
        val bundled = com.alarmx.smartalarm.domain.model.WallpaperRef.fromStorage("bundled:sunrise_amber")
        assertTrue(bundled is com.alarmx.smartalarm.domain.model.WallpaperRef.Bundled)
        val user = com.alarmx.smartalarm.domain.model.WallpaperRef.fromStorage("user:pic.jpg")
        assertTrue(user is com.alarmx.smartalarm.domain.model.WallpaperRef.UserFile)
    }
}
