package com.alarmx.smartalarm.data.repo

import android.content.Context
import com.alarmx.smartalarm.domain.model.AppSettings
import com.alarmx.smartalarm.domain.model.SnoozeState
import com.alarmx.smartalarm.domain.model.TimeFormatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _snooze = MutableStateFlow(readSnooze())
    val snooze: StateFlow<SnoozeState?> = _snooze.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_TIME_FORMAT, next.timeFormat.name)
            .putBoolean(KEY_DARK, next.useDarkTheme)
            .putBoolean(KEY_FOLLOW_SYSTEM, next.followSystemTheme)
            .putInt(KEY_DEFAULT_SNOOZE, next.defaultSnoozeMinutes)
            .putBoolean(KEY_HEARING, next.hearingSafetyAcknowledged)
            .putString(KEY_UPDATE_URL, next.updateManifestUrl)
            .putString(KEY_GH_OWNER, next.githubOwner)
            .putString(KEY_GH_REPO, next.githubRepo)
            .putInt(KEY_LAST_NOTIFIED, next.lastNotifiedVersionCode)
            .putLong(KEY_LAST_CHECK, next.lastUpdateCheckEpochMs)
            .apply()
        _settings.value = next
    }

    fun saveSnooze(state: SnoozeState?) {
        if (state == null) {
            prefs.edit()
                .remove(KEY_SNOOZE_ID)
                .remove(KEY_SNOOZE_COUNT)
                .remove(KEY_SNOOZE_AT)
                .apply()
        } else {
            prefs.edit()
                .putLong(KEY_SNOOZE_ID, state.alarmId)
                .putInt(KEY_SNOOZE_COUNT, state.snoozeCount)
                .putLong(KEY_SNOOZE_AT, state.triggerAtEpochMs)
                .apply()
        }
        _snooze.value = state
    }

    fun clearSnoozeFor(alarmId: Long) {
        if (_snooze.value?.alarmId == alarmId) {
            saveSnooze(null)
        }
    }

    fun currentSnooze(): SnoozeState? = _snooze.value

    private fun readSettings(): AppSettings {
        return AppSettings(
            timeFormat = runCatching {
                TimeFormatMode.valueOf(prefs.getString(KEY_TIME_FORMAT, TimeFormatMode.FOLLOW_SYSTEM.name)!!)
            }.getOrDefault(TimeFormatMode.FOLLOW_SYSTEM),
            useDarkTheme = prefs.getBoolean(KEY_DARK, true),
            followSystemTheme = prefs.getBoolean(KEY_FOLLOW_SYSTEM, true),
            defaultSnoozeMinutes = prefs.getInt(KEY_DEFAULT_SNOOZE, 5),
            hearingSafetyAcknowledged = prefs.getBoolean(KEY_HEARING, false),
            updateManifestUrl = prefs.getString(KEY_UPDATE_URL, "").orEmpty(),
            githubOwner = prefs.getString(KEY_GH_OWNER, "joilinsm").orEmpty(),
            githubRepo = prefs.getString(KEY_GH_REPO, "alarm-app").orEmpty(),
            lastNotifiedVersionCode = prefs.getInt(KEY_LAST_NOTIFIED, 0),
            lastUpdateCheckEpochMs = prefs.getLong(KEY_LAST_CHECK, 0L)
        )
    }

    private fun readSnooze(): SnoozeState? {
        if (!prefs.contains(KEY_SNOOZE_ID)) return null
        return SnoozeState(
            alarmId = prefs.getLong(KEY_SNOOZE_ID, -1L),
            snoozeCount = prefs.getInt(KEY_SNOOZE_COUNT, 0),
            triggerAtEpochMs = prefs.getLong(KEY_SNOOZE_AT, 0L)
        ).takeIf { it.alarmId > 0L && it.triggerAtEpochMs > 0L }
    }

    companion object {
        private const val PREFS = "alarmx_settings"
        private const val KEY_TIME_FORMAT = "time_format"
        private const val KEY_DARK = "dark_theme"
        private const val KEY_FOLLOW_SYSTEM = "follow_system_theme"
        private const val KEY_DEFAULT_SNOOZE = "default_snooze"
        private const val KEY_HEARING = "hearing_safety"
        private const val KEY_UPDATE_URL = "update_manifest_url"
        private const val KEY_GH_OWNER = "github_owner"
        private const val KEY_GH_REPO = "github_repo"
        private const val KEY_LAST_NOTIFIED = "last_notified_version"
        private const val KEY_LAST_CHECK = "last_update_check"
        private const val KEY_SNOOZE_ID = "snooze_alarm_id"
        private const val KEY_SNOOZE_COUNT = "snooze_count"
        private const val KEY_SNOOZE_AT = "snooze_at"
    }
}
