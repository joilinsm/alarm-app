package com.alarmx.smartalarm.ui.wallpaper

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.audio.BundledCatalog
import com.alarmx.smartalarm.audio.WallpaperLabels
import com.alarmx.smartalarm.domain.model.WallpaperRef
import com.alarmx.smartalarm.ui.components.SectionCard
import com.alarmx.smartalarm.ui.components.WallpaperPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(
    selected: WallpaperRef,
    overlay: Float,
    errors: List<String>,
    onPreviewBundled: (WallpaperRef) -> Unit,
    onPreviewUser: (WallpaperRef) -> Unit,
    onPreviewVideo: (WallpaperRef) -> Unit,
    onImportImage: (Uri) -> Unit,
    onImportVideo: (Uri) -> Unit,
    onRestoreDefault: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as AlarmXApp
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportImage(uri)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportVideo(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alarm wallpapers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionCard {
                    Text("Background for this alarm only. AlarmX never changes the phone wallpaper.")
                    Text("Current: ${WallpaperLabels.title(selected)}", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { imagePicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import image") }
                    Button(
                        onClick = { videoPicker.launch(arrayOf("video/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import video") }
                    OutlinedButton(
                        onClick = onRestoreDefault,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Restore default") }
                    errors.forEach {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            items(BundledCatalog.wallpapers) { paper ->
                Column(modifier = Modifier.clickable { onPreviewBundled(paper.ref) }) {
                    WallpaperPreview(
                        wallpaper = paper,
                        overlay = overlay,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.62f)
                    )
                    Text(
                        paper.title + if (selected.storageKey == paper.ref.storageKey) " · selected" else "",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            val imported = app.container.userFilesStore.listUserWallpapers()
            items(imported) { name ->
                val ref = WallpaperRef.UserFile(name)
                Column(modifier = Modifier.clickable { onPreviewUser(ref) }) {
                    Text(
                        name + if (selected.storageKey == ref.storageKey) " · selected" else "",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text("Imported image · tap to preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val videos = app.container.userFilesStore.listUserVideos()
            items(videos) { name ->
                val ref = WallpaperRef.VideoFile(name)
                Column(modifier = Modifier.clickable { onPreviewVideo(ref) }) {
                    Text(
                        name + if (selected.storageKey == ref.storageKey) " · selected" else "",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text("Imported video · tap to preview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
