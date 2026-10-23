package com.alarmx.smartalarm.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.alarmx.smartalarm.MainActivity
import com.alarmx.smartalarm.R

object UpdateNotifier {
    const val CHANNEL_UPDATES = "alarmx_updates"
    const val NOTIFICATION_ID = 7201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_UPDATES,
            "App updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when a newer AlarmX APK is published."
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyAvailable(context: Context, release: ReleaseManifest) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).putExtra("open_updates", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification_alarm)
            .setContentTitle("AlarmX ${release.versionName} available")
            .setContentText(release.releaseNotes.ifBlank { "A newer APK is ready to download." }.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(release.releaseNotes.ifBlank { "Open Settings → Check for updates." }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}
