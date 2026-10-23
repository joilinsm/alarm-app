package com.alarmx.smartalarm.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alarmx.smartalarm.audio.SoundLabels
import com.alarmx.smartalarm.audio.WallpaperLabels
import com.alarmx.smartalarm.domain.model.DismissMethod
import com.alarmx.smartalarm.domain.model.VibrationPattern
import com.alarmx.smartalarm.ui.components.SectionCard
import com.alarmx.smartalarm.ui.components.SettingRow
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditAlarmScreen(
    state: EditorUiState,
    is24Hour: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onChange: ((com.alarmx.smartalarm.domain.model.Alarm) -> com.alarmx.smartalarm.domain.model.Alarm) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onOpenSound: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenVolume: () -> Unit,
    onOpenRecurrence: () -> Unit
) {
    if (!state.ready) {
        Scaffold(topBar = { TopAppBar(title = { Text("Alarm") }) }) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
        }
        return
    }
    val alarm = state.alarm
    key(alarm.id, state.isNew) {
    val timeState = rememberTimePickerState(alarm.hour, alarm.minute, is24Hour)
    LaunchedEffect(timeState.hour, timeState.minute) {
        onChange { it.copy(hour = timeState.hour, minute = timeState.minute) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Add alarm" else "Edit alarm") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                TimePicker(state = timeState, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            SectionCard {
                OutlinedTextField(
                    value = alarm.label,
                    onValueChange = { value -> onChange { it.copy(label = value) } },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Text("Repeat", style = MaterialTheme.typography.titleMedium)
                Text(alarm.recurrence.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onOpenRecurrence, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit recurrence")
                }
                if (alarm.recurrence.mode == com.alarmx.smartalarm.domain.model.RecurrenceMode.WEEKLY ||
                    alarm.recurrence.mode == com.alarmx.smartalarm.domain.model.RecurrenceMode.ONE_TIME
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = alarm.repeatDays.contains(day),
                                onClick = { onToggleDay(day) },
                                label = { Text(day.name.take(3)) }
                            )
                        }
                    }
                }
            }
            SectionCard {
                SettingRow("Enabled", checked = alarm.enabled, onCheckedChange = { v -> onChange { it.copy(enabled = v) } })
                SettingRow("Sound", SoundLabels.title(alarm.sound), onClick = onOpenSound)
                Button(onClick = onOpenSound, modifier = Modifier.fillMaxWidth()) { Text("Choose sound") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenWallpaper, modifier = Modifier.fillMaxWidth()) { Text("Alarm wallpaper") }
                Text(
                    WallpaperLabels.title(alarm.wallpaper) + " · ${alarm.wallpaperFit.name.lowercase()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenVolume, modifier = Modifier.fillMaxWidth()) { Text("Volume and booster") }
                Text(
                    "Playback ${alarm.volume.volumePercent}%" +
                        if (alarm.volume.softwareAmpEnabled) " · +${"%.1f".format(alarm.volume.softwareGainDb)} dB" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionCard {
                SettingRow("Vibration", checked = alarm.vibrate, onCheckedChange = { v -> onChange { it.copy(vibrate = v) } })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VibrationPattern.entries.filter { it != VibrationPattern.NONE }.forEach { pattern ->
                        FilterChip(
                            selected = alarm.vibrationPattern == pattern,
                            onClick = { onChange { it.copy(vibrationPattern = pattern, vibrate = true) } },
                            label = { Text(pattern.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) }
                        )
                    }
                }
            }
            SectionCard {
                Text("Snooze", style = MaterialTheme.typography.titleMedium)
                Text("${alarm.snoozeMinutes} minutes · max ${alarm.snoozeMaxCount}")
                Slider(
                    value = alarm.snoozeMinutes.toFloat(),
                    onValueChange = { v -> onChange { it.copy(snoozeMinutes = v.toInt()) } },
                    valueRange = 1f..30f
                )
                Slider(
                    value = alarm.snoozeMaxCount.toFloat(),
                    onValueChange = { v -> onChange { it.copy(snoozeMaxCount = v.toInt()) } },
                    valueRange = 0f..10f
                )
                SettingRow(
                    "Keep ringing until dismissed",
                    "Stops after ${alarm.maxRingDurationMinutes} minutes for safety",
                    checked = alarm.keepRingingUntilDismissed,
                    onCheckedChange = { v -> onChange { it.copy(keepRingingUntilDismissed = v) } }
                )
                Slider(
                    value = alarm.maxRingDurationMinutes.toFloat(),
                    onValueChange = { v -> onChange { it.copy(maxRingDurationMinutes = v.toInt()) } },
                    valueRange = 1f..30f
                )
            }
            SectionCard {
                Text("Dismiss method", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DismissMethod.entries.forEach { method ->
                        FilterChip(
                            selected = alarm.dismissMethod == method,
                            onClick = { onChange { it.copy(dismissMethod = method) } },
                            label = { Text(methodLabel(method)) }
                        )
                    }
                }
                Text(
                    "Challenges always include an emergency stop so an alarm can never trap you.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionCard {
                SettingRow(
                    "Flashlight assist",
                    if (alarm.flashlightEnabled) "Blinks while ringing if a camera flash is available" else "Off",
                    checked = alarm.flashlightEnabled,
                    onCheckedChange = { v -> onChange { it.copy(flashlightEnabled = v) } }
                )
                SettingRow(
                    "Screen flash",
                    checked = alarm.screenFlashEnabled,
                    onCheckedChange = { v -> onChange { it.copy(screenFlashEnabled = v) } }
                )
            }
            if (!state.exactAlarmsAllowed) {
                Text(
                    "Exact alarm access is off. This alarm will use Android’s best inexact fallback and may run late. Open the reliability guide to grant exact alarms.",
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Save alarm")
            }
            Text(
                "Wallpaper: ${WallpaperLabels.title(alarm.wallpaper)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    }
}

private fun methodLabel(method: DismissMethod): String = when (method) {
    DismissMethod.TAP -> "Tap"
    DismissMethod.TAP_CONFIRM -> "Confirm"
    DismissMethod.MATH -> "Math"
    DismissMethod.SHAKE -> "Shake"
}
