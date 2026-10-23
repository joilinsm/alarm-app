package com.alarmx.smartalarm.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.ui.components.EmptyState
import com.alarmx.smartalarm.ui.components.SectionCard
import com.alarmx.smartalarm.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onDelete: (Alarm) -> Unit,
    onDuplicate: (Alarm) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSoundTools: () -> Unit,
    onOpenReliability: () -> Unit
) {
    var menuAlarm by remember { mutableStateOf<Alarm?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AlarmX") },
                actions = {
                    IconButton(onClick = onOpenSoundTools) {
                        Icon(Icons.Outlined.GraphicEq, contentDescription = "Sound tools")
                    }
                    IconButton(onClick = onOpenReliability) {
                        Icon(Icons.Outlined.Info, contentDescription = "Reliability guide")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.semantics { contentDescription = "Add alarm" }
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard {
                    Text(TimeFormat.dateLine(state.now), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        TimeFormat.clockWithSeconds(state.now, state.is24Hour),
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    val next = state.next
                    if (next == null) {
                        Text("No upcoming alarm", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val label = if (next.isSnooze) "${next.alarm.label} · snooze" else next.alarm.label
                        Text("Next · $label", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${next.alarm.timeLabel(state.is24Hour)}  ·  ${TimeFormat.countdown(state.now, next.triggerAt)}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    if (!state.exactAlarmsAllowed) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Exact alarms are off. AlarmX cannot silently replace them with a timer. Open the reliability guide to allow exact alarms.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (state.alarms.isEmpty()) {
                item {
                    EmptyState(
                        title = "No alarms yet",
                        body = "Create an alarm, pick a sound, and test a time a minute from now."
                    )
                }
            } else {
                items(state.alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        is24Hour = state.is24Hour,
                        onClick = { onEdit(alarm.id) },
                        onLongClick = { menuAlarm = alarm },
                        onToggle = { onToggle(alarm, it) }
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
    menuAlarm?.let { alarm ->
        AlertDialog(
            onDismissRequest = { menuAlarm = null },
            title = { Text(alarm.label) },
            text = { Text("Edit, duplicate, or delete this alarm.") },
            confirmButton = {
                TextButton(onClick = { onDuplicate(alarm); menuAlarm = null }) { Text("Duplicate") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onEdit(alarm.id); menuAlarm = null }) { Text("Edit") }
                    TextButton(onClick = { onDelete(alarm); menuAlarm = null }) { Text("Delete") }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmRow(
    alarm: Alarm,
    is24Hour: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    SectionCard(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(alarm.timeLabel(is24Hour), style = MaterialTheme.typography.headlineMedium)
                Text(alarm.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    alarm.repeatDays.summary() + if (alarm.enabled) " · on" else " · off",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics {
                    contentDescription = if (alarm.enabled) "Disable ${alarm.label}" else "Enable ${alarm.label}"
                }
            )
        }
    }
}
