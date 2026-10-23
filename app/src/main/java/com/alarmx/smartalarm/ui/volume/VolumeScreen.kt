package com.alarmx.smartalarm.ui.volume

import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.audio.SoundLabels
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.VolumeSettings
import com.alarmx.smartalarm.playback.AlarmAudioController
import com.alarmx.smartalarm.playback.PlaybackStartResult
import com.alarmx.smartalarm.ui.components.SectionCard
import com.alarmx.smartalarm.ui.components.SettingRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeScreen(
    alarm: Alarm,
    onChange: (VolumeSettings) -> Unit,
    onHearingAcknowledged: () -> Unit,
    hearingAcknowledged: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AlarmXApp
    val audioManager = context.getSystemService(AudioManager::class.java)
    val controller = remember { AlarmAudioController(context, app.container.userFilesStore) }
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var testError by remember { mutableStateOf<String?>(null) }
    val volume = alarm.volume.sanitized()
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

    DisposableEffect(Unit) { onDispose { controller.stop() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarm volume") },
                navigationIcon = {
                    IconButton(onClick = { controller.stop(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                Text("Applies to: ${SoundLabels.title(alarm.sound)}")
                Text("Playback gain ${volume.volumePercent}% (0–100 maps to player volume 0.0–1.0)")
                Slider(
                    value = volume.volumePercent.toFloat(),
                    onValueChange = { onChange(volume.copy(volumePercent = it.toInt(), rampTargetPercent = it.toInt())) },
                    valueRange = 0f..100f
                )
                Text(
                    "System alarm stream is $current of $max. AlarmX does not change that stream unless you open system settings yourself. If the system alarm volume is 0, playback will be silent."
                )
                if (current == 0) {
                    Text(
                        "System alarm volume is muted. Raise it in Android sound settings.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open system sound settings") }
            }
            SectionCard {
                SettingRow(
                    "Gradual volume increase",
                    "Ramps player gain only; the selected sound does not restart.",
                    checked = volume.gradualEnabled,
                    onCheckedChange = { onChange(volume.copy(gradualEnabled = it)) }
                )
                Text("Start ${volume.rampStartPercent}%")
                Slider(
                    value = volume.rampStartPercent.toFloat(),
                    onValueChange = { onChange(volume.copy(rampStartPercent = it.toInt())) },
                    valueRange = 0f..100f
                )
                Text("Target ${volume.rampTargetPercent}%")
                Slider(
                    value = volume.rampTargetPercent.toFloat(),
                    onValueChange = { onChange(volume.copy(rampTargetPercent = it.toInt(), volumePercent = it.toInt())) },
                    valueRange = 0f..100f
                )
                Text("Ramp ${(volume.rampDurationMs / 1000)} seconds")
                Slider(
                    value = (volume.rampDurationMs / 1000f),
                    onValueChange = { onChange(volume.copy(rampDurationMs = it.toInt() * 1000)) },
                    valueRange = 0f..60f
                )
            }
            if (alarm.wallpaper is com.alarmx.smartalarm.domain.model.WallpaperRef.VideoFile) {
                SectionCard {
                    Text("Video wallpaper audio gains")
                    Text("Video volume ${volume.videoVolumePercent}%")
                    Slider(
                        value = volume.videoVolumePercent.toFloat(),
                        onValueChange = { onChange(volume.copy(videoVolumePercent = it.toInt())) },
                        valueRange = 0f..100f
                    )
                    Text("Ringtone volume ${volume.ringtoneVolumePercent}% (used in mixed mode)")
                    Slider(
                        value = volume.ringtoneVolumePercent.toFloat(),
                        onValueChange = { onChange(volume.copy(ringtoneVolumePercent = it.toInt())) },
                        valueRange = 0f..100f
                    )
                }
            }
            SectionCard {
                SettingRow(
                    "Software amplification",
                    "Decodes the same selected file and applies 0–12 dB with a limiter. This is not extra hardware loudness.",
                    checked = volume.softwareAmpEnabled,
                    onCheckedChange = { onChange(volume.copy(softwareAmpEnabled = it)) }
                )
                Text("${"%.1f".format(volume.softwareGainDb)} dB")
                Slider(
                    value = volume.softwareGainDb,
                    onValueChange = { onChange(volume.copy(softwareGainDb = it)) },
                    valueRange = 0f..12f,
                    enabled = volume.softwareAmpEnabled
                )
                if (!hearingAcknowledged) {
                    Text(
                        "High gain and high volume can damage hearing.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = onHearingAcknowledged, modifier = Modifier.fillMaxWidth()) {
                        Text("I understand the hearing risk")
                    }
                }
            }
            testError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    scope.launch {
                        controller.stop()
                        testing = true
                        testError = null
                        when (val result = controller.start(alarm.copy(volume = volume), scope)) {
                            is PlaybackStartResult.Started -> Unit
                            is PlaybackStartResult.Failed -> {
                                testing = false
                                testError = result.message
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Test alarm sound") }
            OutlinedButton(
                onClick = { controller.stop(); testing = false },
                enabled = testing,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Stop test immediately") }
        }
    }
}
