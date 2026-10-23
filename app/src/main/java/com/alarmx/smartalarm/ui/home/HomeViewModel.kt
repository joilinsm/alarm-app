package com.alarmx.smartalarm.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.domain.logic.NextAlarmCalculator
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.AppSettings
import com.alarmx.smartalarm.domain.model.NextAlarmOccurrence
import com.alarmx.smartalarm.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

data class HomeUiState(
    val now: ZonedDateTime = ZonedDateTime.now(),
    val alarms: List<Alarm> = emptyList(),
    val next: NextAlarmOccurrence? = null,
    val settings: AppSettings = AppSettings(),
    val exactAlarmsAllowed: Boolean = true,
    val is24Hour: Boolean = true
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AlarmXApp
    private val clock = flow {
        while (true) {
            emit(ZonedDateTime.now())
            delay(1000)
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        clock,
        app.container.alarmRepository.observeAlarms(),
        app.container.settingsRepository.settings,
        app.container.settingsRepository.snooze
    ) { now, alarms, settings, snooze ->
        HomeUiState(
            now = now,
            alarms = alarms,
            next = NextAlarmCalculator.nextOccurrence(alarms, listOfNotNull(snooze), now),
            settings = settings,
            exactAlarmsAllowed = app.container.alarmScheduler.canScheduleExact(),
            is24Hour = TimeFormat.is24Hour(app, settings.timeFormat)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            val updated = app.container.alarmRepository.setEnabled(alarm.id, enabled) ?: return@launch
            if (enabled) {
                val epoch = app.container.alarmScheduler.scheduleAlarm(updated)
                app.container.alarmRepository.setLastScheduled(updated.id, epoch)
            } else {
                app.container.alarmScheduler.cancel(updated.id)
                app.container.settingsRepository.clearSnoozeFor(updated.id)
            }
        }
    }

    fun delete(alarm: Alarm) {
        viewModelScope.launch {
            app.container.alarmScheduler.cancel(alarm.id)
            app.container.settingsRepository.clearSnoozeFor(alarm.id)
            app.container.alarmRepository.delete(alarm.id)
        }
    }

    fun duplicate(alarm: Alarm) {
        viewModelScope.launch {
            app.container.alarmRepository.duplicate(alarm.id)
        }
    }
}
