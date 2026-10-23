package com.alarmx.smartalarm.playback

import com.alarmx.smartalarm.domain.model.Alarm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveAlarm(
    val alarm: Alarm,
    val scheduledAt: Long,
    val isSnooze: Boolean,
    val snoozeCount: Int,
    val playbackError: String? = null
)

class ActiveAlarmStore {
    private val _active = MutableStateFlow<ActiveAlarm?>(null)
    val active: StateFlow<ActiveAlarm?> = _active.asStateFlow()

    fun setActive(
        alarm: Alarm,
        scheduledAt: Long,
        isSnooze: Boolean,
        snoozeCount: Int,
        playbackError: String? = null
    ) {
        _active.value = ActiveAlarm(alarm, scheduledAt, isSnooze, snoozeCount, playbackError)
    }

    fun clear() {
        _active.value = null
    }
}
