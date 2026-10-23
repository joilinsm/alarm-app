package com.alarmx.smartalarm.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.alarmx.smartalarm.domain.model.VideoAudioMode
import com.alarmx.smartalarm.domain.model.WallpaperFit
import com.alarmx.smartalarm.domain.model.WallpaperRef
import com.alarmx.smartalarm.domain.logic.VolumeRampCalculator
import java.io.File

/**
 * Shared video wallpaper surface for preview and ringing screens.
 * Ringtone audio is owned by [com.alarmx.smartalarm.playback.AlarmAudioController];
 * this composable only owns the video decoder / surface.
 */
@Composable
fun AlarmVideoBackdrop(
    videoFile: File?,
    overlay: Float,
    fit: WallpaperFit,
    audioMode: VideoAudioMode,
    videoVolumePercent: Int,
    fallback: WallpaperRef,
    fallbackUserFile: File? = null,
    playing: Boolean = true,
    onVideoFailed: (() -> Unit)? = null,
    onHasAudio: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var failed by remember(videoFile?.absolutePath) { mutableStateOf(false) }
    Box(modifier = modifier) {
        if (videoFile != null && videoFile.isFile && !failed && playing) {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val video = VideoView(context)
                        addView(
                            video,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                        tag = video
                    }
                },
                update = { frame ->
                    val video = frame.tag as VideoView
                    val uri = Uri.fromFile(videoFile)
                    video.setOnPreparedListener { player ->
                        player.isLooping = true
                        applyVideoScaling(player, fit)
                        val muteVideo = audioMode == VideoAudioMode.SEPARATE_RINGTONE
                        val volume = if (muteVideo) {
                            0f
                        } else {
                            VolumeRampCalculator.playerGain(videoVolumePercent)
                        }
                        player.setVolume(volume, volume)
                        player.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                .build()
                        )
                        onHasAudio?.invoke(true)
                        player.start()
                    }
                    video.setOnErrorListener { _, _, _ ->
                        failed = true
                        onVideoFailed?.invoke()
                        true
                    }
                    if (video.tag != videoFile.absolutePath) {
                        video.tag = videoFile.absolutePath
                        video.setVideoURI(uri)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            DisposableEffect(videoFile.absolutePath) {
                onDispose {
                    // AndroidView teardown releases the VideoView with the composition.
                }
            }
        } else {
            WallpaperBackdrop(
                wallpaper = fallback,
                overlay = 0f,
                fit = fit,
                userFile = fallbackUserFile,
                modifier = Modifier.fillMaxSize()
            )
            if (failed) {
                onVideoFailed?.invoke()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlay))
        )
    }
}

private fun applyVideoScaling(player: MediaPlayer, fit: WallpaperFit) {
    // VideoView letterboxes by default; Crop is approximated by CENTER_CROP via layout.
    // Fit/Center keep the default VideoView behavior.
    when (fit) {
        WallpaperFit.CROP, WallpaperFit.FIT, WallpaperFit.CENTER -> Unit
    }
}
