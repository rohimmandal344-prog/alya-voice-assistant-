package com.example

import com.example.data.ai.OfflineNluEngine
import com.example.domain.tools.ToolRegistry
import com.example.domain.tools.ToolRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAndVoiceCommandTest {

    @Test
    fun testToolRegistryContainsWeatherTool() {
        val weatherTool = ToolRegistry.findTool("check_weather")
        assertNotNull("Weather tool must be registered in ToolRegistry", weatherTool)
        assertEquals("check_weather", weatherTool?.name)
        assertEquals(ToolRiskLevel.LOW, weatherTool?.riskLevel)
        assertEquals(false, weatherTool?.defaultRequiresConfirmation)
    }

    @Test
    fun testWeatherVoiceCommandsParsing() {
        val testCases = listOf(
            "check local weather" to "local",
            "what is the weather in Tokyo" to "Tokyo",
            "weather report" to "local",
            "how is the weather in London" to "London",
            "what's the temperature in Paris" to "Paris",
            "mausam kaisa hai" to "local",
            "will it rain today" to "local"
        )

        for ((input, expectedLocation) in testCases) {
            val action = OfflineNluEngine.parseCommand(input)
            assertNotNull("Command '$input' should parse into an action", action)
            assertEquals("Tool name for '$input' should be check_weather", "check_weather", action?.toolName)
            val actualLoc = action?.parameters?.get("location")?.lowercase() ?: ""
            assertEquals("Location for '$input' should match", expectedLocation.lowercase(), actualLoc)
        }
    }

    @Test
    fun testLocalWeatherKeywordsDetection() {
        val extraWeatherQueries = listOf(
            "local weather report",
            "check local weather now",
            "weather forecast",
            "forecast in New York",
            "current temperature"
        )
        for (query in extraWeatherQueries) {
            val action = OfflineNluEngine.parseCommand(query)
            assertNotNull("Query '$query' should be recognized as a weather action", action)
            assertEquals("check_weather", action?.toolName)
        }
    }

    @Test
    fun testRestartPhoneCommand() {
        val restartQueries = listOf("restart phone", "reboot phone", "restart mobile", "turn off phone", "power off phone")
        for (query in restartQueries) {
            val action = OfflineNluEngine.parseCommand(query)
            assertNotNull("Query '$query' should be recognized as restart_phone action", action)
            assertEquals("restart_phone", action?.toolName)
        }
    }

    @Test
    fun testLocalDeviceControlRegistryHasOfflineHandlers() {
        val registry = com.example.domain.tools.LocalDeviceControlRegistry
        assertTrue("Registry must have handler for control_volume", registry.hasHandler("control_volume"))
        assertTrue("Registry must have handler for control_brightness", registry.hasHandler("control_brightness"))
        assertTrue("Registry must have handler for toggle_flashlight", registry.hasHandler("toggle_flashlight"))
        assertTrue("Registry must have handler for toggle_wifi", registry.hasHandler("toggle_wifi"))
        assertTrue("Registry must have handler for toggle_bluetooth", registry.hasHandler("toggle_bluetooth"))
        assertTrue("Registry must have handler for go_home", registry.hasHandler("go_home"))
        assertTrue("Registry must have handler for open_camera", registry.hasHandler("open_camera"))
    }

    @Test
    fun testBrightnessCommandsParsing() {
        val brightnessUpQueries = listOf("brightness up", "increase screen brightness", "brighten screen")
        for (query in brightnessUpQueries) {
            val action = OfflineNluEngine.parseCommand(query)
            assertNotNull("Query '$query' should parse to structured action", action)
            assertEquals("control_brightness", action?.toolName)
            assertEquals("up", action?.parameters?.get("action"))
        }

        val brightnessDownQueries = listOf("brightness down", "decrease screen brightness", "dim screen", "screen dimmer")
        for (query in brightnessDownQueries) {
            val action = OfflineNluEngine.parseCommand(query)
            assertNotNull("Query '$query' should parse to structured action", action)
            assertEquals("control_brightness", action?.toolName)
            assertEquals("down", action?.parameters?.get("action"))
        }

        val actionSet = OfflineNluEngine.parseCommand("brightness 75%")
        assertNotNull(actionSet)
        assertEquals("control_brightness", actionSet?.toolName)
        assertEquals("set", actionSet?.parameters?.get("action"))
        assertEquals("75", actionSet?.parameters?.get("level"))
    }
}
