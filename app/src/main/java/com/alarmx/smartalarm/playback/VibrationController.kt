package com.alarmx.smartalarm.playback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.alarmx.smartalarm.domain.model.VibrationPattern

class VibrationController(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun start(pattern: VibrationPattern) {
        val v = vibrator ?: return
        if (!v.hasVibrator() || pattern == VibrationPattern.NONE) return
        val timings = timingsFor(pattern)
        val amplitudes = amplitudesFor(pattern, timings.size)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, 0)
            } else {
                VibrationEffect.createWaveform(timings, 0)
            }
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, 0)
        }
    }

    fun stop() {
        vibrator?.cancel()
    }

    private fun timingsFor(pattern: VibrationPattern): LongArray {
        return when (pattern) {
            VibrationPattern.NONE -> longArrayOf(0, 0)
            VibrationPattern.SHORT_PULSE -> longArrayOf(0, 180, 220, 180, 700)
            VibrationPattern.HEARTBEAT -> longArrayOf(0, 120, 80, 220, 650)
            VibrationPattern.URGENT -> longArrayOf(0, 250, 90, 250, 90, 400, 300)
            VibrationPattern.CONTINUOUS -> longArrayOf(0, 800, 200)
        }
    }

    private fun amplitudesFor(pattern: VibrationPattern, size: Int): IntArray {
        val values = when (pattern) {
            VibrationPattern.NONE -> intArrayOf(0, 0)
            VibrationPattern.SHORT_PULSE -> intArrayOf(0, 180, 0, 180, 0)
            VibrationPattern.HEARTBEAT -> intArrayOf(0, 200, 0, 255, 0)
            VibrationPattern.URGENT -> intArrayOf(0, 255, 0, 220, 0, 255, 0)
            VibrationPattern.CONTINUOUS -> intArrayOf(0, 200, 0)
        }
        return if (values.size == size) values else IntArray(size) { if (it % 2 == 1) 200 else 0 }
    }
}
