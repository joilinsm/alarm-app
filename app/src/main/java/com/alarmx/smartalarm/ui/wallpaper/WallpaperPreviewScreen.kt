package com.alarmx.smartalarm.ui.wallpaper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.audio.WallpaperLabels
import com.alarmx.smartalarm.domain.model.VideoAudioMode
import com.alarmx.smartalarm.domain.model.WallpaperFit
import com.alarmx.smartalarm.domain.model.WallpaperRef
import com.alarmx.smartalarm.ui.components.AlarmVideoBackdrop
import com.alarmx.smartalarm.ui.components.WallpaperBackdrop
import com.alarmx.smartalarm.ui.editor.WallpaperPreviewDraft
import com.alarmx.smartalarm.util.TimeFormat
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperPreviewScreen(
    draft: WallpaperPreviewDraft,
    alarmLabel: String,
    onFit: (WallpaperFit) -> Unit,
    onOverlay: (Float) -> Unit,
    onVideoMode: (VideoAudioMode) -> Unit,
    onVideoVolume: (Int) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AlarmXApp
    val now = ZonedDateTime.now()
    var videoFailed by remember(draft.wallpaper.storageKey) { mutableStateOf(false) }
    val imageFile = (draft.wallpaper as? WallpaperRef.UserFile)?.let {
        app.container.userFilesStore.wallpaperFile(it.fileName)
    }
    val videoFile = (draft.wallpaper as? WallpaperRef.VideoFile)?.let {
        app.container.userFilesStore.videoFile(it.fileName)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            draft.wallpaper is WallpaperRef.VideoFile && !videoFailed -> {
                AlarmVideoBackdrop(
                    videoFile = videoFile,
                    overlay = draft.overlay,
                    fit = draft.fit,
                    audioMode = draft.videoAudioMode,
                    videoVolumePercent = draft.videoVolumePercent,
                    fallback = WallpaperRef.Default,
                    playing = true,
                    onVideoFailed = { videoFailed = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                WallpaperBackdrop(
                    wallpaper = if (videoFailed) WallpaperRef.Default else draft.wallpaper,
                    overlay = draft.overlay,
                    fit = draft.fit,
                    userFile = imageFile,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Preview", color = Color.White.copy(alpha = 0.8f))
            Text(
                WallpaperLabels.title(draft.wallpaper),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                alarmLabel,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Text(
                TimeFormat.clockWithSeconds(now, true),
                color = Color.White,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp)
            )
            Text(TimeFormat.dateLine(now), color = Color.White.copy(alpha = 0.75f))
            if (videoFailed) {
                Text(
                    "Video failed — falling back to a static background for this preview.",
                    color = Color(0xFFFFB4A8),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color(0xFFF5C16C),
                    disabledContentColor = Color(0xFF1A1304)
                )
            ) { Text("Dismiss") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = Color.White)
            ) { Text("Snooze") }
            Spacer(Modifier.height(16.dp))
            Text("Fit", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperFit.entries.forEach { fit ->
                    FilterChip(
                        selected = draft.fit == fit,
                        onClick = { onFit(fit) },
                        label = { Text(fit.name.lowercase().replaceFirstChar(Char::uppercase)) }
                    )
                }
            }
            Text("Dark overlay ${(draft.overlay * 100).toInt()}%", color = Color.White)
            Slider(value = draft.overlay, onValueChange = onOverlay, valueRange = 0f..0.85f)
            if (draft.wallpaper is WallpaperRef.VideoFile) {
                Text("Video audio mode", color = Color.White)
                VideoAudioMode.entries.forEach { mode ->
                    FilterChip(
                        selected = draft.videoAudioMode == mode,
                        onClick = { onVideoMode(mode) },
                        enabled = draft.videoHasAudio || mode == VideoAudioMode.SEPARATE_RINGTONE,
                        label = { Text(videoModeLabel(mode)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 2.dp)
                    )
                }
                if (!draft.videoHasAudio) {
                    Text(
                        "No usable audio track detected. Separate ringtone is required.",
                        color = Color(0xFFFFB4A8)
                    )
                }
                if (draft.videoAudioMode != VideoAudioMode.SEPARATE_RINGTONE) {
                    Text("Video volume ${draft.videoVolumePercent}%", color = Color.White)
                    Slider(
                        value = draft.videoVolumePercent.toFloat(),
                        onValueChange = { onVideoVolume(it.toInt()) },
                        valueRange = 0f..100f
                    )
                }
                Text(
                    videoModeHint(draft.videoAudioMode),
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Apply wallpaper") }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) { Text("Cancel") }
        }
    }
}

private fun videoModeLabel(mode: VideoAudioMode): String = when (mode) {
    VideoAudioMode.SEPARATE_RINGTONE -> "A · Separate ringtone (video muted)"
    VideoAudioMode.VIDEO_AUDIO -> "B · Video audio only"
    VideoAudioMode.VIDEO_AND_RINGTONE -> "C · Video audio + ringtone"
}

private fun videoModeHint(mode: VideoAudioMode): String = when (mode) {
    VideoAudioMode.SEPARATE_RINGTONE -> "Video is silent. Your selected alarm ringtone plays."
    VideoAudioMode.VIDEO_AUDIO -> "Only the video’s audio plays. Ringtone stays saved but silent."
    VideoAudioMode.VIDEO_AND_RINGTONE -> "Video audio and ringtone play together with separate gains."
}
