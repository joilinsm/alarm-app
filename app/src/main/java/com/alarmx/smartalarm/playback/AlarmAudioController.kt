package com.alarmx.smartalarm.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.alarmx.smartalarm.data.repo.UserFilesStore
import com.alarmx.smartalarm.domain.logic.SoundPlaybackMessages
import com.alarmx.smartalarm.domain.logic.VolumeRampCalculator
import com.alarmx.smartalarm.domain.model.Alarm
import com.alarmx.smartalarm.domain.model.SoundRef
import com.alarmx.smartalarm.domain.model.VideoAudioMode
import com.alarmx.smartalarm.domain.model.WallpaperRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

sealed class PlaybackStartResult {
    data object Started : PlaybackStartResult()
    data class Failed(val message: String, val soundKey: String, val cause: String?) : PlaybackStartResult()
}

class AlarmAudioController(
    private val context: Context,
    private val filesStore: UserFilesStore
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val amplifier = SoftwareAmplifier()
    private var mediaPlayer: MediaPlayer? = null
    private var soundStream: FileInputStream? = null
    private var ampSession: SoftwareAmplifier.PlaybackSession? = null
    private var rampJob: Job? = null
    var isPlaying: Boolean = false
        private set
    var activeSoundKey: String? = null
        private set

    val maxAlarmVolume: Int get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    val currentAlarmVolume: Int get() = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

    suspend fun start(alarm: Alarm, scope: CoroutineScope): PlaybackStartResult {
        if (isPlaying) {
            Log.w(TAG, "Ignoring duplicate start for ${alarm.id}; already playing $activeSoundKey")
            return PlaybackStartResult.Started
        }
        stop()
        isPlaying = true
        activeSoundKey = alarm.sound.storageKey
        val settings = alarm.volume.sanitized()
        val wantsRingtone = shouldPlayRingtone(alarm)
        if (!wantsRingtone) {
            // Video surface owns alarm audio in VIDEO_AUDIO mode.
            Log.i(TAG, "Alarm ${alarm.id} uses video audio only; ringtone player idle")
            return PlaybackStartResult.Started
        }

        val ringtonePercent = if (alarm.wallpaper is WallpaperRef.VideoFile &&
            alarm.videoAudioMode == VideoAudioMode.VIDEO_AND_RINGTONE
        ) {
            settings.ringtoneVolumePercent
        } else {
            settings.volumePercent
        }
        val startPercent = if (settings.gradualEnabled) settings.rampStartPercent else ringtonePercent
        val targetPercent = if (settings.gradualEnabled) settings.rampTargetPercent else ringtonePercent

        val resolved = resolvePlayable(alarm.sound)
        if (resolved == null) {
            val message = SoundPlaybackMessages.missingSound(alarm.sound)
            Log.e(TAG, "Cannot play ${alarm.sound.storageKey} for alarm ${alarm.id}: $message")
            isPlaying = false
            activeSoundKey = null
            return PlaybackStartResult.Failed(message, alarm.sound.storageKey, "unresolved")
        }

        return try {
            var usedAmp = false
            if (settings.softwareAmpEnabled && settings.softwareGainDb > 0.05f) {
                val session = amplifier.prepare(context, resolved.uri, settings.softwareGainDb)
                if (session != null) {
                    ampSession = session
                    session.track.setVolume(VolumeRampCalculator.playerGain(startPercent))
                    session.track.play()
                    usedAmp = true
                    Log.i(TAG, "Alarm ${alarm.id} playing ${alarm.sound.storageKey} via software gain ${settings.softwareGainDb} dB")
                } else {
                    Log.w(TAG, "Software gain unavailable for ${alarm.sound.storageKey}; using the same file without extra dB")
                }
            }
            if (!usedAmp) {
                startMediaPlayer(resolved, alarm.keepRingingUntilDismissed)
                applyPlayerGain(VolumeRampCalculator.playerGain(startPercent))
                Log.i(TAG, "Alarm ${alarm.id} playing ${alarm.sound.storageKey} via MediaPlayer at ${startPercent}%")
            }
            startRamp(scope, settings.gradualEnabled, startPercent, targetPercent, settings.rampDurationMs)
            PlaybackStartResult.Started
        } catch (error: Exception) {
            Log.e(TAG, "Playback failed for alarm ${alarm.id} sound ${alarm.sound.storageKey}", error)
            stop()
            PlaybackStartResult.Failed(
                "The selected sound could not be played.",
                alarm.sound.storageKey,
                error.message
            )
        }
    }

    fun shouldPlayRingtone(alarm: Alarm): Boolean {
        if (alarm.wallpaper !is WallpaperRef.VideoFile) return true
        return when (alarm.videoAudioMode) {
            VideoAudioMode.SEPARATE_RINGTONE -> true
            VideoAudioMode.VIDEO_AUDIO -> false
            VideoAudioMode.VIDEO_AND_RINGTONE -> true
        }
    }

    fun stop() {
        rampJob?.cancel()
        rampJob = null
        releasePlayers()
        isPlaying = false
        activeSoundKey = null
    }

    private fun releasePlayers() {
        try {
            mediaPlayer?.setOnCompletionListener(null)
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        mediaPlayer?.release()
        mediaPlayer = null
        runCatching { soundStream?.close() }
        soundStream = null
        try {
            ampSession?.track?.pause()
            ampSession?.track?.flush()
            ampSession?.track?.stop()
        } catch (_: Exception) {
        }
        ampSession?.track?.release()
        ampSession = null
    }

    private suspend fun startMediaPlayer(source: ResolvedSound, loop: Boolean) {
        val player = withContext(Dispatchers.IO) {
            MediaPlayer().also { created ->
                try {
                    created.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    created.isLooping = loop
                    if (source.file != null) {
                        val stream = FileInputStream(source.file)
                        soundStream = stream
                        created.setDataSource(stream.fd)
                    } else {
                        created.setDataSource(context, source.uri)
                    }
                    created.prepare()
                } catch (error: Exception) {
                    created.release()
                    runCatching { soundStream?.close() }
                    soundStream = null
                    throw error
                }
            }
        }
        if (!loop) {
            player.setOnCompletionListener { stop() }
        }
        player.start()
        mediaPlayer = player
    }

    private fun startRamp(
        scope: CoroutineScope,
        enabled: Boolean,
        start: Int,
        target: Int,
        durationMs: Int
    ) {
        rampJob?.cancel()
        if (!enabled || durationMs <= 0 || start >= target) {
            applyPlayerGain(VolumeRampCalculator.playerGain(target))
            return
        }
        val startedAt = System.currentTimeMillis()
        rampJob = scope.launch {
            while (isActive && isPlaying) {
                val elapsed = System.currentTimeMillis() - startedAt
                applyPlayerGain(VolumeRampCalculator.rampedPlayerGain(start, target, elapsed, durationMs))
                if (elapsed >= durationMs) break
                delay(80)
            }
        }
    }

    private fun applyPlayerGain(gain: Float) {
        val safe = gain.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(safe, safe)
        ampSession?.track?.setVolume(safe)
    }

    data class ResolvedSound(val uri: Uri, val file: File?)

    fun resolvePlayable(sound: SoundRef): ResolvedSound? {
        return when (sound) {
            is SoundRef.SystemDefault -> {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                uri?.let { ResolvedSound(it, null) }
            }
            is SoundRef.Bundled -> {
                val resId = context.resources.getIdentifier(sound.rawName, "raw", context.packageName)
                if (resId == 0) {
                    Log.e(TAG, "Bundled raw resource missing: ${sound.rawName}")
                    null
                } else {
                    ResolvedSound(Uri.parse("android.resource://${context.packageName}/$resId"), null)
                }
            }
            is SoundRef.UserFile -> {
                val file = filesStore.soundFile(sound.fileName)
                if (!file.isFile) {
                    Log.e(TAG, "Imported sound missing: ${sound.fileName}")
                    null
                } else {
                    ResolvedSound(Uri.fromFile(file), file)
                }
            }
        }
    }

    fun userSoundMissing(sound: SoundRef): Boolean {
        return sound is SoundRef.UserFile && !filesStore.soundExists(sound.fileName)
    }

    companion object {
        private const val TAG = "AlarmXAudio"
    }
}
