package com.alarmx.smartalarm.updates

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object UpdateValidator {
    fun parseManifest(json: String): ReleaseManifest {
        val root = JSONObject(json)
        return ReleaseManifest(
            versionCode = root.getInt("versionCode"),
            versionName = root.getString("versionName"),
            apkUrl = root.getString("apkUrl"),
            sha256 = root.getString("sha256").lowercase().trim(),
            releaseNotes = root.optString("releaseNotes", ""),
            minSdk = root.optInt("minSdk", 26),
            packageName = root.optString("packageName", "com.alarmx.smartalarm")
        )
    }

    fun isNewer(remoteCode: Int, installedCode: Int): Boolean = remoteCode > installedCode

    fun rejectDowngrade(remoteCode: Int, installedCode: Int): Boolean = remoteCode < installedCode

    fun packageMatches(expected: String, actual: String): Boolean =
        expected.equals(actual, ignoreCase = true)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun checksumMatches(file: File, expectedHex: String): Boolean {
        if (expectedHex.isBlank()) return false
        return sha256(file).equals(expectedHex.trim(), ignoreCase = true)
    }
}
