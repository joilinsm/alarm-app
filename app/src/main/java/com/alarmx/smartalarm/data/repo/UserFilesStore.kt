package com.alarmx.smartalarm.data.repo

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class VideoImportResult(
    val fileName: String,
    val hasAudioTrack: Boolean
)

class UserFilesStore(context: Context) {
    private val appContext = context.applicationContext
    val wallpapersDir: File = File(appContext.filesDir, "wallpapers").also { it.mkdirs() }
    val videosDir: File = File(appContext.filesDir, "videos").also { it.mkdirs() }
    val soundsDir: File = File(appContext.filesDir, "sounds").also { it.mkdirs() }

    suspend fun importWallpaper(uri: Uri): Result<String> =
        copyUri(uri, wallpapersDir, "wp", validateImage = true, maxBytes = MAX_IMAGE_BYTES)

    suspend fun importVideo(uri: Uri): Result<VideoImportResult> = withContext(Dispatchers.IO) {
        copyUri(uri, videosDir, "vid", validateVideo = true, maxBytes = MAX_VIDEO_BYTES).map { name ->
            VideoImportResult(name, videoHasAudio(videoFile(name)))
        }
    }

    suspend fun importSound(uri: Uri): Result<String> =
        copyUri(uri, soundsDir, "snd", validateAudio = true, maxBytes = MAX_IMAGE_BYTES)

    fun wallpaperFile(fileName: String): File = File(wallpapersDir, fileName)

    fun videoFile(fileName: String): File = File(videosDir, fileName)

    fun soundFile(fileName: String): File = File(soundsDir, fileName)

    fun wallpaperExists(fileName: String): Boolean = wallpaperFile(fileName).isFile

    fun videoExists(fileName: String): Boolean = videoFile(fileName).isFile

    fun soundExists(fileName: String): Boolean = soundFile(fileName).isFile

    fun deleteWallpaper(fileName: String) {
        wallpaperFile(fileName).delete()
    }

    fun deleteVideo(fileName: String) {
        videoFile(fileName).delete()
    }

    fun listUserWallpapers(): List<String> {
        return wallpapersDir.listFiles()?.map { it.name }?.sorted().orEmpty()
    }

    fun listUserVideos(): List<String> {
        return videosDir.listFiles()?.map { it.name }?.sorted().orEmpty()
    }

    fun listUserSounds(): List<String> {
        return soundsDir.listFiles()?.map { it.name }?.sorted().orEmpty()
    }

    fun videoHasAudio(file: File): Boolean {
        if (!file.isFile) return false
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } catch (error: Exception) {
            Log.e(TAG, "videoHasAudio failed: ${error.message}")
            false
        } finally {
            extractor.release()
        }
    }

    fun canDecodeAudio(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } catch (error: Exception) {
            Log.e(TAG, "Cannot decode audio ${file.name}: ${error.message}")
            false
        } finally {
            extractor.release()
        }
    }

    private suspend fun copyUri(
        uri: Uri,
        dir: File,
        prefix: String,
        validateImage: Boolean = false,
        validateAudio: Boolean = false,
        validateVideo: Boolean = false,
        maxBytes: Long
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val type = resolver.getType(uri).orEmpty()
            val ext = extensionFor(type, uri)
            val name = "${prefix}_${UUID.randomUUID().toString().take(12)}.$ext"
            val outFile = File(dir, name)
            try {
                resolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > maxBytes) {
                                outFile.delete()
                                return@withContext Result.failure(
                                    IllegalArgumentException(
                                        if (validateVideo) "That video is larger than 40 MB."
                                        else "That file is larger than 15 MB."
                                    )
                                )
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: return@withContext Result.failure(
                    IllegalArgumentException("Android could not open the selected file.")
                )
                if (outFile.length() == 0L) {
                    outFile.delete()
                    return@withContext Result.failure(IllegalArgumentException("The selected file was empty."))
                }
                if (validateAudio && !canDecodeAudio(outFile)) {
                    outFile.delete()
                    return@withContext Result.failure(
                        IllegalArgumentException("That audio format is not supported on this device.")
                    )
                }
                if (validateImage && !canDecodeImage(outFile)) {
                    outFile.delete()
                    return@withContext Result.failure(
                        IllegalArgumentException("That image could not be decoded.")
                    )
                }
                if (validateVideo && !canDecodeVideo(outFile)) {
                    outFile.delete()
                    return@withContext Result.failure(
                        IllegalArgumentException("That video could not be decoded on this device.")
                    )
                }
                Result.success(name)
            } catch (error: Exception) {
                outFile.delete()
                Log.e(TAG, "Import failed: ${error.message}")
                Result.failure(error)
            }
        }
    }

    private fun canDecodeImage(file: File): Boolean {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun canDecodeVideo(file: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            hasVideo && duration > 0L
        } catch (error: Exception) {
            Log.e(TAG, "Cannot decode video ${file.name}: ${error.message}")
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extensionFor(mime: String, uri: Uri): String {
        val fromMime = when {
            mime.contains("png") -> "png"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("webp") -> "webp"
            mime.contains("mp4") -> "mp4"
            mime.contains("webm") -> "webm"
            mime.contains("3gpp") -> "3gp"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("wav") -> "wav"
            mime.contains("ogg") -> "ogg"
            mime.contains("aac") -> "aac"
            mime.contains("video") -> "mp4"
            else -> null
        }
        if (fromMime != null) return fromMime
        val path = uri.lastPathSegment.orEmpty()
        val dot = path.lastIndexOf('.')
        return if (dot >= 0) path.substring(dot + 1).lowercase().take(4) else "dat"
    }

    companion object {
        private const val TAG = "AlarmXFiles"
        private const val MAX_IMAGE_BYTES = 15L * 1024L * 1024L
        private const val MAX_VIDEO_BYTES = 40L * 1024L * 1024L
    }
}
