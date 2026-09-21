package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateManagerTest {

    @Test
    fun testUpdateJsonParsing_newerVersion() {
        val sampleJson = """
            {
                "versionCode": 4,
                "versionName": "1.2.0",
                "apkUrl": "https://example.com/downloads/alya-v1.2.0.apk",
                "releaseNotes": "Added in-app updater and performance optimizations."
            }
        """.trimIndent()

        val jsonObject = JSONObject(sampleJson)
        val versionCode = jsonObject.getLong("versionCode")
        val versionName = jsonObject.getString("versionName")
        val apkUrl = jsonObject.getString("apkUrl")
        val releaseNotes = jsonObject.optString("releaseNotes")

        val currentVersionCode = 1L
        val isUpdateAvailable = versionCode > currentVersionCode

        val info = AppUpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            apkUrl = apkUrl,
            releaseNotes = releaseNotes,
            isUpdateAvailable = isUpdateAvailable,
            currentVersionCode = currentVersionCode,
            currentVersionName = "1.0.0"
        )

        assertTrue("Newer version code should trigger update availability", info.isUpdateAvailable)
        assertEquals(4L, info.versionCode)
        assertEquals("1.2.0", info.versionName)
        assertEquals("https://example.com/downloads/alya-v1.2.0.apk", info.apkUrl)
        assertEquals("Added in-app updater and performance optimizations.", info.releaseNotes)
    }

    @Test
    fun testUpdateJsonParsing_sameOrOlderVersion() {
        val sampleJson = """
            {
                "versionCode": 1,
                "versionName": "1.0.0",
                "apkUrl": "https://example.com/downloads/alya-v1.0.0.apk"
            }
        """.trimIndent()

        val jsonObject = JSONObject(sampleJson)
        val versionCode = jsonObject.getLong("versionCode")
        val currentVersionCode = 1L
        val isUpdateAvailable = versionCode > currentVersionCode

        assertFalse("Same version should not trigger update", isUpdateAvailable)
    }

    @Test
    fun testAppUpdateManager_versionReporting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AppUpdateManager(context)
        val versionName = manager.getCurrentVersionName()
        val versionCode = manager.getCurrentVersionCode()

        assertTrue("Version name should not be blank", versionName.isNotBlank())
        assertTrue("Version code should be >= 0", versionCode >= 0L)
    }
}
