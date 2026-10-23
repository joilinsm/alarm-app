package com.alarmx.smartalarm.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.playback.AlarmPlaybackService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        if (alarmId <= 0L) return
        val scheduledAt = intent.getLongExtra(AlarmIntents.EXTRA_SCHEDULED_AT, 0L)
        val isSnooze = intent.getBooleanExtra(AlarmIntents.EXTRA_IS_SNOOZE, false)
        val snoozeCount = intent.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0)

        val app = context.applicationContext as? AlarmXApp
        val guard = app?.container?.deliveryGuard
        val key = guard?.eventKey(alarmId, scheduledAt) ?: "$alarmId:$scheduledAt"
        if (guard != null && !guard.shouldHandle(key)) {
            return
        }

        AlarmPlaybackService.start(context, alarmId, scheduledAt, isSnooze, snoozeCount)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != AlarmIntents.ACTION_RESCHEDULE
        ) {
            return
        }
        RescheduleWorker.enqueue(context)
    }
}

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED
        ) {
            RescheduleWorker.enqueue(context)
        }
    }
}
