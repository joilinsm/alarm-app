package com.alarmx.smartalarm.ui.settings

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.alarmx.smartalarm.BuildConfig
import com.alarmx.smartalarm.domain.model.AppSettings
import com.alarmx.smartalarm.domain.model.TimeFormatMode
import com.alarmx.smartalarm.ui.components.SectionCard
import com.alarmx.smartalarm.ui.components.SettingRow
import com.alarmx.smartalarm.ui.updates.UpdateUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    updateState: UpdateUiState,
    onChange: (AppSettings) -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onSaveGithubToken: (String) -> Unit,
    onOpenReliability: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit
) {
    var tokenDraft by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                Text("Time format")
                TimeFormatMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.timeFormat == mode,
                        onClick = { onChange(settings.copy(timeFormat = mode)) },
                        label = { Text(mode.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
            SectionCard {
                SettingRow(
                    "Follow system theme",
                    checked = settings.followSystemTheme,
                    onCheckedChange = { onChange(settings.copy(followSystemTheme = it)) }
                )
                SettingRow(
                    "Dark theme",
                    checked = settings.useDarkTheme,
                    onCheckedChange = { onChange(settings.copy(useDarkTheme = it, followSystemTheme = false)) }
                )
            }
            SectionCard {
                Text("Default snooze ${settings.defaultSnoozeMinutes} minutes")
                Slider(
                    value = settings.defaultSnoozeMinutes.toFloat(),
                    onValueChange = { onChange(settings.copy(defaultSnoozeMinutes = it.toInt())) },
                    valueRange = 1f..30f
                )
            }
            SectionCard {
                Text("Updates", style = MaterialTheme.typography.titleMedium)
                Text("Installed ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text(
                    "Private GitHub releases need either a public HTTPS version.json proxy " +
                        "or a fine-grained token stored only on this device. Tokens are never embedded in the APK.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = settings.updateManifestUrl,
                    onValueChange = { onChange(settings.copy(updateManifestUrl = it.trim())) },
                    label = { Text("Update manifest URL (preferred)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = settings.githubOwner,
                    onValueChange = { onChange(settings.copy(githubOwner = it.trim())) },
                    label = { Text("GitHub owner") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = settings.githubRepo,
                    onValueChange = { onChange(settings.copy(githubRepo = it.trim())) },
                    label = { Text("GitHub repo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = tokenDraft,
                    onValueChange = { tokenDraft = it },
                    label = { Text(if (updateState.hasToken) "Replace GitHub token" else "GitHub token (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedButton(
                    onClick = {
                        onSaveGithubToken(tokenDraft)
                        tokenDraft = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save token on device") }
                if (updateState.hasToken) {
                    OutlinedButton(
                        onClick = { onSaveGithubToken("") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Clear saved token") }
                }
                Button(
                    onClick = onCheckUpdates,
                    enabled = !updateState.checking && !updateState.downloading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (updateState.checking) "Checking…" else "Check for updates")
                }
                updateState.available?.let { release ->
                    Text("Available: ${release.versionName} (${release.versionCode})")
                    Text(release.releaseNotes.take(400), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (updateState.downloading) {
                        LinearProgressIndicator(
                            progress = { updateState.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Downloading ${updateState.progress}%")
                    }
                    Button(
                        onClick = onDownloadUpdate,
                        enabled = !updateState.downloading,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Update now") }
                    OutlinedButton(onClick = onDismissUpdate, modifier = Modifier.fillMaxWidth()) {
                        Text("Later")
                    }
                }
            }
            SectionCard {
                SettingRow("Permissions and reliability", onClick = onOpenReliability)
                SettingRow("About and privacy", onClick = onOpenAbout)
            }
        }
    }
}
