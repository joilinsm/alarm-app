package com.alarmx.smartalarm.domain.logic

import com.alarmx.smartalarm.domain.model.Alarm

object AlarmValidator {
    fun validate(alarm: Alarm): List<String> {
        val errors = mutableListOf<String>()
        if (alarm.hour !in 0..23) errors += "Hour must be between 0 and 23."
        if (alarm.minute !in 0..59) errors += "Minute must be between 0 and 59."
        if (alarm.label.isBlank()) errors += "Label cannot be empty."
        if (alarm.volume.volumePercent !in 0..100) errors += "Volume must be between 0 and 100."
        if (alarm.volume.rampStartPercent !in 0..100) errors += "Ramp start must be between 0 and 100."
        if (alarm.volume.rampTargetPercent !in 0..100) errors += "Ramp target must be between 0 and 100."
        if (alarm.volume.softwareGainDb < 0f || alarm.volume.softwareGainDb > 12f) {
            errors += "Software gain must be between 0 and 12 dB."
        }
        if (alarm.snoozeMinutes !in 1..30) errors += "Snooze duration must be 1–30 minutes."
        if (alarm.snoozeMaxCount !in 0..10) errors += "Snooze count must be 0–10."
        if (alarm.maxRingDurationMinutes !in 1..30) errors += "Max ring duration must be 1–30 minutes."
        if (alarm.overlayDarkness !in 0f..0.85f) errors += "Overlay darkness is out of range."
        return errors
    }

    fun isValid(alarm: Alarm): Boolean = validate(alarm).isEmpty()
}

object MathChallenge {
    data class Problem(val prompt: String, val answer: Int)

    fun generate(seed: Long = System.currentTimeMillis()): Problem {
        val rng = kotlin.random.Random(seed)
        return when (rng.nextInt(3)) {
            0 -> {
                val a = rng.nextInt(6, 20)
                val b = rng.nextInt(6, 20)
                Problem("$a + $b", a + b)
            }
            1 -> {
                val a = rng.nextInt(12, 40)
                val b = rng.nextInt(4, 12)
                Problem("$a − $b", a - b)
            }
            else -> {
                val a = rng.nextInt(3, 10)
                val b = rng.nextInt(3, 9)
                Problem("$a × $b", a * b)
            }
        }
    }
}
