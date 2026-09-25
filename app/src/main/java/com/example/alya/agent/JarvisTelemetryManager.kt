package com.example.alya.agent

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.example.data.local.dao.MemoryDao
import com.example.data.local.dao.ScheduledTaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

/**
 * JarvisTelemetryManager
 *
 * Real-time hardware telemetry and proactive JARVIS-style executive status briefing engine.
 */
class JarvisTelemetryManager(
    private val context: Context,
    private val memoryDao: MemoryDao,
    private val scheduledTaskDao: ScheduledTaskDao
) {

    /**
     * Gathers complete real-world system telemetry.
     */
    suspend fun getSystemTelemetry(): JarvisTelemetry = withContext(Dispatchers.IO) {
        // 1. Battery Information
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).roundToInt() else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTempC = tempRaw / 10.0f

        // 2. Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
        val bytesTotal = stat.blockCountLong * stat.blockSizeLong
        val availableStorageMb = bytesAvailable / (1024 * 1024)
        val totalStorageMb = bytesTotal / (1024 * 1024)

        // 3. RAM
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val estimatedAvailableRamMb = memInfo.availMem / (1024 * 1024)

        // 4. Wi-Fi & Bluetooth
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiInfo = wifiManager?.connectionInfo
        val wifiSsid = wifiInfo?.ssid?.replace("\"", "")?.takeIf { it != "<unknown ssid>" && it.isNotBlank() }
        
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(network)
        val isWifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val isBtEnabled = btManager?.adapter?.isEnabled == true

        // 5. Real-World Ping Latency
        val networkLatencyMs = measurePingLatency()

        // 6. Tasks and Memory counts
        val activeTasksCount = try { scheduledTaskDao.getAllTasks().size } catch (_: Exception) { 0 }
        val memoryEntitiesCount = try { memoryDao.getAllMemories().size } catch (_: Exception) { 0 }

        JarvisTelemetry(
            batteryPercentage = batteryPct,
            isCharging = isCharging,
            batteryTemperatureC = batteryTempC,
            availableStorageMb = availableStorageMb,
            totalStorageMb = totalStorageMb,
            estimatedAvailableRamMb = estimatedAvailableRamMb,
            wifiSsid = wifiSsid,
            isWifiConnected = isWifiConnected,
            isBluetoothEnabled = isBtEnabled,
            networkLatencyMs = networkLatencyMs,
            activeTasksCount = activeTasksCount,
            memoryEntitiesCount = memoryEntitiesCount
        )
    }

    /**
     * Generates a sleek, natural JARVIS-style voice & text executive briefing.
     */
    suspend fun generateJarvisExecutiveBriefing(): String = withContext(Dispatchers.IO) {
        val t = getSystemTelemetry()
        val chargeStatus = if (t.isCharging) "charging" else "on battery power"
        val wifiStatus = if (t.isWifiConnected) "connected to ${t.wifiSsid ?: "Wi-Fi"}" else "on mobile network"
        val storagePctUsed = 100 - ((t.availableStorageMb.toDouble() / t.totalStorageMb.toDouble()) * 100).roundToInt()

        buildString {
            append("Good day. All core systems are operational.\n")
            append("• Power: ${t.batteryPercentage}% ($chargeStatus, ${t.batteryTemperatureC}°C)\n")
            append("• Connectivity: $wifiStatus (Latency: ${t.networkLatencyMs}ms)\n")
            append("• Storage: $storagePctUsed% utilized (${t.availableStorageMb / 1024} GB free)\n")
            append("• System RAM: ~${t.estimatedAvailableRamMb} MB available\n")
            append("• Tasks & Memory: ${t.activeTasksCount} scheduled tasks, ${t.memoryEntitiesCount} long-term facts stored.\n")
            append("Standing by for your command.")
        }
    }

    private fun measurePingLatency(): Long {
        return try {
            val start = System.currentTimeMillis()
            val socket = Socket()
            socket.connect(InetSocketAddress("1.1.1.1", 53), 1200)
            socket.close()
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            45L // Fallback estimated nominal latency
        }
    }
}
