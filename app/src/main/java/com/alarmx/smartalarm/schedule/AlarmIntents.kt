package com.alarmx.smartalarm.schedule

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.alarmx.smartalarm.playback.AlarmPlaybackService
import com.alarmx.smartalarm.ui.ringing.AlarmRingingActivity

object AlarmIntents {
    const val ACTION_FIRE = "com.alarmx.smartalarm.action.FIRE"
    const val ACTION_SNOOZE_FIRE = "com.alarmx.smartalarm.action.SNOOZE_FIRE"
    const val ACTION_SNOOZE = "com.alarmx.smartalarm.action.SNOOZE"
    const val ACTION_DISMISS = "com.alarmx.smartalarm.action.DISMISS"
    const val ACTION_STOP = "com.alarmx.smartalarm.action.STOP"
    const val ACTION_RESCHEDULE = "com.alarmx.smartalarm.action.RESCHEDULE"

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_SCHEDULED_AT = "extra_scheduled_at"
    const val EXTRA_IS_SNOOZE = "extra_is_snooze"
    const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"

    fun fireBroadcast(
        context: Context,
        alarmId: Long,
        scheduledAt: Long,
        isSnooze: Boolean,
        snoozeCount: Int
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = if (isSnooze) ACTION_SNOOZE_FIRE else ACTION_FIRE
            data = Uri.parse("alarmx://alarm/$alarmId/${if (isSnooze) "snooze" else "fire"}")
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
            putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, isSnooze),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun ringingActivity(
        context: Context,
        alarmId: Long,
        scheduledAt: Long,
        isSnooze: Boolean,
        snoozeCount: Int
    ): PendingIntent {
        val intent = AlarmRingingActivity.createIntent(context, alarmId, scheduledAt, isSnooze, snoozeCount)
        return PendingIntent.getActivity(
            context,
            requestCode(alarmId, isSnooze) + 17,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun serviceAction(context: Context, action: String, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmPlaybackService::class.java).apply {
            this.action = action
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getService(
            context,
            (alarmId.toInt() xor action.hashCode()) and 0x7FFFFFFF,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun requestCode(alarmId: Long, isSnooze: Boolean): Int {
        val base = (alarmId % 100_000L).toInt()
        return if (isSnooze) 200_000 + base else 100_000 + base
    }
}
