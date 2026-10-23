package com.alarmx.smartalarm

import android.content.Context
import com.alarmx.smartalarm.data.local.AlarmDatabase
import com.alarmx.smartalarm.data.repo.AlarmRepository
import com.alarmx.smartalarm.data.repo.SettingsRepository
import com.alarmx.smartalarm.data.repo.UserFilesStore
import com.alarmx.smartalarm.domain.logic.DeliveryGuard
import com.alarmx.smartalarm.playback.ActiveAlarmStore
import com.alarmx.smartalarm.schedule.AlarmScheduler
import com.alarmx.smartalarm.updates.UpdateRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AlarmDatabase = AlarmDatabase.create(appContext)
    val alarmRepository = AlarmRepository(database.alarmDao())
    val settingsRepository = SettingsRepository(appContext)
    val userFilesStore = UserFilesStore(appContext)
    val alarmScheduler = AlarmScheduler(appContext)
    val deliveryGuard = DeliveryGuard()
    val activeAlarmStore = ActiveAlarmStore()
    val updateRepository = UpdateRepository(appContext, settingsRepository)
}
