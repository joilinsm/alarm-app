package com.alarmx.smartalarm.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.alarmx.smartalarm.audio.BundledCatalog
import com.alarmx.smartalarm.audio.BundledWallpaper
import com.alarmx.smartalarm.data.repo.UserFilesStore
import com.alarmx.smartalarm.domain.model.WallpaperFit
import com.alarmx.smartalarm.domain.model.WallpaperRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
fun WallpaperBackdrop(
    wallpaper: WallpaperRef,
    overlay: Float,
    fit: WallpaperFit = WallpaperFit.CROP,
    userFile: File? = null,
    bundled: BundledWallpaper = BundledCatalog.wallpaper(
        (wallpaper as? WallpaperRef.Bundled)?.id ?: WallpaperRef.DEFAULT_ID
    ),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(wallpaper.storageKey, userFile?.absolutePath) {
        mutableStateOf<Bitmap?>(null)
    }
    LaunchedEffect(wallpaper.storageKey, userFile?.absolutePath) {
        bitmap = withContext(Dispatchers.IO) {
            val file = userFile ?: (wallpaper as? WallpaperRef.UserFile)?.let {
                UserFilesStore(context).wallpaperFile(it.fileName)
            }
            if (file != null && file.isFile) decodeSampled(file, 1080, 1920) else null
        }
    }
    Box(modifier = modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScaleFor(fit),
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(bundled.colors))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = overlay))
        )
    }
}

fun contentScaleFor(fit: WallpaperFit): ContentScale = when (fit) {
    WallpaperFit.CROP -> ContentScale.Crop
    WallpaperFit.FIT -> ContentScale.Fit
    WallpaperFit.CENTER -> ContentScale.Inside
}

fun decodeSampled(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
        if (inSampleSize < 1) inSampleSize = 1
        val total = (width / inSampleSize) * (height / inSampleSize)
        val maxPixels = reqWidth * reqHeight * 2
        while (total / (inSampleSize * inSampleSize) > maxPixels) {
            inSampleSize *= 2
        }
    }
    return max(1, inSampleSize)
}
