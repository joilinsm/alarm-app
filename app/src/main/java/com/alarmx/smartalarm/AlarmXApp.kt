package com.alarmx.smartalarm

import android.app.Application
import com.alarmx.smartalarm.playback.AlarmNotifications
import com.alarmx.smartalarm.schedule.RescheduleWorker

class AlarmXApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AlarmNotifications.ensureChannels(this)
        RescheduleWorker.enqueue(this)
    }
}
