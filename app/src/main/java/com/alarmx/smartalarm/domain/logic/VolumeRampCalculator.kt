package com.alarmx.smartalarm.domain.logic

object VolumeRampCalculator {
    fun clampedPercent(value: Int): Int = value.coerceIn(0, 100)

    fun clampedGainDb(value: Float): Float = value.coerceIn(0f, 12f)

    fun linearGain(percent: Int): Float {
        return clampedPercent(percent) / 100f
    }

    /**
     * MediaPlayer / AudioTrack volume is 0f..1f. Values above 1 cannot be applied
     * on that path; extra loudness uses softwareGainDb instead.
     */
    fun playerGain(percent: Int): Float = linearGain(percent)

    fun rampedPlayerGain(start: Int, target: Int, elapsedMs: Long, durationMs: Int): Float {
        return playerGain(rampedPercent(start, target, elapsedMs, durationMs))
    }

    /**
     * Smoothstep interpolation from start to target over [0, 1].
     * Avoids a harsh jump at the beginning of a ramp.
     */
    fun rampedPercent(start: Int, target: Int, elapsedMs: Long, durationMs: Int): Int {
        val safeStart = clampedPercent(start)
        val safeTarget = clampedPercent(target)
        if (durationMs <= 0) return safeTarget
        val t = (elapsedMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
        val smooth = t * t * (3.0 - 2.0 * t)
        return (safeStart + (safeTarget - safeStart) * smooth).toInt().coerceIn(0, 100)
    }

    fun streamVolume(percent: Int, maxVolume: Int): Int {
        if (maxVolume <= 0) return 0
        return ((clampedPercent(percent) / 100f) * maxVolume).toInt().coerceIn(0, maxVolume)
    }

    fun softwareLinearGain(gainDb: Float): Float {
        val db = clampedGainDb(gainDb)
        return Math.pow(10.0, db / 20.0).toFloat()
    }

    /**
     * Soft-knee limiter to reduce hard clipping after software gain.
     */
    fun limitSample(sample: Float): Float {
        val x = sample.coerceIn(-4f, 4f)
        return when {
            x > 1f -> 1f - 1f / (x + 1f) * 0.5f
            x < -1f -> -1f + 1f / (-x + 1f) * 0.5f
            else -> x
        }.coerceIn(-1f, 1f)
    }
}
