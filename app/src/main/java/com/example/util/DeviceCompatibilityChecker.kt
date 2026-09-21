package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class SpecRequirement(
    val title: String,
    val minimum: String,
    val recommended: String,
    val deviceCurrent: String,
    val isSupported: Boolean,
    val isOptimal: Boolean,
    val details: String
)

data class CompatibilityReport(
    val osVersion: SpecRequirement,
    val ramCapacity: SpecRequirement,
    val audioHardware: SpecRequirement,
    val cpuArchitecture: SpecRequirement,
    val overlayPermission: SpecRequirement,
    val batteryOptimization: SpecRequirement,
    val overallStatus: String // "FULLY_COMPATIBLE", "COMPATIBLE", "ACTION_REQUIRED"
)

object DeviceCompatibilityChecker {

    fun checkDeviceCompatibility(context: Context): CompatibilityReport {
        val appContext = context.applicationContext

        // 1. OS Version (Min: Android 7.0 / API 24, Recommended: Android 10+ / API 29)
        val apiLevel = Build.VERSION.SDK_INT
        val osName = "Android ${Build.VERSION.RELEASE} (API $apiLevel)"
        val osSupported = apiLevel >= Build.VERSION_CODES.N
        val osOptimal = apiLevel >= Build.VERSION_CODES.Q
        val osSpec = SpecRequirement(
            title = "Android OS Version",
            minimum = "Android 7.0 (API 24)",
            recommended = "Android 10+ (API 29+)",
            deviceCurrent = osName,
            isSupported = osSupported,
            isOptimal = osOptimal,
            details = if (osOptimal) "Optimal: Full low-latency audio capture and modern foreground service APIs."
            else if (osSupported) "Compatible: Standard audio capture supported."
            else "Unsupported OS version."
        )

        // 2. RAM Capacity (Min: 2GB, Recommended: 3GB+)
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamGb = (memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0))
        val totalRamFormatted = String.format(java.util.Locale.US, "%.1f GB", totalRamGb)
        val ramSupported = totalRamGb >= 1.8
        val ramOptimal = totalRamGb >= 2.8
        val ramSpec = SpecRequirement(
            title = "RAM Capacity",
            minimum = "2.0 GB",
            recommended = "3.0 GB or higher",
            deviceCurrent = totalRamFormatted,
            isSupported = ramSupported,
            isOptimal = ramOptimal,
            details = if (ramOptimal) "Optimal: Smooth zero-lag background AI monitoring and instant speech synthesis."
            else if (ramSupported) "Compatible: Standard TFLite INT8 low-power mode active."
            else "Low memory: Aggressive duty-cycle mode enabled."
        )

        // 3. Audio & Microphone Hardware
        val pm = appContext.packageManager
        val hasMic = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val audioSpec = SpecRequirement(
            title = "Microphone & DSP Audio",
            minimum = "Hardware Microphone (16kHz PCM)",
            recommended = "Low-Latency Audio Hardware",
            deviceCurrent = if (hasMic) "Microphone Present (16kHz Zero-Alloc)" else "No Hardware Microphone",
            isSupported = hasMic,
            isOptimal = hasMic && pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY),
            details = "Powers acoustic formant detection and on-device wake-word engines."
        )

        // 4. CPU Architecture & TFLite Support
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")
        val is64Bit = Build.SUPPORTED_ABIS.any { it.contains("64") }
        val cpuSpec = SpecRequirement(
            title = "CPU & Neural Architecture",
            minimum = "ARMv7 / x86 with NEON",
            recommended = "ARM64-v8a with XNNPACK INT8",
            deviceCurrent = if (is64Bit) "64-Bit ($abis)" else "32-Bit ($abis)",
            isSupported = true,
            isOptimal = is64Bit,
            details = "Hardware-accelerated INT8 tensor operations and fast spectral filtering."
        )

        // 5. System Overlay Permission
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(appContext) else true
        val overlaySpec = SpecRequirement(
            title = "Floating Glow Orb Overlay",
            minimum = "SYSTEM_ALERT_WINDOW Granted",
            recommended = "Granted for Home Screen Pop-up",
            deviceCurrent = if (hasOverlay) "Granted" else "Permission Required",
            isSupported = hasOverlay,
            isOptimal = hasOverlay,
            details = "Allows the animated bottom glow orb to appear over home screen and other apps upon wake-up."
        )

        // 6. Battery Optimization Exemption
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) == true
        } else true

        val batterySpec = SpecRequirement(
            title = "Background Wake-Word Keep-Alive",
            minimum = "Foreground Service Active",
            recommended = "Unrestricted Battery Exemption",
            deviceCurrent = if (isIgnoringBattery) "Unrestricted (Optimized)" else "Standard Optimization",
            isSupported = true,
            isOptimal = isIgnoringBattery,
            details = "Ensures background listening remains responsive even after app is closed."
        )

        val overall = if (osSupported && ramSupported && hasMic && hasOverlay && isIgnoringBattery) {
            "FULLY_COMPATIBLE"
        } else if (osSupported && hasMic) {
            "COMPATIBLE"
        } else {
            "ACTION_REQUIRED"
        }

        return CompatibilityReport(
            osVersion = osSpec,
            ramCapacity = ramSpec,
            audioHardware = audioSpec,
            cpuArchitecture = cpuSpec,
            overlayPermission = overlaySpec,
            batteryOptimization = batterySpec,
            overallStatus = overall
        )
    }
}
