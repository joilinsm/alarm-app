package com.alarmx.smartalarm.ui.ringing

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.audio.BundledCatalog
import com.alarmx.smartalarm.domain.logic.MathChallenge
import com.alarmx.smartalarm.domain.logic.SnoozeCalculator
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.DismissMethod
import com.alarmx.smartalarm.domain.model.WallpaperRef
import com.alarmx.smartalarm.ui.components.WallpaperBackdrop
import com.alarmx.smartalarm.util.TimeFormat
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import kotlin.math.sqrt

@Composable
fun AlarmRingingRoute(
    app: AlarmXApp,
    fallbackAlarmId: Long,
    fallbackScheduledAt: Long,
    fallbackIsSnooze: Boolean,
    fallbackSnoozeCount: Int,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val active by app.container.activeAlarmStore.active.collectAsState()
    var alarm by remember { mutableStateOf<Alarm?>(null) }
    LaunchedEffect(active, fallbackAlarmId) {
        alarm = active?.alarm ?: app.container.alarmRepository.getById(fallbackAlarmId)
    }
    val current = alarm ?: return
    val files = app.container.userFilesStore
    val wallpaperFile = (current.wallpaper as? WallpaperRef.UserFile)?.let { files.wallpaperFile(it.fileName) }
    val videoFile = (current.wallpaper as? WallpaperRef.VideoFile)?.let { files.videoFile(it.fileName) }
    val fallbackFile = (current.fallbackWallpaper as? WallpaperRef.UserFile)?.let { files.wallpaperFile(it.fileName) }
    AlarmRingingScreen(
        alarm = current,
        wallpaperPath = wallpaperFile?.absolutePath,
        videoPath = videoFile?.absolutePath,
        fallbackWallpaperPath = fallbackFile?.absolutePath,
        isSnooze = active?.isSnooze ?: fallbackIsSnooze,
        snoozeCount = active?.snoozeCount ?: fallbackSnoozeCount,
        playbackError = active?.playbackError,
        onSnooze = onSnooze,
        onDismiss = onDismiss
    )
}

@Composable
fun AlarmRingingScreen(
    alarm: Alarm,
    wallpaperPath: String?,
    videoPath: String? = null,
    fallbackWallpaperPath: String? = null,
    isSnooze: Boolean,
    snoozeCount: Int,
    playbackError: String? = null,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(250)
        }
    }
    val bundled = BundledCatalog.wallpaper(
        (alarm.wallpaper as? WallpaperRef.Bundled)?.id ?: WallpaperRef.DEFAULT_ID
    )
    val flashAlpha by animateFloatAsState(
        targetValue = if (alarm.screenFlashEnabled && (now.nano / 1_000_000) < 180) 0.28f else 0f,
        label = "flash"
    )
    var confirm by remember { mutableStateOf(false) }
    var math by remember { mutableStateOf(MathChallenge.generate()) }
    var mathInput by remember { mutableStateOf("") }
    var mathError by remember { mutableStateOf(false) }
    var shakeProgress by remember { mutableFloatStateOf(0f) }
    var emergencyClicks by remember { mutableIntStateOf(0) }
    var videoFailed by remember(alarm.wallpaper.storageKey) { mutableStateOf(false) }

    if (alarm.dismissMethod == DismissMethod.SHAKE) {
        ShakeCollector(sensitivity = 13f) { amount ->
            shakeProgress = (shakeProgress + amount).coerceAtMost(1f)
            if (shakeProgress >= 1f) onDismiss()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (alarm.wallpaper is WallpaperRef.VideoFile && !videoFailed && videoPath != null) {
            com.alarmx.smartalarm.ui.components.AlarmVideoBackdrop(
                videoFile = java.io.File(videoPath),
                overlay = alarm.overlayDarkness,
                fit = alarm.wallpaperFit,
                audioMode = alarm.videoAudioMode,
                videoVolumePercent = alarm.volume.videoVolumePercent,
                fallback = alarm.fallbackWallpaper,
                fallbackUserFile = fallbackWallpaperPath?.let { java.io.File(it) },
                playing = true,
                onVideoFailed = { videoFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            WallpaperBackdrop(
                wallpaper = if (videoFailed) alarm.fallbackWallpaper else alarm.wallpaper,
                overlay = alarm.overlayDarkness,
                fit = alarm.wallpaperFit,
                userFile = when {
                    videoFailed -> fallbackWallpaperPath?.let { java.io.File(it) }
                    else -> wallpaperPath?.let { java.io.File(it) }
                },
                bundled = bundled,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = flashAlpha))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (isSnooze) "Snooze $snoozeCount" else "Alarm",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(12.dp))
            Text(
                alarm.label,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                TimeFormat.clockWithSeconds(now, true),
                color = Color.White,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                modifier = Modifier.semantics { contentDescription = "Current time" }
            )
            Text(
                TimeFormat.dateLine(now),
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.titleMedium
            )
            if (!playbackError.isNullOrBlank()) {
                Text(playbackError, color = Color(0xFFFFB4A8), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))

            when (alarm.dismissMethod) {
                DismissMethod.MATH -> {
                    Text("Solve to dismiss", color = Color.White)
                    Text(math.prompt, color = Color.White, style = MaterialTheme.typography.displayMedium)
                    OutlinedTextField(
                        value = mathInput,
                        onValueChange = { mathInput = it.filter { ch -> ch.isDigit() || ch == '-' } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = mathError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (mathInput.toIntOrNull() == math.answer) {
                                onDismiss()
                            } else {
                                mathError = true
                                math = MathChallenge.generate()
                                mathInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Check answer") }
                }
                DismissMethod.SHAKE -> {
                    Text("Shake the phone to dismiss", color = Color.White)
                    Text("${(shakeProgress * 100).toInt()}%", color = Color.White)
                }
                DismissMethod.TAP_CONFIRM -> {
                    if (!confirm) {
                        DismissButton { confirm = true }
                    } else {
                        Text("Tap again to confirm dismiss", color = Color.White)
                        DismissButton(onDismiss)
                    }
                }
                DismissMethod.TAP -> DismissButton(onDismiss)
            }

            Spacer(Modifier.height(12.dp))
            val canSnooze = SnoozeCalculator.canSnooze(alarm, snoozeCount)
            OutlinedButton(
                onClick = onSnooze,
                enabled = canSnooze,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(if (canSnooze) "Snooze ${alarm.snoozeMinutes} min" else "No snoozes left")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                emergencyClicks += 1
                if (emergencyClicks >= 2) onDismiss()
            }) {
                Text(
                    if (emergencyClicks == 0) "Emergency stop" else "Tap again to stop",
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun DismissButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5C16C), contentColor = Color(0xFF1A1304))
    ) {
        Text("Dismiss", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ShakeCollector(sensitivity: Float, onShake: (Float) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(sensitivity) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val g = sqrt((x * x + y * y + z * z).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH
                if (g > sensitivity / 10f) {
                    onShake(((g - 1.2f) / 8f).coerceIn(0.02f, 0.2f))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { manager.unregisterListener(listener) }
    }
}
