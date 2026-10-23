package com.alarmx.smartalarm.ui.sound

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.alarmx.smartalarm.audio.BundledCatalog
import com.alarmx.smartalarm.audio.SoundLabels
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.SoundRef
import com.alarmx.smartalarm.domain.model.VolumeSettings
import com.alarmx.smartalarm.playback.AlarmAudioController
import com.alarmx.smartalarm.playback.PlaybackStartResult
import com.alarmx.smartalarm.ui.components.SectionCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundScreen(
    selected: SoundRef,
    errors: List<String>,
    onSelect: (SoundRef) -> Unit,
    onImport: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AlarmXApp
    val controller = remember { AlarmAudioController(context, app.container.userFilesStore) }
    val scope = rememberCoroutineScope()
    var previewing by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    DisposableEffect(Unit) {
        onDispose { controller.stop() }
    }

    fun preview(sound: SoundRef) {
        scope.launch {
            controller.stop()
            previewError = null
            previewing = true
            val dummy = Alarm(
                hour = 8,
                minute = 0,
                sound = sound,
                volume = VolumeSettings(gradualEnabled = false, volumePercent = 60)
            )
            when (val result = controller.start(dummy, scope)) {
                is PlaybackStartResult.Started -> Unit
                is PlaybackStartResult.Failed -> {
                    previewing = false
                    previewError = result.message
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarm sounds") },
                navigationIcon = {
                    IconButton(onClick = { controller.stop(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard {
                    Text("Preview plays the exact file that will be saved. Tap Save alarm after you choose.")
                    Text("Saved: ${SoundLabels.title(selected)}", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Import audio file")
                    }
                    OutlinedButton(
                        onClick = { controller.stop(); previewing = false },
                        enabled = previewing,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Stop preview") }
                    (previewError ?: errors.firstOrNull())?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                SoundRow(
                    SoundLabels.title(SoundRef.SystemDefault),
                    SoundLabels.detail(SoundRef.SystemDefault),
                    selected is SoundRef.SystemDefault
                ) {
                    onSelect(SoundRef.SystemDefault)
                    preview(SoundRef.SystemDefault)
                }
            }
            items(BundledCatalog.tones) { tone ->
                SoundRow(tone.title, tone.description, selected.storageKey == tone.ref.storageKey) {
                    onSelect(tone.ref)
                    preview(tone.ref)
                }
            }
            val userSounds = app.container.userFilesStore.listUserSounds()
            items(userSounds) { name ->
                val ref = SoundRef.UserFile(name)
                SoundRow(name, "Imported", selected.storageKey == ref.storageKey) {
                    onSelect(ref)
                    preview(ref)
                }
            }
        }
    }
}

@Composable
private fun SoundRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Text(title + if (selected) " · selected" else "", style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
