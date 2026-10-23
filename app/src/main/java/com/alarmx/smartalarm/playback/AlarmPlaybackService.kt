package com.alarmx.smartalarm.playback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.domain.logic.SnoozeCalculator
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.schedule.AlarmIntents
import com.alarmx.smartalarm.ui.ringing.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmPlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var audio: AlarmAudioController? = null
    private var vibration: VibrationController? = null
    private var flashlight: FlashlightController? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var timeoutJob: Job? = null
    private var currentAlarm: Alarm? = null
    private var currentSnoozeCount: Int = 0
    private var currentScheduledAt: Long = 0L
    private var currentIsSnooze: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AlarmNotifications.ensureChannels(this)
        val app = application as AlarmXApp
        audio = AlarmAudioController(this, app.container.userFilesStore)
        vibration = VibrationController(this)
        flashlight = FlashlightController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must enter the foreground before any suspend work so a missing alarm
        // or slow Room read cannot crash startForegroundService().
        promoteToForeground(AlarmNotifications.starting(this))
        when (intent?.action) {
            AlarmIntents.ACTION_DISMISS, AlarmIntents.ACTION_STOP -> {
                scope.launch { dismiss(reschedule = true) }
                return START_NOT_STICKY
            }
            AlarmIntents.ACTION_SNOOZE -> {
                scope.launch { snooze() }
                return START_NOT_STICKY
            }
        }
        val alarmId = intent?.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L) ?: -1L
        if (alarmId <= 0L) {
            stopSelfSafely()
            return START_NOT_STICKY
        }
        currentScheduledAt = intent?.getLongExtra(AlarmIntents.EXTRA_SCHEDULED_AT, 0L) ?: 0L
        currentIsSnooze = intent?.getBooleanExtra(AlarmIntents.EXTRA_IS_SNOOZE, false) ?: false
        currentSnoozeCount = intent?.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0) ?: 0
        scope.launch { begin(alarmId) }
        return START_STICKY
    }

    private suspend fun begin(alarmId: Long) {
        val app = application as AlarmXApp
        val alarm = app.container.alarmRepository.getById(alarmId)
        if (alarm == null) {
            stopSelfSafely()
            return
        }
        currentAlarm = alarm
        promoteToForeground(
            AlarmNotifications.firing(this, alarm, currentScheduledAt, currentIsSnooze, currentSnoozeCount)
        )
        acquireWakeLock()
        launchRingingUi(alarm)
        if (alarm.vibrate) {
            vibration?.start(alarm.vibrationPattern)
        }
        flashlight?.start(scope, alarm.flashlightEnabled)
        val playbackError = try {
            when (val result = audio?.start(alarm, scope)) {
                is PlaybackStartResult.Failed -> result.message
                else -> null
            }
        } catch (error: Exception) {
            android.util.Log.e("AlarmXService", "Audio start crashed", error)
            audio?.stop()
            error.message ?: "Alarm audio failed to start."
        }
        val minutes = alarm.maxRingDurationMinutes.coerceIn(1, 30)
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(minutes * 60_000L)
            dismiss(reschedule = true)
        }
        app.container.activeAlarmStore.setActive(
            alarm,
            currentScheduledAt,
            currentIsSnooze,
            currentSnoozeCount,
            playbackError
        )
        if (currentIsSnooze) {
            app.container.settingsRepository.clearSnoozeFor(alarm.id)
        }
    }

    private fun promoteToForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AlarmNotifications.FIRING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(AlarmNotifications.FIRING_ID, notification)
        }
    }

    private fun launchRingingUi(alarm: Alarm) {
        val intent = AlarmRingingActivity.createIntent(
            this, alarm.id, currentScheduledAt, currentIsSnooze, currentSnoozeCount
        )
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Full-screen notification remains the lock-screen fallback.
        }
    }

    private suspend fun snooze() {
        val alarm = currentAlarm ?: return
        val app = application as AlarmXApp
        val next = SnoozeCalculator.nextState(alarm, currentSnoozeCount, System.currentTimeMillis())
        stopPlayback()
        if (next != null) {
            app.container.settingsRepository.saveSnooze(next)
            app.container.alarmScheduler.scheduleSnooze(next)
        } else if (alarm.isRepeating()) {
            app.container.alarmScheduler.scheduleAlarm(alarm)
        } else {
            app.container.alarmRepository.disableOneTimeAfterFire(alarm)
        }
        app.container.activeAlarmStore.clear()
        stopSelfSafely()
    }

    private suspend fun dismiss(reschedule: Boolean) {
        val alarm = currentAlarm
        val app = application as AlarmXApp
        stopPlayback()
        if (alarm != null && reschedule) {
            app.container.settingsRepository.clearSnoozeFor(alarm.id)
            if (alarm.isRepeating() && alarm.enabled) {
                val epoch = app.container.alarmScheduler.scheduleAlarm(alarm)
                app.container.alarmRepository.setLastScheduled(alarm.id, epoch)
            } else {
                app.container.alarmScheduler.cancel(alarm.id)
                app.container.alarmRepository.disableOneTimeAfterFire(alarm)
            }
        }
        app.container.activeAlarmStore.clear()
        stopSelfSafely()
    }

    private fun stopPlayback() {
        timeoutJob?.cancel()
        timeoutJob = null
        audio?.stop()
        vibration?.stop()
        flashlight?.stop()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmx:firing").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(
            context: Context,
            alarmId: Long,
            scheduledAt: Long,
            isSnooze: Boolean,
            snoozeCount: Int
        ) {
            val intent = Intent(context, AlarmPlaybackService::class.java).apply {
                putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmIntents.EXTRA_SCHEDULED_AT, scheduledAt)
                putExtra(AlarmIntents.EXTRA_IS_SNOOZE, isSnooze)
                putExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, snoozeCount)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
