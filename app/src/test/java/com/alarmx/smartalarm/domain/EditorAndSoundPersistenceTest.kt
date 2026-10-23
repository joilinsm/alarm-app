package com.alarmx.smartalarm.domain

import com.alarmx.smartalarm.audio.SoundLabels
import com.alarmx.smartalarm.data.local.toDomain
import com.alarmx.smartalarm.data.local.toEntity
import com.alarmx.smartalarm.domain.logic.EditorLoadPolicy
import com.alarmx.smartalarm.domain.logic.SoundPlaybackMessages
import com.alarmx.smartalarm.domain.logic.VolumeRampCalculator
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.SoundRef
import com.alarmx.smartalarm.domain.model.VolumeSettings
import com.alarmx.smartalarm.domain.model.WallpaperFit
import com.alarmx.smartalarm.domain.model.WallpaperRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorAndSoundPersistenceTest {
    @Test
    fun returningToEditorDoesNotReloadSameDraft() {
        assertFalse(EditorLoadPolicy.shouldReload(true, 4L, 4L, force = false))
        assertTrue(EditorLoadPolicy.shouldReload(true, 4L, 4L, force = true))
        assertTrue(EditorLoadPolicy.shouldReload(false, 4L, 4L, force = false))
        assertTrue(EditorLoadPolicy.shouldReload(true, 4L, 9L, force = false))
        assertTrue(EditorLoadPolicy.shouldReload(true, null, 0L, force = false))
    }

    @Test
    fun twoAlarmsKeepIndependentSoundsAfterRoundTrip() {
        val alarmA = Alarm(id = 1, hour = 6, minute = 0, sound = SoundRef.Bundled("tone_dawn_pulse"))
        val alarmB = Alarm(id = 2, hour = 7, minute = 0, sound = SoundRef.Bundled("tone_crystal_chime"))
        val restoredA = alarmA.toEntity().toDomain()
        val restoredB = alarmB.toEntity().toDomain()
        assertEquals("bundled:tone_dawn_pulse", restoredA.sound.storageKey)
        assertEquals("bundled:tone_crystal_chime", restoredB.sound.storageKey)
        assertTrue(restoredA.sound is SoundRef.Bundled)
        assertTrue(restoredB.sound is SoundRef.Bundled)
    }

    @Test
    fun importedSoundKeyIsNotRewrittenToSystemDefault() {
        val alarm = Alarm(id = 3, hour = 8, minute = 0, sound = SoundRef.UserFile("snd_abc.mp3"))
        val restored = alarm.toEntity().toDomain()
        assertEquals("user:snd_abc.mp3", restored.sound.storageKey)
        assertTrue(restored.sound is SoundRef.UserFile)
        assertFalse(restored.sound is SoundRef.SystemDefault)
    }

    @Test
    fun wallpaperFitAndOverlaySurviveRoundTrip() {
        val alarm = Alarm(
            id = 5,
            hour = 9,
            minute = 15,
            wallpaper = WallpaperRef.UserFile("wp_demo.jpg"),
            wallpaperFit = WallpaperFit.FIT,
            overlayDarkness = 0.3f
        )
        val restored = alarm.toEntity().toDomain()
        assertEquals(WallpaperFit.FIT, restored.wallpaperFit)
        assertEquals(0.3f, restored.overlayDarkness, 0.001f)
        assertEquals("user:wp_demo.jpg", restored.wallpaper.storageKey)
    }

    @Test
    fun volumeSettingsSurviveRoundTrip() {
        val alarm = Alarm(
            id = 6,
            hour = 5,
            minute = 0,
            volume = VolumeSettings(
                volumePercent = 35,
                gradualEnabled = true,
                rampStartPercent = 10,
                rampTargetPercent = 80,
                rampDurationMs = 15_000,
                softwareAmpEnabled = true,
                softwareGainDb = 4.5f
            )
        )
        val restored = alarm.toEntity().toDomain()
        assertEquals(35, restored.volume.volumePercent)
        assertEquals(10, restored.volume.rampStartPercent)
        assertEquals(80, restored.volume.rampTargetPercent)
        assertEquals(15_000, restored.volume.rampDurationMs)
        assertTrue(restored.volume.softwareAmpEnabled)
        assertEquals(4.5f, restored.volume.softwareGainDb, 0.01f)
    }

    @Test
    fun playerGainNeverExceedsUnity() {
        assertEquals(0.0f, VolumeRampCalculator.playerGain(0), 0.001f)
        assertEquals(0.35f, VolumeRampCalculator.playerGain(35), 0.001f)
        assertEquals(1.0f, VolumeRampCalculator.playerGain(100), 0.001f)
        assertEquals(1.0f, VolumeRampCalculator.playerGain(140), 0.001f)
        val mid = VolumeRampCalculator.rampedPlayerGain(10, 90, 10_000, 20_000)
        assertTrue(mid > 0.1f && mid < 0.9f)
    }

    @Test
    fun soundLabelsUseBundledTitles() {
        assertEquals("Dawn Pulse", SoundLabels.title(SoundRef.Bundled("tone_dawn_pulse")))
        assertEquals("System default alarm", SoundLabels.title(SoundRef.SystemDefault))
        assertEquals("wake.mp3", SoundLabels.title(SoundRef.UserFile("wake.mp3")))
    }

    @Test
    fun cancelPreviewKeepsPreviousWallpaperKey() {
        val previous = WallpaperRef.Bundled("night_sky")
        val pending = WallpaperRef.UserFile("wp_new.jpg")
        assertTrue(previous.storageKey != pending.storageKey)
    }

    @Test
    fun missingCustomSoundMessageDoesNotSuggestASilentFallback() {
        val message = SoundPlaybackMessages.missingSound(SoundRef.UserFile("gone.mp3"))
        assertTrue(message.contains("gone.mp3"))
        assertFalse(message.contains("system", ignoreCase = true))
        assertFalse(message.contains("default", ignoreCase = true))
    }
}
