package com.alarmx.smartalarm.updates

data class ReleaseManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String = "",
    val minSdk: Int = 26,
    val packageName: String = "com.alarmx.smartalarm"
)

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class Available(val release: ReleaseManifest) : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
    data object NotConfigured : UpdateCheckResult()
}

sealed class UpdateDownloadResult {
    data class Progress(val percent: Int) : UpdateDownloadResult()
    data class Ready(val filePath: String) : UpdateDownloadResult()
    data class Failed(val message: String) : UpdateDownloadResult()
    data object Cancelled : UpdateDownloadResult()
}
