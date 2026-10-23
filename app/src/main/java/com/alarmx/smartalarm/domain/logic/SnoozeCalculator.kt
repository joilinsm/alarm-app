package com.alarmx.smartalarm.domain.logic

import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.SnoozeState

object SnoozeCalculator {
    fun canSnooze(alarm: Alarm, currentCount: Int): Boolean {
        if (alarm.snoozeMaxCount <= 0) return false
        return currentCount < alarm.snoozeMaxCount
    }

    fun remaining(alarm: Alarm, currentCount: Int): Int {
        return (alarm.snoozeMaxCount - currentCount).coerceAtLeast(0)
    }

    fun nextState(alarm: Alarm, currentCount: Int, nowEpochMs: Long): SnoozeState? {
        if (!canSnooze(alarm, currentCount)) return null
        val minutes = alarm.snoozeMinutes.coerceIn(1, 30)
        return SnoozeState(
            alarmId = alarm.id,
            snoozeCount = currentCount + 1,
            triggerAtEpochMs = nowEpochMs + minutes * 60_000L
        )
    }
}
