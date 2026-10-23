package com.alarmx.smartalarm.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.domain.model.WallpaperRef
import com.alarmx.smartalarm.ui.about.AboutScreen
import com.alarmx.smartalarm.ui.editor.EditAlarmScreen
import com.alarmx.smartalarm.ui.editor.EditAlarmViewModel
import com.alarmx.smartalarm.ui.home.HomeScreen
import com.alarmx.smartalarm.ui.home.HomeViewModel
import com.alarmx.smartalarm.ui.recurrence.RecurrenceScreen
import com.alarmx.smartalarm.ui.reliability.ReliabilityScreen
import com.alarmx.smartalarm.ui.settings.SettingsScreen
import com.alarmx.smartalarm.ui.sound.SoundScreen
import com.alarmx.smartalarm.ui.soundtools.SoundToolsScreen
import com.alarmx.smartalarm.ui.theme.AlarmXTheme
import com.alarmx.smartalarm.ui.updates.UpdateViewModel
import com.alarmx.smartalarm.ui.volume.VolumeScreen
import com.alarmx.smartalarm.ui.wallpaper.WallpaperPreviewScreen
import com.alarmx.smartalarm.ui.wallpaper.WallpaperScreen
import com.alarmx.smartalarm.util.TimeFormat

object Routes {
    const val HOME = "home"
    const val EDIT = "edit/{alarmId}"
    const val SOUND = "sound"
    const val WALLPAPER = "wallpaper"
    const val WALLPAPER_PREVIEW = "wallpaperPreview"
    const val VOLUME = "volume"
    const val RECURRENCE = "recurrence"
    const val SOUND_TOOLS = "soundtools"
    const val SETTINGS = "settings"
    const val RELIABILITY = "reliability"
    const val ABOUT = "about"

    fun edit(id: Long) = "edit/$id"
}

@Composable
fun AlarmXAppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as AlarmXApp
    val settings by app.container.settingsRepository.settings.collectAsState()
    val dark = if (settings.followSystemTheme) isSystemInDarkTheme() else settings.useDarkTheme
    AlarmXTheme(darkTheme = dark) {
        val nav = rememberNavController()
        val editorVm: EditAlarmViewModel = viewModel()
        val homeVm: HomeViewModel = viewModel()
        val updateVm: UpdateViewModel = viewModel()
        val homeState by homeVm.state.collectAsState()
        val editorState by editorVm.state.collectAsState()
        val updateState by updateVm.state.collectAsState()
        val is24 = TimeFormat.is24Hour(context, settings.timeFormat)
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            updateVm.checkForUpdates(manual = false)
        }
        LaunchedEffect(editorState.message) {
            val message = editorState.message ?: return@LaunchedEffect
            snackbar.showSnackbar(message)
            editorVm.consumeMessage()
        }
        LaunchedEffect(updateState.message) {
            val message = updateState.message ?: return@LaunchedEffect
            snackbar.showSnackbar(message)
            updateVm.consumeMessage()
        }
        LaunchedEffect(editorState.preview) {
            val preview = editorState.preview
            val current = nav.currentDestination?.route
            if (preview != null && current != Routes.WALLPAPER_PREVIEW) {
                nav.navigate(Routes.WALLPAPER_PREVIEW)
            }
        }

        androidx.compose.foundation.layout.Box {
            NavHost(navController = nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        state = homeState,
                        onAdd = {
                            editorVm.load(0L, force = true)
                            nav.navigate(Routes.edit(0L))
                        },
                        onEdit = { id ->
                            editorVm.load(id, force = true)
                            nav.navigate(Routes.edit(id))
                        },
                        onToggle = homeVm::setEnabled,
                        onDelete = homeVm::delete,
                        onDuplicate = homeVm::duplicate,
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenSoundTools = { nav.navigate(Routes.SOUND_TOOLS) },
                        onOpenReliability = { nav.navigate(Routes.RELIABILITY) }
                    )
                }
                composable(
                    Routes.EDIT,
                    arguments = listOf(navArgument("alarmId") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("alarmId") ?: 0L
                    LaunchedEffect(id) { editorVm.load(id, force = false) }
                    LaunchedEffect(editorState.saved) {
                        if (editorState.saved) nav.popBackStack()
                    }
                    EditAlarmScreen(
                        state = editorState,
                        is24Hour = is24,
                        onBack = { nav.popBackStack() },
                        onSave = editorVm::save,
                        onChange = editorVm::update,
                        onToggleDay = editorVm::toggleDay,
                        onOpenSound = { nav.navigate(Routes.SOUND) },
                        onOpenWallpaper = { nav.navigate(Routes.WALLPAPER) },
                        onOpenVolume = { nav.navigate(Routes.VOLUME) },
                        onOpenRecurrence = { nav.navigate(Routes.RECURRENCE) }
                    )
                }
                composable(Routes.SOUND) {
                    SoundScreen(
                        selected = editorState.alarm.sound,
                        errors = editorState.errors,
                        onSelect = editorVm::setSound,
                        onImport = editorVm::importSound,
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Routes.WALLPAPER) {
                    WallpaperScreen(
                        selected = editorState.alarm.wallpaper,
                        overlay = editorState.alarm.overlayDarkness,
                        errors = editorState.errors,
                        onPreviewBundled = { editorVm.beginWallpaperPreview(it, isNewImport = false) },
                        onPreviewUser = { editorVm.beginWallpaperPreview(it, isNewImport = false) },
                        onPreviewVideo = {
                            val hasAudio = (it as? WallpaperRef.VideoFile)?.let { video ->
                                app.container.userFilesStore.videoHasAudio(
                                    app.container.userFilesStore.videoFile(video.fileName)
                                )
                            } ?: true
                            editorVm.beginWallpaperPreview(it, isNewImport = false, videoHasAudio = hasAudio)
                        },
                        onImportImage = editorVm::importWallpaperForPreview,
                        onImportVideo = editorVm::importVideoForPreview,
                        onRestoreDefault = {
                            editorVm.setWallpaper(WallpaperRef.Default)
                        },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Routes.WALLPAPER_PREVIEW) {
                    val preview = editorState.preview
                    if (preview == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        WallpaperPreviewScreen(
                            draft = preview,
                            alarmLabel = editorState.alarm.label,
                            onFit = { fit -> editorVm.updatePreview { it.copy(fit = fit) } },
                            onOverlay = { value -> editorVm.updatePreview { it.copy(overlay = value) } },
                            onVideoMode = { mode -> editorVm.updatePreview { it.copy(videoAudioMode = mode) } },
                            onVideoVolume = { value -> editorVm.updatePreview { it.copy(videoVolumePercent = value) } },
                            onApply = { editorVm.applyPreview() },
                            onCancel = { editorVm.cancelPreview() }
                        )
                    }
                }
                composable(Routes.VOLUME) {
                    VolumeScreen(
                        alarm = editorState.alarm,
                        onChange = editorVm::setVolume,
                        hearingAcknowledged = settings.hearingSafetyAcknowledged,
                        onHearingAcknowledged = {
                            app.container.settingsRepository.update { it.copy(hearingSafetyAcknowledged = true) }
                        },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Routes.RECURRENCE) {
                    RecurrenceScreen(
                        alarm = editorState.alarm,
                        onSave = {
                            editorVm.setRecurrence(it)
                            nav.popBackStack()
                        },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Routes.SOUND_TOOLS) {
                    SoundToolsScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        updateState = updateState,
                        onChange = { next -> app.container.settingsRepository.update { next } },
                        onCheckUpdates = { updateVm.checkForUpdates(manual = true) },
                        onDownloadUpdate = updateVm::downloadAndInstall,
                        onDismissUpdate = updateVm::dismissAvailable,
                        onSaveGithubToken = updateVm::saveGithubToken,
                        onOpenReliability = { nav.navigate(Routes.RELIABILITY) },
                        onOpenAbout = { nav.navigate(Routes.ABOUT) },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Routes.RELIABILITY) {
                    ReliabilityScreen(onBack = { nav.popBackStack() })
                }
                composable(Routes.ABOUT) {
                    AboutScreen(onBack = { nav.popBackStack() })
                }
            }
            SnackbarHost(hostState = snackbar)
        }
    }
}
