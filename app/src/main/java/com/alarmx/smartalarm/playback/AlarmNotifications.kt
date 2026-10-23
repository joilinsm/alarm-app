package com.alarmx.smartalarm.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.alarmx.smartalarm.R
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.schedule.AlarmIntents

object AlarmNotifications {
    const val CHANNEL_FIRING = "alarmx_firing"
    const val CHANNEL_STATUS = "alarmx_status"
    const val FIRING_ID = 7101
    const val STATUS_ID = 7102

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val firing = NotificationChannel(
            CHANNEL_FIRING,
            context.getString(R.string.channel_firing_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_firing_desc)
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_status_desc)
            setSound(null, null)
        }
        manager.createNotificationChannel(firing)
        manager.createNotificationChannel(status)
    }

    fun starting(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_FIRING)
            .setSmallIcon(R.drawable.ic_notification_alarm)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_firing_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSound(null)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun firing(
        context: Context,
        alarm: Alarm,
        scheduledAt: Long,
        isSnooze: Boolean,
        snoozeCount: Int
    ): Notification {
        ensureChannels(context)
        val fullScreen = AlarmIntents.ringingActivity(context, alarm.id, scheduledAt, isSnooze, snoozeCount)
        val dismiss = AlarmIntents.serviceAction(context, AlarmIntents.ACTION_DISMISS, alarm.id)
        val snooze = AlarmIntents.serviceAction(context, AlarmIntents.ACTION_SNOOZE, alarm.id)
        val title = alarm.label.ifBlank { context.getString(R.string.app_name) }
        val text = if (isSnooze) {
            context.getString(R.string.notification_snooze_text, snoozeCount)
        } else {
            context.getString(R.string.notification_firing_text)
        }
        return NotificationCompat.Builder(context, CHANNEL_FIRING)
            .setSmallIcon(R.drawable.ic_notification_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSound(null)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(R.drawable.ic_notification_alarm, context.getString(R.string.action_dismiss), dismiss)
            .addAction(R.drawable.ic_notification_alarm, context.getString(R.string.action_snooze), snooze)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
