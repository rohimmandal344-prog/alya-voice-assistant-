package com.example.domain.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.tools.ActionResultStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocalCommandRouterTest {

    private lateinit var context: Context
    private lateinit var deviceContextManager: DeviceContextManager
    private lateinit var capabilityRegistry: AlyaCapabilityRegistry
    private lateinit var localCommandRouter: LocalCommandRouter
    private lateinit var intelligenceRouter: IntelligenceRouter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deviceContextManager = DeviceContextManager.getInstance(context)
        capabilityRegistry = AlyaCapabilityRegistry.getInstance(context)
        localCommandRouter = LocalCommandRouter(context)
        intelligenceRouter = IntelligenceRouter(context)
    }

    @Test
    fun testCapabilityRegistry_defaultCapabilitiesRegistered() {
        val allCaps = capabilityRegistry.getAllCapabilities()
        assertTrue("Capabilities should not be empty", allCaps.isNotEmpty())

        val openAppCap = capabilityRegistry.getCapability("open_app")
        assertNotNull("open_app capability should exist", openAppCap)
        assertTrue("open_app must be offline supported", openAppCap!!.offlineSupported)
        assertFalse("open_app does not require online AI", openAppCap.onlineRequired)

        val weatherCap = capabilityRegistry.getCapability("check_weather")
        assertNotNull("check_weather capability should exist", weatherCap)
        assertTrue("check_weather requires online", weatherCap!!.onlineRequired)
    }

    @Test
    fun testDeviceContextManager_foregroundTracking() {
        deviceContextManager.updateForegroundApp("com.google.android.youtube", "YouTube")
        assertEquals("com.google.android.youtube", deviceContextManager.getCurrentForegroundPackage())
        assertTrue("isAppInForeground should match youtube", deviceContextManager.isAppInForeground("youtube"))

        deviceContextManager.recordAction("open shorts", target = "Shorts", success = true, task = "YouTube Shorts")
        val state = deviceContextManager.state.value
        assertEquals("open shorts", state.lastCommand)
        assertEquals("Shorts", state.lastTargetElement)
        assertEquals("YouTube Shorts", state.lastActiveTask)
        assertTrue(state.lastExecutionSuccess)
    }

    @Test
    fun testYouTubeShortsContextualFlow() {
        // Step 1: Simulate YouTube being in foreground
        deviceContextManager.updateForegroundApp("com.google.android.youtube", "YouTube")

        // Step 2: User says "tap shorts" or "shorts"
        val result = localCommandRouter.routeAndExecute("tap shorts")
        assertNotNull("Contextual shorts command should be routed locally", result)
    }

    @Test
    fun testIntelligenceRouter_localVsOnlineRouting() {
        // Local device command routing
        val decisionVolume = intelligenceRouter.evaluateAndRoute("volume up")
        assertEquals(IntelligenceRouteType.LOCAL_DEVICE_COMMAND, decisionVolume.routeType)

        // Web search query routing
        val decisionSearch = intelligenceRouter.evaluateAndRoute("search who was Albert Einstein")
        assertEquals(IntelligenceRouteType.ONLINE_INFORMATION_SEARCH, decisionSearch.routeType)

        // Conversational AI routing
        val decisionChat = intelligenceRouter.evaluateAndRoute("Tell me a bedtime story about space exploration")
        assertEquals(IntelligenceRouteType.ONLINE_AI_REQUEST, decisionChat.routeType)
    }
}
