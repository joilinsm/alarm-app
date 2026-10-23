package com.alarmx.smartalarm.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateValidatorTest {
    @Test
    fun parsesManifestAndComparesVersions() {
        val json = """
            {
              "versionCode": 3,
              "versionName": "1.2.0",
              "apkUrl": "https://example.com/app.apk",
              "sha256": "abc",
              "releaseNotes": "fixes",
              "packageName": "com.alarmx.smartalarm"
            }
        """.trimIndent()
        val manifest = UpdateValidator.parseManifest(json)
        assertEquals(3, manifest.versionCode)
        assertTrue(UpdateValidator.isNewer(3, 2))
        assertFalse(UpdateValidator.isNewer(2, 2))
        assertTrue(UpdateValidator.rejectDowngrade(1, 2))
        assertTrue(UpdateValidator.packageMatches("com.alarmx.smartalarm", "com.alarmx.smartalarm"))
        assertFalse(UpdateValidator.packageMatches("other", "com.alarmx.smartalarm"))
    }

    @Test
    fun checksumRejectsMismatch() {
        val file = File.createTempFile("alarmx", ".bin")
        file.writeText("hello")
        val hash = UpdateValidator.sha256(file)
        assertTrue(UpdateValidator.checksumMatches(file, hash))
        assertFalse(UpdateValidator.checksumMatches(file, "deadbeef"))
        file.delete()
    }
}
