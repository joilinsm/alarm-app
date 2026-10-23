package com.alarmx.smartalarm.domain.logic

import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.NextAlarmOccurrence
import com.alarmx.smartalarm.domain.model.RecurrenceMode
import com.alarmx.smartalarm.domain.model.RepeatDays
import com.alarmx.smartalarm.domain.model.SnoozeState
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object NextAlarmCalculator {
    fun nextTrigger(
        hour: Int,
        minute: Int,
        repeatDays: RepeatDays,
        from: ZonedDateTime
    ): ZonedDateTime? {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        if (repeatDays.isOneTime) {
            return firstFutureClockTime(safeHour, safeMinute, from)
        }
        val selected = repeatDays.toSet()
        if (selected.isEmpty()) return null
        for (offset in 0..7) {
            val day = from.plusDays(offset.toLong())
            val candidate = atTime(day, safeHour, safeMinute)
            if (candidate.dayOfWeek in selected && candidate.isAfter(from)) {
                return candidate
            }
        }
        return null
    }

    fun nextTrigger(alarm: Alarm, from: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        val schedule = alarm.recurrence
        return when (schedule.mode) {
            RecurrenceMode.ONE_TIME -> firstFutureClockTime(alarm.hour, alarm.minute, from)
            RecurrenceMode.WEEKLY -> nextTrigger(alarm.hour, alarm.minute, schedule.weeklyDays, from)
            RecurrenceMode.MONTHLY -> MonthlyRecurrenceCalculator.nextTrigger(
                schedule, alarm.hour, alarm.minute, from
            )
        }
    }

    fun upcoming(alarm: Alarm, from: ZonedDateTime, limit: Int = 20): List<ZonedDateTime> {
        if (!alarm.enabled && alarm.id > 0L) {
            // Preview while editing may still want dates even if temporarily disabled.
        }
        return MonthlyRecurrenceCalculator.upcoming(
            alarm.recurrence, alarm.hour, alarm.minute, from, limit = limit
        )
    }

    fun nextOccurrence(
        alarms: List<Alarm>,
        snoozes: List<SnoozeState>,
        from: ZonedDateTime
    ): NextAlarmOccurrence? {
        val zone = from.zone
        val snoozeHits = snoozes.mapNotNull { snooze ->
            val alarm = alarms.find { it.id == snooze.alarmId } ?: return@mapNotNull null
            val trigger = ZonedDateTime.ofInstant(Instant.ofEpochMilli(snooze.triggerAtEpochMs), zone)
            if (trigger.isAfter(from)) {
                NextAlarmOccurrence(alarm, trigger, isSnooze = true, snoozeCount = snooze.snoozeCount)
            } else {
                null
            }
        }
        val alarmHits = alarms.mapNotNull { alarm ->
            val trigger = nextTrigger(alarm, from) ?: return@mapNotNull null
            NextAlarmOccurrence(alarm, trigger)
        }
        return (snoozeHits + alarmHits).minByOrNull { it.triggerAt.toInstant() }
    }

    fun firstFutureClockTime(hour: Int, minute: Int, from: ZonedDateTime): ZonedDateTime {
        val today = atTime(from, hour, minute)
        return if (today.isAfter(from)) today else atTime(from.plusDays(1), hour, minute)
    }

    fun matchesWeekday(repeatDays: RepeatDays, day: DayOfWeek): Boolean {
        return repeatDays.isOneTime || repeatDays.contains(day)
    }

    fun ofInstant(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
    }

    private fun atTime(base: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        return try {
            base.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        } catch (_: Exception) {
            base.plusHours(1).withMinute(minute).withSecond(0).withNano(0)
        }
    }
}
