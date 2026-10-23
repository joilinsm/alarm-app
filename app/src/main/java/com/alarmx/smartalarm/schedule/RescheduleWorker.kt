package com.alarmx.smartalarm.schedule

import android.content.Context
import com.alarmx.smartalarm.AlarmXApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object RescheduleWorker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueue(context: Context) {
        val app = context.applicationContext as? AlarmXApp ?: return
        scope.launch {
            val alarms = app.container.alarmRepository.getAll()
            val snooze = app.container.settingsRepository.currentSnooze()
            app.container.alarmScheduler.rescheduleAll(alarms, snooze)
        }
    }
}
