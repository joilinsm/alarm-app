package com.alarmx.smartalarm.ui.soundtools

import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.audiofx.Equalizer
import android.widget.Toast
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmx.smartalarm.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val audioManager = context.getSystemService(AudioManager::class.java)
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    var mediaVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var bands by remember { mutableStateOf(listOf<Short>()) }
    var eqSupported by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            equalizer?.release()
            player?.release()
        }
    }

    fun stopPreview() {
        equalizer?.release()
        equalizer = null
        player?.release()
        player = null
        bands = emptyList()
    }

    fun startPreview() {
        stopPreview()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        val mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(context, uri)
            isLooping = true
            prepare()
            start()
        }
        player = mediaPlayer
        try {
            val eq = Equalizer(0, mediaPlayer.audioSessionId)
            eq.enabled = true
            equalizer = eq
            bands = (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()) }
            eqSupported = true
        } catch (error: Exception) {
            eqSupported = false
            Toast.makeText(context, "Equalizer is not available on this device.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media sound tools") },
                navigationIcon = {
                    IconButton(onClick = { stopPreview(); onBack() }) {
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
                Text("These controls only change media volume. Alarm volume is independent and is not modified here.")
                Text("Media volume $mediaVolume / $max")
                Slider(
                    value = mediaVolume.toFloat(),
                    onValueChange = {
                        val value = it.toInt().coerceIn(0, max)
                        mediaVolume = value
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                    },
                    valueRange = 0f..max.toFloat().coerceAtLeast(1f)
                )
            }
            SectionCard {
                Text("Optional equalizer", style = MaterialTheme.typography.titleMedium)
                Text("Starts only while a preview is playing. It is released when you leave this screen.")
                Button(onClick = { startPreview() }, modifier = Modifier.fillMaxWidth()) { Text("Start media preview") }
                OutlinedButton(onClick = { stopPreview() }, modifier = Modifier.fillMaxWidth()) { Text("Stop preview") }
                if (!eqSupported) {
                    Text("Equalizer APIs are unavailable. Volume control still works.", color = MaterialTheme.colorScheme.error)
                }
                val eq = equalizer
                if (eq != null) {
                    val range = eq.bandLevelRange
                    bands.forEachIndexed { index, level ->
                        val freq = eq.getCenterFreq(index.toShort()) / 1000
                        Text("Band $freq Hz · $level")
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { value ->
                                val next = value.toInt().toShort()
                                eq.setBandLevel(index.toShort(), next)
                                bands = bands.toMutableList().also { it[index] = next }
                            },
                            valueRange = range[0].toFloat()..range[1].toFloat()
                        )
                    }
                }
            }
        }
    }
}
