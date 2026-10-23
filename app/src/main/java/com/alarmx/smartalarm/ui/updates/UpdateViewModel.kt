package com.alarmx.smartalarm.ui.updates

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alarmx.smartalarm.AlarmXApp
import com.alarmx.smartalarm.updates.ReleaseManifest
import com.alarmx.smartalarm.updates.UpdateCheckResult
import com.alarmx.smartalarm.updates.UpdateDownloadResult
import com.alarmx.smartalarm.updates.UpdateNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val available: ReleaseManifest? = null,
    val message: String? = null,
    val hasToken: Boolean = false
)

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AlarmXApp
    private val updates = app.container.updateRepository
    private val _state = MutableStateFlow(UpdateUiState(hasToken = updates.hasGithubToken()))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(checking = true, message = null) }
            when (val result = updates.checkForUpdates(manual)) {
                is UpdateCheckResult.Available -> {
                    val settings = app.container.settingsRepository.settings.value
                    if (result.release.versionCode != settings.lastNotifiedVersionCode) {
                        UpdateNotifier.notifyAvailable(app, result.release)
                        app.container.settingsRepository.update {
                            it.copy(lastNotifiedVersionCode = result.release.versionCode)
                        }
                    }
                    _state.update {
                        it.copy(
                            checking = false,
                            available = result.release,
                            message = if (manual) "Update ${result.release.versionName} is available." else null
                        )
                    }
                }
                UpdateCheckResult.UpToDate -> {
                    _state.update {
                        it.copy(
                            checking = false,
                            available = null,
                            message = if (manual) "You are on the latest build." else null
                        )
                    }
                }
                UpdateCheckResult.NotConfigured -> {
                    _state.update {
                        it.copy(
                            checking = false,
                            message = if (manual) {
                                "Configure an update manifest URL or a GitHub token in Settings."
                            } else null
                        )
                    }
                }
                is UpdateCheckResult.Failed -> {
                    _state.update {
                        it.copy(checking = false, message = if (manual) result.message else null)
                    }
                }
            }
        }
    }

    fun downloadAndInstall() {
        val release = _state.value.available ?: return
        viewModelScope.launch {
            _state.update { it.copy(downloading = true, progress = 0, message = null) }
            val collector = launch {
                updates.progress.collect { value ->
                    _state.update { it.copy(progress = value) }
                }
            }
            when (val result = updates.download(release)) {
                is UpdateDownloadResult.Ready -> {
                    val launched = updates.launchInstaller(result.filePath)
                    _state.update {
                        it.copy(
                            downloading = false,
                            message = if (launched) {
                                "Confirm the Android install prompt. Allow installs from AlarmX if asked."
                            } else {
                                "Downloaded, but the installer could not be opened. Enable unknown-app installs for AlarmX."
                            }
                        )
                    }
                }
                is UpdateDownloadResult.Failed -> {
                    _state.update { it.copy(downloading = false, message = result.message) }
                }
                UpdateDownloadResult.Cancelled -> {
                    _state.update { it.copy(downloading = false, message = "Download cancelled.") }
                }
                is UpdateDownloadResult.Progress -> Unit
            }
            collector.cancel()
        }
    }

    fun dismissAvailable() {
        _state.update { it.copy(available = null) }
    }

    fun saveGithubToken(token: String) {
        updates.saveGithubToken(token.ifBlank { null })
        _state.update {
            it.copy(
                hasToken = updates.hasGithubToken(),
                message = if (token.isBlank()) "GitHub token cleared." else "GitHub token saved on this device only."
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
