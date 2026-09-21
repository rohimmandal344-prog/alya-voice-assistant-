package com.example.domain.tools

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

data class AppSecurityInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    val isThirdPartyApp: Boolean,
    val isSideloaded: Boolean,
    val requestedPermissions: List<String>,
    val riskLevel: AppRiskLevel,
    val riskReasons: List<String>
)

enum class AppRiskLevel {
    SAFE,
    WARNING,
    HIGH_RISK
}

data class PhoneAppsScanResult(
    val totalAppsCount: Int,
    val systemAppsCount: Int,
    val thirdPartyAppsCount: Int,
    val safeAppsCount: Int,
    val warningAppsCount: Int,
    val highRiskAppsCount: Int,
    val thirdPartyAppNames: List<String>,
    val suspiciousApps: List<AppSecurityInfo>,
    val fullSummaryText: String,
    val fullSpeechText: String
)

class PhoneSecurityAppScanner(private val context: Context) {

    fun scanAllInstalledApps(): PhoneAppsScanResult {
        val pm = context.packageManager
        val installedPackages: List<PackageInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
        } catch (e: Exception) {
            emptyList()
        }

        var systemCount = 0
        var thirdPartyCount = 0
        var safeCount = 0
        var warningCount = 0
        var highRiskCount = 0

        val thirdPartyNames = mutableListOf<String>()
        val suspiciousList = mutableListOf<AppSecurityInfo>()

        for (pkg in installedPackages) {
            val appInfo = pkg.applicationInfo ?: continue
            val appName = try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg.packageName
            }
            val packageName = pkg.packageName

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val isThirdParty = !isSystem

            if (isSystem) {
                systemCount++
            } else {
                thirdPartyCount++
                thirdPartyNames.add(appName)
            }

            val permissions = pkg.requestedPermissions?.toList() ?: emptyList()
            val installerPackage = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(packageName)
                }
            } catch (_: Exception) {
                null
            }

            val isSideloaded = isThirdParty && installerPackage != "com.android.vending" && installerPackage != "com.google.android.feedback"

            val riskReasons = mutableListOf<String>()
            var riskLevel = AppRiskLevel.SAFE

            if (isSideloaded) {
                riskReasons.add("Unknown installation source (Sideloaded APK)")
                riskLevel = AppRiskLevel.WARNING
            }

            if (permissions.contains(android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE)) {
                riskReasons.add("Full Accessibility Service control")
                riskLevel = AppRiskLevel.HIGH_RISK
            }
            if (permissions.contains(android.Manifest.permission.SYSTEM_ALERT_WINDOW)) {
                riskReasons.add("Draw over other apps (Screen Overlay)")
                if (riskLevel != AppRiskLevel.HIGH_RISK) riskLevel = AppRiskLevel.WARNING
            }
            if (permissions.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES)) {
                riskReasons.add("Can install unknown application packages")
                riskLevel = AppRiskLevel.HIGH_RISK
            }
            if (permissions.contains(android.Manifest.permission.READ_SMS) || permissions.contains(android.Manifest.permission.RECEIVE_SMS)) {
                riskReasons.add("Reads confidential SMS messages")
                if (riskLevel != AppRiskLevel.HIGH_RISK) riskLevel = AppRiskLevel.WARNING
            }

            when (riskLevel) {
                AppRiskLevel.SAFE -> safeCount++
                AppRiskLevel.WARNING -> warningCount++
                AppRiskLevel.HIGH_RISK -> highRiskCount++
            }

            val appSecurity = AppSecurityInfo(
                appName = appName,
                packageName = packageName,
                versionName = pkg.versionName ?: "1.0",
                isSystemApp = isSystem,
                isThirdPartyApp = isThirdParty,
                isSideloaded = isSideloaded,
                requestedPermissions = permissions,
                riskLevel = riskLevel,
                riskReasons = riskReasons
            )

            if (riskLevel != AppRiskLevel.SAFE) {
                suspiciousList.add(appSecurity)
            }
        }

        val totalCount = systemCount + thirdPartyCount

        val summary = StringBuilder().apply {
            append("📱 **Phone App Intelligence & Security Audit Report**\n")
            append("• **Total Apps Installed:** $totalCount\n")
            append("• **System Default Apps:** $systemCount\n")
            append("• **User Installed (Third-Party) Apps:** $thirdPartyCount\n\n")
            append("🔒 **Security & Risk Assessment:**\n")
            append("• ✅ Safe Apps: $safeCount\n")
            append("• ⚠️ Low Warning / Sideloaded: $warningCount\n")
            append("• 🚨 High Risk / Sensitive Permissions: $highRiskCount\n\n")

            if (suspiciousList.isNotEmpty()) {
                append("🔍 **Flagged Applications:**\n")
                suspiciousList.take(6).forEach { app ->
                    val badge = if (app.riskLevel == AppRiskLevel.HIGH_RISK) "🚨 HIGH RISK" else "⚠️ WARNING"
                    append("• **${app.appName}** ($badge): ${app.riskReasons.joinToString(", ")}\n")
                }
            } else {
                append("✅ All installed applications passed safety checks. No harmful or suspicious apps detected.")
            }
        }.toString()

        val speech = "Alya has completed a full phone scan. Your phone has a total of $totalCount installed apps: $systemCount pre-installed system apps and $thirdPartyCount user-installed apps. Audit result: $safeCount safe apps, $warningCount low warning apps, and $highRiskCount flagged high-risk apps."

        return PhoneAppsScanResult(
            totalAppsCount = totalCount,
            systemAppsCount = systemCount,
            thirdPartyAppsCount = thirdPartyCount,
            safeAppsCount = safeCount,
            warningAppsCount = warningCount,
            highRiskAppsCount = highRiskCount,
            thirdPartyAppNames = thirdPartyNames,
            suspiciousApps = suspiciousList,
            fullSummaryText = summary,
            fullSpeechText = speech
        )
    }
}
