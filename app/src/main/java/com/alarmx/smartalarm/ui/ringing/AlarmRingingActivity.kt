package com.alarmx.smartalarm.ui.ringing

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.playback.AlarmPlaybackService
import com.alarmx.smartalarm.schedule.AlarmIntents
import com.alarmx.smartalarm.ui.theme.AlarmXTheme
class AlarmRingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockscreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        val scheduledAt = intent.getLongExtra(AlarmIntents.EXTRA_SCHEDULED_AT, 0L)
        val isSnooze = intent.getBooleanExtra(AlarmIntents.EXTRA_IS_SNOOZE, false)
        val snoozeCount = intent.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0)
        val app = application as AlarmXApp

        setContent {
            val settings by app.container.settingsRepository.settings.collectAsState()
            val active by app.container.activeAlarmStore.active.collectAsState()
            val dark = settings.followSystemTheme || settings.useDarkTheme
            AlarmXTheme(darkTheme = if (settings.followSystemTheme) dark else settings.useDarkTheme) {
                AlarmRingingRoute(
                    app = app,
                    fallbackAlarmId = alarmId,
                    fallbackScheduledAt = scheduledAt,
                    fallbackIsSnooze = isSnooze,
                    fallbackSnoozeCount = snoozeCount,
                    onSnooze = { sendService(AlarmIntents.ACTION_SNOOZE, alarmId) },
                    onDismiss = { sendService(AlarmIntents.ACTION_DISMISS, alarmId) }
                )
            }
        }
    }

    private fun sendService(action: String, alarmId: Long) {
        val intent = Intent(this, AlarmPlaybackService::class.java).apply {
            this.action = action
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        startService(intent)
        finish()
    }

    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        fun createIntent(
            context: Context,
            alarmId: Long,
            scheduledAt: Long,
            isSnooze: Boolean,
            snoozeCount: Int
        ): Intent {
            return Intent(context, AlarmRingingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmIntents.EXTRA_SCHEDULED_AT, scheduledAt)
                putExtra(AlarmIntents.EXTRA_IS_SNOOZE, isSnooze)
                putExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, snoozeCount)
            }
        }
    }
}
