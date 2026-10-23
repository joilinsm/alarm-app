package com.alarmx.smartalarm.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.domain.logic.AlarmValidator
import com.alarmx.smartalarm.domain.logic.EditorLoadPolicy
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.DismissMethod
import com.alarmx.smartalarm.domain.model.RecurrenceMode
import com.alarmx.smartalarm.domain.model.RecurrenceSchedule
import com.alarmx.smartalarm.domain.model.SoundRef
import com.alarmx.smartalarm.domain.model.VibrationPattern
import com.alarmx.smartalarm.domain.model.VideoAudioMode
import com.alarmx.smartalarm.domain.model.VolumeSettings
import com.alarmx.smartalarm.domain.model.WallpaperFit
import com.alarmx.smartalarm.domain.model.WallpaperRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZonedDateTime

fun defaultEditorAlarm(): Alarm {
    val now = ZonedDateTime.now().plusMinutes(5)
    return Alarm(hour = now.hour, minute = now.minute, label = "Alarm")
}

data class WallpaperPreviewDraft(
    val wallpaper: WallpaperRef,
    val fit: WallpaperFit,
    val overlay: Float,
    val isNewImport: Boolean,
    val videoAudioMode: VideoAudioMode = VideoAudioMode.SEPARATE_RINGTONE,
    val videoHasAudio: Boolean = true,
    val videoVolumePercent: Int = 80
)

data class EditorUiState(
    val alarm: Alarm = defaultEditorAlarm(),
    val isNew: Boolean = true,
    val errors: List<String> = emptyList(),
    val message: String? = null,
    val saved: Boolean = false,
    val exactAlarmsAllowed: Boolean = true,
    val ready: Boolean = false,
    val preview: WallpaperPreviewDraft? = null
)

class EditAlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AlarmXApp
    private var loadedKey: Long? = null
    private val _state = MutableStateFlow(
        EditorUiState(exactAlarmsAllowed = app.container.alarmScheduler.canScheduleExact())
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    fun load(alarmId: Long, force: Boolean = false) {
        if (!EditorLoadPolicy.shouldReload(_state.value.ready, loadedKey, alarmId, force)) {
            return
        }
        viewModelScope.launch {
            if (alarmId <= 0L) {
                val settings = app.container.settingsRepository.settings.value
                loadedKey = 0L
                _state.value = EditorUiState(
                    alarm = defaultEditorAlarm().copy(snoozeMinutes = settings.defaultSnoozeMinutes),
                    isNew = true,
                    exactAlarmsAllowed = app.container.alarmScheduler.canScheduleExact(),
                    ready = true
                )
            } else {
                val existing = app.container.alarmRepository.getById(alarmId)
                loadedKey = EditorLoadPolicy.keyFor(existing?.id ?: alarmId)
                _state.value = if (existing != null) {
                    EditorUiState(
                        alarm = existing,
                        isNew = false,
                        exactAlarmsAllowed = app.container.alarmScheduler.canScheduleExact(),
                        ready = true
                    )
                } else {
                    loadedKey = 0L
                    EditorUiState(
                        alarm = defaultEditorAlarm(),
                        isNew = true,
                        exactAlarmsAllowed = app.container.alarmScheduler.canScheduleExact(),
                        ready = true
                    )
                }
            }
        }
    }

    fun update(transform: (Alarm) -> Alarm) {
        _state.update { it.copy(alarm = transform(it.alarm).sanitized(), saved = false, errors = emptyList()) }
    }

    fun toggleDay(day: DayOfWeek) {
        update { alarm ->
            val days = alarm.repeatDays.toggle(day)
            val schedule = if (days.isOneTime) {
                RecurrenceSchedule.oneTime()
            } else {
                RecurrenceSchedule(RecurrenceMode.WEEKLY, weeklyDays = days)
            }
            alarm.copy(repeatDays = days, recurrence = schedule)
        }
    }

    fun setRecurrence(schedule: RecurrenceSchedule) {
        update { alarm ->
            val sanitized = schedule.sanitized()
            alarm.copy(
                recurrence = sanitized,
                repeatDays = when (sanitized.mode) {
                    RecurrenceMode.WEEKLY -> sanitized.weeklyDays
                    RecurrenceMode.ONE_TIME -> com.alarmx.smartalarm.domain.model.RepeatDays.NONE
                    RecurrenceMode.MONTHLY -> com.alarmx.smartalarm.domain.model.RepeatDays.NONE
                }
            )
        }
        persistDraftIfExisting()
    }

    fun setSound(sound: SoundRef) {
        update { it.copy(sound = sound) }
        persistDraftIfExisting()
    }

    fun setVolume(volume: VolumeSettings) {
        update { it.copy(volume = volume.sanitized()) }
        persistDraftIfExisting()
    }

    fun setWallpaper(
        wallpaper: WallpaperRef,
        fit: WallpaperFit? = null,
        overlay: Float? = null,
        videoAudioMode: VideoAudioMode? = null
    ) {
        update {
            val previous = it.wallpaper
            it.copy(
                wallpaper = wallpaper,
                wallpaperFit = fit ?: it.wallpaperFit,
                overlayDarkness = overlay ?: it.overlayDarkness,
                videoAudioMode = videoAudioMode ?: it.videoAudioMode,
                fallbackWallpaper = when {
                    wallpaper is WallpaperRef.VideoFile && previous !is WallpaperRef.VideoFile -> previous
                    wallpaper is WallpaperRef.VideoFile -> it.fallbackWallpaper
                    else -> it.fallbackWallpaper
                }
            )
        }
        persistDraftIfExisting()
    }

    fun setDismiss(method: DismissMethod) = update { it.copy(dismissMethod = method) }

    fun setVibration(pattern: VibrationPattern, enabled: Boolean) {
        update { it.copy(vibrate = enabled, vibrationPattern = pattern) }
    }

    fun importSound(uri: Uri) {
        viewModelScope.launch {
            val previous = _state.value.alarm.sound
            val result = app.container.userFilesStore.importSound(uri)
            result.fold(
                onSuccess = { name ->
                    setSound(SoundRef.UserFile(name))
                    _state.update { it.copy(message = "Imported $name", errors = emptyList()) }
                },
                onFailure = { error ->
                    setSound(previous)
                    _state.update {
                        it.copy(errors = listOf(error.message ?: "Could not import that audio file."))
                    }
                }
            )
        }
    }

    fun beginWallpaperPreview(wallpaper: WallpaperRef, isNewImport: Boolean, videoHasAudio: Boolean = true) {
        val current = _state.value.alarm
        _state.update {
            it.copy(
                preview = WallpaperPreviewDraft(
                    wallpaper = wallpaper,
                    fit = current.wallpaperFit,
                    overlay = current.overlayDarkness,
                    isNewImport = isNewImport,
                    videoAudioMode = if (wallpaper is WallpaperRef.VideoFile) {
                        current.videoAudioMode
                    } else {
                        VideoAudioMode.SEPARATE_RINGTONE
                    },
                    videoHasAudio = videoHasAudio,
                    videoVolumePercent = current.volume.videoVolumePercent
                ),
                errors = emptyList()
            )
        }
    }

    fun importWallpaperForPreview(uri: Uri) {
        viewModelScope.launch {
            val result = app.container.userFilesStore.importWallpaper(uri)
            result.fold(
                onSuccess = { name ->
                    beginWallpaperPreview(WallpaperRef.UserFile(name), isNewImport = true)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(errors = listOf(error.message ?: "Could not import that image."))
                    }
                }
            )
        }
    }

    fun importVideoForPreview(uri: Uri) {
        viewModelScope.launch {
            val result = app.container.userFilesStore.importVideo(uri)
            result.fold(
                onSuccess = { imported ->
                    beginWallpaperPreview(
                        WallpaperRef.VideoFile(imported.fileName),
                        isNewImport = true,
                        videoHasAudio = imported.hasAudioTrack
                    )
                    if (!imported.hasAudioTrack) {
                        _state.update {
                            it.copy(
                                message = "This video has no audio track. Use a separate ringtone.",
                                preview = it.preview?.copy(
                                    videoAudioMode = VideoAudioMode.SEPARATE_RINGTONE,
                                    videoHasAudio = false
                                )
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(errors = listOf(error.message ?: "Could not import that video."))
                    }
                }
            )
        }
    }

    fun updatePreview(transform: (WallpaperPreviewDraft) -> WallpaperPreviewDraft) {
        _state.update { state ->
            val preview = state.preview ?: return@update state
            state.copy(preview = transform(preview))
        }
    }

    fun applyPreview() {
        val preview = _state.value.preview ?: return
        if (preview.wallpaper is WallpaperRef.VideoFile &&
            preview.videoAudioMode != VideoAudioMode.SEPARATE_RINGTONE &&
            !preview.videoHasAudio
        ) {
            _state.update {
                it.copy(errors = listOf("This video has no audio. Choose Separate ringtone or cancel."))
            }
            return
        }
        setWallpaper(preview.wallpaper, preview.fit, preview.overlay, preview.videoAudioMode)
        update {
            it.copy(
                volume = it.volume.copy(videoVolumePercent = preview.videoVolumePercent)
            )
        }
        _state.update { it.copy(preview = null, message = "Wallpaper applied to this alarm") }
    }

    fun cancelPreview() {
        val preview = _state.value.preview
        _state.update { it.copy(preview = null) }
        if (preview?.isNewImport == true) {
            when (val wall = preview.wallpaper) {
                is WallpaperRef.UserFile -> {
                    val current = _state.value.alarm.wallpaper
                    if (current.storageKey != wall.storageKey) {
                        app.container.userFilesStore.deleteWallpaper(wall.fileName)
                    }
                }
                is WallpaperRef.VideoFile -> {
                    val current = _state.value.alarm.wallpaper
                    if (current.storageKey != wall.storageKey) {
                        app.container.userFilesStore.deleteVideo(wall.fileName)
                    }
                }
                else -> Unit
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun save() {
        viewModelScope.launch {
            val current = _state.value.alarm
            val errors = AlarmValidator.validate(current)
            if (errors.isNotEmpty()) {
                _state.update { it.copy(errors = errors) }
                return@launch
            }
            val saved = app.container.alarmRepository.upsert(current)
            loadedKey = saved.id
            if (saved.enabled) {
                val epoch = app.container.alarmScheduler.scheduleAlarm(saved)
                app.container.alarmRepository.setLastScheduled(saved.id, epoch)
            } else {
                app.container.alarmScheduler.cancel(saved.id)
            }
            _state.update { it.copy(alarm = saved, isNew = false, saved = true, errors = emptyList()) }
        }
    }

    private fun persistDraftIfExisting() {
        val alarm = _state.value.alarm
        if (alarm.id <= 0L) return
        viewModelScope.launch {
            app.container.alarmRepository.upsert(alarm)
        }
    }
}
