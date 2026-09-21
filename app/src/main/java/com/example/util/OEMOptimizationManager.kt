package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

enum class OEMType {
    SAMSUNG, ONEPLUS, REALME, REDMI, XIAOMI, OPPO, VIVO, GOOGLE, UNKNOWN
}

data class OEMOptimizationGuide(
    val oemType: OEMType,
    val manufacturerName: String,
    val steps: List<String>,
    val intentAction: String? = null,
    val intentPackage: String? = null,
    val intentClass: String? = null
)

object OEMOptimizationManager {

    fun getDeviceOEM(): OEMType {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("samsung") -> OEMType.SAMSUNG
            manufacturer.contains("oneplus") -> OEMType.ONEPLUS
            manufacturer.contains("realme") -> OEMType.REALME
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> OEMType.XIAOMI
            manufacturer.contains("oppo") -> OEMType.OPPO
            manufacturer.contains("vivo") -> OEMType.VIVO
            manufacturer.contains("google") -> OEMType.GOOGLE
            else -> OEMType.UNKNOWN
        }
    }

    fun getOptimizationGuide(): OEMOptimizationGuide {
        return when (getDeviceOEM()) {
            OEMType.SAMSUNG -> OEMOptimizationGuide(
                OEMType.SAMSUNG, "Samsung",
                listOf(
                    "Open Settings -> Apps -> Alya.",
                    "Tap 'Battery' -> Select 'Unrestricted'.",
                    "Go to 'App battery usage' and ensure 'Allow background activity' is ON.",
                    "Ensure Alya is NOT in 'Deep sleeping apps' in Device Care."
                )
            )
            OEMType.XIAOMI, OEMType.REDMI -> OEMOptimizationGuide(
                OEMType.XIAOMI, "Xiaomi / Redmi",
                listOf(
                    "Open Settings -> Apps -> Manage Apps -> Alya.",
                    "Enable 'Autostart'.",
                    "Go to 'Battery Saver' -> Select 'No restrictions'.",
                    "In Recent Apps, long press Alya and tap the Lock icon."
                )
            )
            OEMType.OPPO, OEMType.REALME -> OEMOptimizationGuide(
                OEMType.OPPO, "Oppo / Realme",
                listOf(
                    "Open Settings -> App Management -> Alya.",
                    "Tap 'Battery' -> Enable 'Allow background activity'.",
                    "Enable 'Allow auto-launch'.",
                    "Go to Battery Settings -> App Battery Management -> Alya -> Enable 'Allow background activity'."
                )
            )
            OEMType.VIVO -> OEMOptimizationGuide(
                OEMType.VIVO, "Vivo",
                listOf(
                    "Open Settings -> Battery -> High background power consumption.",
                    "Find Alya and enable it.",
                    "Go to Settings -> More settings -> Applications -> Autostart -> Enable Alya.",
                    "In Recent Apps, swipe down on Alya to lock it."
                )
            )
            OEMType.ONEPLUS -> OEMOptimizationGuide(
                OEMType.ONEPLUS, "OnePlus",
                listOf(
                    "Open Settings -> Battery -> Battery optimization.",
                    "Find Alya -> Select 'Don't optimize'.",
                    "Go to Settings -> Apps & notifications -> Special app access -> Battery optimization.",
                    "Ensure 'Advanced optimization' is OFF in Battery settings."
                )
            )
            else -> OEMOptimizationGuide(
                OEMType.UNKNOWN, Build.MANUFACTURER.capitalize(),
                listOf(
                    "Go to App Info for Alya.",
                    "Set Battery Usage to 'Unrestricted'.",
                    "Enable 'Autostart' or 'Background Activity' if available.",
                    "Disable any 'Battery Saver' modes for Alya."
                )
            )
        }
    }

    fun openOptimizationSettings(context: Context) {
        val oem = getDeviceOEM()
        try {
            when (oem) {
                OEMType.XIAOMI, OEMType.REDMI -> {
                    val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                    intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    intent.putExtra("extra_pkgname", context.packageName)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
                OEMType.SAMSUNG -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
                OEMType.ONEPLUS -> {
                    try {
                        val intent = Intent().apply {
                            setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                }
                OEMType.VIVO -> {
                    try {
                        val intent = Intent().apply {
                            setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                }
                OEMType.OPPO, OEMType.REALME -> {
                    try {
                        val intent = Intent().apply {
                            setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                }
                else -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            Log.e("OEMOptimization", "Failed to open specific settings: ${e.message}")
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (ex: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }
            }
        }
    }
}
