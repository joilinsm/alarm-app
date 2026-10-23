package com.alarmx.smartalarm.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.alarmx.smartalarm.domain.logic.NextAlarmCalculator
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.SnoozeState
import java.time.ZonedDateTime

class AlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun nextAlarmClockTriggerMillis(): Long? {
        return alarmManager.nextAlarmClock?.triggerTime
    }

    fun cancel(alarmId: Long) {
        cancelPending(AlarmIntents.fireBroadcast(appContext, alarmId, 0L, false, 0))
        cancelPending(AlarmIntents.fireBroadcast(appContext, alarmId, 0L, true, 0))
    }

    fun cancelAll(alarmIds: Iterable<Long>) {
        alarmIds.forEach { cancel(it) }
    }

    fun scheduleAlarm(alarm: Alarm, from: ZonedDateTime = ZonedDateTime.now()): Long? {
        cancel(alarm.id)
        if (!alarm.enabled) return null
        val trigger = NextAlarmCalculator.nextTrigger(alarm, from) ?: return null
        val epoch = trigger.toInstant().toEpochMilli()
        setAlarm(alarm.id, epoch, isSnooze = false, snoozeCount = 0, showClock = true)
        return epoch
    }

    fun scheduleSnooze(state: SnoozeState) {
        cancelPending(AlarmIntents.fireBroadcast(appContext, state.alarmId, 0L, true, 0))
        setAlarm(
            alarmId = state.alarmId,
            epochMs = state.triggerAtEpochMs,
            isSnooze = true,
            snoozeCount = state.snoozeCount,
            showClock = true
        )
    }

    fun rescheduleAll(alarms: List<Alarm>, snooze: SnoozeState?) {
        alarms.forEach { alarm ->
            if (alarm.enabled) {
                scheduleAlarm(alarm)
            } else {
                cancel(alarm.id)
            }
        }
        if (snooze != null) {
            scheduleSnooze(snooze)
        }
    }

    private fun setAlarm(
        alarmId: Long,
        epochMs: Long,
        isSnooze: Boolean,
        snoozeCount: Int,
        showClock: Boolean
    ) {
        val operation = AlarmIntents.fireBroadcast(appContext, alarmId, epochMs, isSnooze, snoozeCount)
        val showIntent = AlarmIntents.ringingActivity(appContext, alarmId, epochMs, isSnooze, snoozeCount)
        when {
            canScheduleExact() && showClock -> {
                val info = AlarmManager.AlarmClockInfo(epochMs, showIntent)
                alarmManager.setAlarmClock(info, operation)
            }
            canScheduleExact() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, operation)
            }
            canScheduleExact() -> {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, epochMs, operation)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Best supported fallback when exact alarms are denied. This is not a hidden timer.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, operation)
            }
            else -> {
                alarmManager.set(AlarmManager.RTC_WAKEUP, epochMs, operation)
            }
        }
    }

    private fun cancelPending(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
