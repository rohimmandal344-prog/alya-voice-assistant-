package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.OfflineNluEngine
import com.example.domain.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name resource`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Alya Assistant", appName)
    }

    @Test
    fun `verify tools registry contains essential tools`() {
        assertNotNull(ToolRegistry.findTool("open_app"))
        assertNotNull(ToolRegistry.findTool("open_settings"))
        assertNotNull(ToolRegistry.findTool("toggle_flashlight"))
        assertNotNull(ToolRegistry.findTool("create_timer"))
        assertNotNull(ToolRegistry.findTool("make_call"))
    }

    @Test
    fun `verify offline nlu parsing`() {
        val action = OfflineNluEngine.parseCommand("Turn on flashlight")
        assertNotNull(action)
        assertEquals("toggle_flashlight", action?.toolName)
        assertEquals("on", action?.parameters?.get("state"))
    }
}
