package com.alarmx.smartalarm.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.alarmx.smartalarm.domain.logic.VolumeRampCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.max

class SoftwareAmplifier {
    data class PlaybackSession(
        val track: AudioTrack,
        val pcm: ShortArray,
        val sampleRate: Int,
        val channels: Int
    )

    suspend fun prepare(
        context: Context,
        uri: Uri,
        gainDb: Float
    ): PlaybackSession? = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var track: AudioTrack? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            if (durationUs > MAX_DURATION_US) return@withContext null

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val pcm = decodeToPcm(extractor, codec, channels)
            runCatching { codec.stop() }
            codec.release()
            codec = null
            if (pcm.isEmpty()) return@withContext null

            val gain = VolumeRampCalculator.softwareLinearGain(gainDb)
            applyGain(pcm, gain)

            val channelConfig = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(channelConfig)
                .build()
            track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(max(minBuf, pcm.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setLoopPoints(0, pcm.size / channels, -1)
            PlaybackSession(track, pcm, sampleRate, channels).also { track = null }
        } catch (_: Exception) {
            track?.release()
            null
        } finally {
            extractor.release()
            if (codec != null) {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
        }
    }

    private fun decodeToPcm(
        extractor: MediaExtractor,
        codec: MediaCodec,
        channels: Int
    ): ShortArray {
        val chunks = ArrayList<Short>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var totalSamples = 0
        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex) ?: break
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                val outBuf = codec.getOutputBuffer(outIndex)
                if (outBuf != null && info.size > 0) {
                    val shorts = bufferToShorts(outBuf, info.size)
                    totalSamples += shorts.size
                    if (totalSamples > MAX_SAMPLES) {
                        codec.releaseOutputBuffer(outIndex, false)
                        break
                    }
                    shorts.forEach { chunks.add(it) }
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }
        return chunks.toShortArray()
    }

    private fun bufferToShorts(buffer: ByteBuffer, size: Int): ShortArray {
        val count = size / 2
        val result = ShortArray(count)
        buffer.asShortBuffer().get(result, 0, count)
        return result
    }

    private fun applyGain(pcm: ShortArray, gain: Float) {
        for (i in pcm.indices) {
            val sample = pcm[i] / 32768f
            val limited = VolumeRampCalculator.limitSample(sample * gain)
            pcm[i] = (limited * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    companion object {
        private const val MAX_DURATION_US = 60L * 1_000_000L
        private const val MAX_SAMPLES = 48_000 * 2 * 45
    }
}
