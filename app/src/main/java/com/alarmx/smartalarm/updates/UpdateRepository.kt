package com.alarmx.smartalarm.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.alarmx.smartalarm.BuildConfig
import com.alarmx.smartalarm.data.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import kotlin.coroutines.coroutineContext

/**
 * Private-release updates without embedding credentials in the APK.
 *
 * Order of checks:
 * 1. Optional HTTPS manifest URL (preferred: your own authenticated proxy).
 * 2. Optional user-supplied GitHub token (fine-grained, stored encrypted locally)
 *    against the private joilinsm/alarm-app releases API.
 * 3. NotConfigured when neither path is available.
 */
class UpdateRepository(
    context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val appContext = context.applicationContext
    private val tokenPrefs = appContext.getSharedPreferences("alarmx_update_secure", Context.MODE_PRIVATE)
    private val downloadDir = File(appContext.cacheDir, "updates").also { it.mkdirs() }
    private var cancelDownload = false

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    suspend fun checkForUpdates(manual: Boolean): UpdateCheckResult = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settings.value
        val now = System.currentTimeMillis()
        if (!manual && now - settings.lastUpdateCheckEpochMs < THROTTLE_MS) {
            return@withContext UpdateCheckResult.UpToDate
        }
        settingsRepository.update { it.copy(lastUpdateCheckEpochMs = now) }

        val manifestUrl = settings.updateManifestUrl.trim()
        val token = readGithubToken()
        val release = when {
            manifestUrl.isNotBlank() -> fetchManifest(manifestUrl, bearer = null)
            !token.isNullOrBlank() -> fetchLatestGithubRelease(settings.githubOwner, settings.githubRepo, token)
            else -> return@withContext UpdateCheckResult.NotConfigured
        }

        release.fold(
            onSuccess = { remote ->
                if (!UpdateValidator.packageMatches(remote.packageName, appContext.packageName)) {
                    return@withContext UpdateCheckResult.Failed("Release package does not match this app.")
                }
                if (UpdateValidator.rejectDowngrade(remote.versionCode, BuildConfig.VERSION_CODE)) {
                    return@withContext UpdateCheckResult.Failed("Remote release is older than the installed build.")
                }
                if (UpdateValidator.isNewer(remote.versionCode, BuildConfig.VERSION_CODE)) {
                    UpdateCheckResult.Available(remote)
                } else {
                    UpdateCheckResult.UpToDate
                }
            },
            onFailure = { error ->
                UpdateCheckResult.Failed(error.message ?: "Update check failed.")
            }
        )
    }

    suspend fun download(release: ReleaseManifest): UpdateDownloadResult = withContext(Dispatchers.IO) {
        cancelDownload = false
        _progress.value = 0
        val out = File(downloadDir, "alarmx-${release.versionCode}.apk")
        try {
            val connection = openConnection(release.apkUrl, readGithubToken())
            connection.connect()
            if (connection.responseCode !in 200..299) {
                return@withContext UpdateDownloadResult.Failed("Download HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.coerceAtLeast(1L)
            connection.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    while (coroutineContext.isActive) {
                        if (cancelDownload) {
                            out.delete()
                            return@withContext UpdateDownloadResult.Cancelled
                        }
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        readTotal += read
                        _progress.value = ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                    }
                }
            }
            if (!UpdateValidator.checksumMatches(out, release.sha256)) {
                out.delete()
                return@withContext UpdateDownloadResult.Failed("SHA-256 checksum mismatch. Download rejected.")
            }
            UpdateDownloadResult.Ready(out.absolutePath)
        } catch (error: Exception) {
            out.delete()
            Log.e(TAG, "Download failed", error)
            UpdateDownloadResult.Failed(error.message ?: "Download failed.")
        }
    }

    fun cancelDownload() {
        cancelDownload = true
    }

    fun launchInstaller(apkPath: String): Boolean {
        val file = File(apkPath)
        if (!file.isFile) return false
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            appContext.startActivity(intent)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Installer launch failed", error)
            false
        }
    }

    fun saveGithubToken(raw: String?) {
        if (raw.isNullOrBlank()) {
            tokenPrefs.edit().remove(KEY_TOKEN).remove(KEY_IV).apply()
            return
        }
        val encrypted = encrypt(raw.trim())
        tokenPrefs.edit()
            .putString(KEY_TOKEN, encrypted.first)
            .putString(KEY_IV, encrypted.second)
            .apply()
    }

    fun hasGithubToken(): Boolean = !readGithubToken().isNullOrBlank()

    private fun readGithubToken(): String? {
        val cipherText = tokenPrefs.getString(KEY_TOKEN, null) ?: return null
        val iv = tokenPrefs.getString(KEY_IV, null) ?: return null
        return runCatching { decrypt(cipherText, iv) }.getOrNull()
    }

    private fun fetchManifest(url: String, bearer: String?): Result<ReleaseManifest> {
        return try {
            val body = httpGet(url, bearer)
            Result.success(UpdateValidator.parseManifest(body))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun fetchLatestGithubRelease(owner: String, repo: String, token: String): Result<ReleaseManifest> {
        return try {
            val api = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val body = httpGet(api, token)
            val root = JSONObject(body)
            val tag = root.optString("tag_name", "")
            val notes = root.optString("body", "")
            val assets = root.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            var shaAssetUrl: String? = null
            var versionMetaUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                val url = asset.optString("url")
                when {
                    name.endsWith(".apk", ignoreCase = true) -> apkUrl = url
                    name.equals("SHA256SUMS", ignoreCase = true) || name.endsWith(".sha256", true) ->
                        shaAssetUrl = url
                    name.equals("version.json", ignoreCase = true) -> versionMetaUrl = url
                }
            }
            if (apkUrl == null) {
                return Result.failure(IllegalStateException("Latest release has no APK asset."))
            }
            val meta = if (versionMetaUrl != null) {
                UpdateValidator.parseManifest(httpGet(versionMetaUrl, token))
            } else {
                val code = tag.filter { it.isDigit() }.takeLast(4).toIntOrNull()
                    ?: BuildConfig.VERSION_CODE + 1
                ReleaseManifest(
                    versionCode = code,
                    versionName = tag.removePrefix("v"),
                    apkUrl = apkUrl,
                    sha256 = "",
                    releaseNotes = notes
                )
            }
            val sha = if (meta.sha256.isNotBlank()) {
                meta.sha256
            } else if (shaAssetUrl != null) {
                httpGet(shaAssetUrl, token).lineSequence().firstOrNull()
                    ?.substringBefore(" ")
                    ?.trim()
                    .orEmpty()
            } else {
                ""
            }
            if (sha.isBlank()) {
                return Result.failure(
                    IllegalStateException(
                        "Release is missing SHA-256 metadata (version.json or SHA256SUMS)."
                    )
                )
            }
            Result.success(
                meta.copy(
                    apkUrl = apkUrl,
                    sha256 = sha,
                    releaseNotes = notes.ifBlank { meta.releaseNotes }
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun httpGet(url: String, bearer: String?): String {
        val connection = openConnection(url, bearer)
        connection.connect()
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode} for $url")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun openConnection(url: String, bearer: String?): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream, application/json")
            setRequestProperty("User-Agent", "AlarmX/${BuildConfig.VERSION_NAME}")
            if (!bearer.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearer")
            }
        }
        return connection
    }

    private fun encrypt(plain: String): Pair<String, String> {
        val key = deviceKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP) to Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    private fun decrypt(cipherText: String, ivText: String): String {
        val key = deviceKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(ivText, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP))
        return String(plain, Charsets.UTF_8)
    }

    private fun deviceKey(): SecretKey {
        val existing = tokenPrefs.getString(KEY_AES, null)
        if (existing != null) {
            val raw = Base64.decode(existing, Base64.NO_WRAP)
            return javax.crypto.spec.SecretKeySpec(raw, "AES")
        }
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(128)
        val key = keyGen.generateKey()
        tokenPrefs.edit().putString(KEY_AES, Base64.encodeToString(key.encoded, Base64.NO_WRAP)).apply()
        return key
    }

    companion object {
        private const val TAG = "AlarmXUpdate"
        private const val THROTTLE_MS = 6L * 60L * 60L * 1000L
        private const val KEY_TOKEN = "gh_token"
        private const val KEY_IV = "gh_iv"
        private const val KEY_AES = "aes_key"
    }
}
