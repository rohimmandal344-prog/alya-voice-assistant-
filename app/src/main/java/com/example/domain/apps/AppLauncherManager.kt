package com.example.domain.apps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.domain.tools.ActionResultStatus
import com.example.domain.tools.ToolExecutionResult
import java.util.Locale
import kotlin.math.min

/**
 * Model representing an installed launchable application.
 */
data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String?,
    val normalizedLabel: String,
    val aliases: Set<String> = emptySet()
)

sealed class AppResolutionResult {
    data class Success(val app: LaunchableApp, val launchIntent: Intent) : AppResolutionResult()
    data class Ambiguous(val candidates: List<LaunchableApp>) : AppResolutionResult()
    data class NotInstalled(val requestedQuery: String) : AppResolutionResult()
    data class ExecutionFailed(val app: LaunchableApp, val reason: String) : AppResolutionResult()
}

/**
 * Decoupled, robust Android Application Launcher and Action Resolver.
 * Uses PackageManager & LauncherApps to dynamically discover, normalize,
 * fuzzy-match, and safely launch applications.
 */
class AppLauncherManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    // Common app aliases mapping to facilitate natural voice commands and misspellings
    private val knownAppAliases: Map<String, List<String>> = mapOf(
        "free fire" to listOf(
            "free fire", "freefire", "ff", "garena", "garena free fire", "free fire max",
            "free fair", "fri fair", "freefare", "freefire max", "free fire battlegrounds"
        ),
        "youtube" to listOf("youtube", "yt", "you tube", "utube", "youtub", "yutub"),
        "whatsapp" to listOf("whatsapp", "wa", "whatsap", "watsapp", "watsap", "whatapp", "whats app"),
        "instagram" to listOf("instagram", "insta", "ig", "instgram", "instaa"),
        "facebook" to listOf("facebook", "fb", "face book", "meta"),
        "chrome" to listOf("chrome", "google chrome", "browser", "web browser", "internet"),
        "camera" to listOf("camera", "cam", "photo", "photos camera", "take photo"),
        "calculator" to listOf("calculator", "calc"),
        "gallery" to listOf("gallery", "photos", "photo", "google photos", "albums", "images"),
        "dialer" to listOf("phone", "dialer", "dial", "call", "calls", "telephone"),
        "messages" to listOf("messages", "messaging", "sms", "message", "text", "texts"),
        "settings" to listOf("settings", "system settings", "device settings", "phone settings"),
        "clock" to listOf("clock", "alarm", "timer", "stopwatch", "alarms"),
        "maps" to listOf("maps", "map", "google maps", "navigation", "gps", "directions"),
        "spotify" to listOf("spotify", "music", "songs"),
        "snapchat" to listOf("snapchat", "snap"),
        "telegram" to listOf("telegram", "tg"),
        "twitter" to listOf("twitter", "x", "x app"),
        "gmail" to listOf("gmail", "google mail", "mail", "email"),
        "play store" to listOf("play store", "google play", "store", "app store", "market"),
        "drive" to listOf("drive", "google drive")
    )

    /**
     * Normalizes a raw voice or text command by stripping conversational filler words,
     * assistant invocations (e.g. "aliya tum"), and command suffixes (e.g. "kholo", "open karo").
     */
    fun normalizeCommand(raw: String): String {
        var clean = raw.lowercase(Locale.ROOT).trim()

        // 1. Strip leading assistant invocations & pronouns (e.g. "aliya", "alia tum", "tum", "aap", "tumi")
        val assistantPrefixRegex = Regex("^(?:(?:aliya|alya|alia)\\s+)?(?:(?:tum|aap|tumi|please|can\\s+you)\\s+)+", RegexOption.IGNORE_CASE)
        clean = clean.replace(assistantPrefixRegex, "").trim()

        // 2. Strip leading common command action verbs
        val prefixRegex = Regex("^(?:open|launch|start|run|go\\s+to|kholo|chalao|play|switch\\s+to)\\s+", RegexOption.IGNORE_CASE)
        clean = clean.replace(prefixRegex, "").trim()

        // 3. Strip trailing filler words & regional action suffixes (e.g. "kholo", "chalao", "open karo", "chalu karo")
        val suffixRegex = Regex("\\s+(?:app|application|kholo|chalao|open\\s+karo|open\\s+koro|chalu\\s+karo|chalu\\s+koro|start\\s+karo|khol|khulun|kholey\\s+dao|now|please|kholey|dao)$", RegexOption.IGNORE_CASE)
        clean = clean.replace(suffixRegex, "").trim()

        // 4. Fallback check: if assistant name is still at the start, remove it
        clean = clean.replace(Regex("^(?:aliya|alya|alia)\\s+", RegexOption.IGNORE_CASE), "").trim()

        return clean
    }

    /**
     * Collapses spaces and non-alphanumeric characters for fuzzy token comparison.
     */
    fun simplifyForComparison(text: String): String {
        return text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    }

    /**
     * Dynamically discovers all installed launchable applications on the Android device.
     */
    fun getInstalledLaunchableApps(): List<LaunchableApp> {
        val appMap = mutableMapOf<String, LaunchableApp>()

        // 1. Primary discovery: Intent.ACTION_MAIN + CATEGORY_LAUNCHER
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList: List<ResolveInfo> = try {
            packageManager.queryIntentActivities(launcherIntent, 0)
        } catch (e: Exception) {
            emptyList()
        }

        for (resolveInfo in resolveList) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim() ?: pkg
            val activity = resolveInfo.activityInfo?.name
            val norm = simplifyForComparison(label)

            // Associate aliases if label or pkg matches known app keywords
            val matchedAliases = mutableSetOf<String>()
            for ((key, aliases) in knownAppAliases) {
                if (norm.contains(simplifyForComparison(key)) || pkg.contains(simplifyForComparison(key))) {
                    matchedAliases.addAll(aliases)
                }
            }

            appMap[pkg] = LaunchableApp(
                label = label,
                packageName = pkg,
                activityName = activity,
                normalizedLabel = norm,
                aliases = matchedAliases
            )
        }

        // 2. Secondary discovery via LauncherApps (API 21+) for complete profile & work profile coverage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                if (launcherApps != null) {
                    val activityList = launcherApps.getActivityList(null, Process.myUserHandle())
                    for (activityInfo in activityList) {
                        val pkg = activityInfo.applicationInfo.packageName
                        if (!appMap.containsKey(pkg)) {
                            val label = activityInfo.label?.toString()?.trim() ?: pkg
                            val norm = simplifyForComparison(label)
                            val matchedAliases = mutableSetOf<String>()
                            for ((key, aliases) in knownAppAliases) {
                                if (norm.contains(simplifyForComparison(key)) || pkg.contains(simplifyForComparison(key))) {
                                    matchedAliases.addAll(aliases)
                                }
                            }
                            appMap[pkg] = LaunchableApp(
                                label = label,
                                packageName = pkg,
                                activityName = activityInfo.name,
                                normalizedLabel = norm,
                                aliases = matchedAliases
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("AppLauncherManager", "LauncherApps scan note: ${e.message}")
            }
        }

        // 3. Fallback: inspect installed applications with getLaunchIntentForPackage
        try {
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val pkg = app.packageName
                if (!appMap.containsKey(pkg) && packageManager.getLaunchIntentForPackage(pkg) != null) {
                    val label = packageManager.getApplicationLabel(app).toString().trim()
                    val norm = simplifyForComparison(label)
                    val matchedAliases = mutableSetOf<String>()
                    for ((key, aliases) in knownAppAliases) {
                        if (norm.contains(simplifyForComparison(key)) || pkg.contains(simplifyForComparison(key))) {
                            matchedAliases.addAll(aliases)
                        }
                    }
                    appMap[pkg] = LaunchableApp(
                        label = label,
                        packageName = pkg,
                        activityName = null,
                        normalizedLabel = norm,
                        aliases = matchedAliases
                    )
                }
            }
        } catch (e: Exception) { android.util.Log.e("Alya", "Exception handled", e) }

        return appMap.values.toList()
    }

    /**
     * Resolves the user's requested application query using multi-tier matching:
     * exact label → package name → normalized label → known aliases → safe fuzzy matching.
     */
    fun resolveApp(requestedAppName: String): AppResolutionResult {
        val raw = requestedAppName.trim()
        val query = normalizeCommand(raw)
        val querySimplified = simplifyForComparison(query)

        if (querySimplified.isEmpty()) {
            return AppResolutionResult.NotInstalled(raw)
        }

        val installedApps = getInstalledLaunchableApps()

        // 1. Exact Label Match (case-insensitive)
        val exactLabelMatches = installedApps.filter { it.label.equals(query, ignoreCase = true) }
        if (exactLabelMatches.size == 1) {
            val app = exactLabelMatches.first()
            return buildLaunchResult(app)
        } else if (exactLabelMatches.size > 1) {
            return AppResolutionResult.Ambiguous(exactLabelMatches)
        }

        // 2. Exact Package Name or Suffix Match
        val packageMatches = installedApps.filter {
            it.packageName.equals(query, ignoreCase = true) ||
            it.packageName.endsWith(".$query", ignoreCase = true)
        }
        if (packageMatches.size == 1) {
            return buildLaunchResult(packageMatches.first())
        }

        // 3. Normalized Label Match (ignoring spaces & punctuation)
        // e.g., "free fire" -> "freefire" matches app with label "Free Fire"
        val normalizedMatches = installedApps.filter { it.normalizedLabel == querySimplified }
        if (normalizedMatches.size == 1) {
            return buildLaunchResult(normalizedMatches.first())
        } else if (normalizedMatches.size > 1) {
            return AppResolutionResult.Ambiguous(normalizedMatches)
        }

        // 4. Known Aliases Match
        // Look through user query aliases, e.g. "free fire" -> aliases include "ff", "garena free fire", "free fire max"
        val aliasMatches = installedApps.filter { app ->
            app.aliases.any { alias ->
                simplifyForComparison(alias) == querySimplified ||
                querySimplified == simplifyForComparison(alias)
            }
        }
        if (aliasMatches.size == 1) {
            return buildLaunchResult(aliasMatches.first())
        } else if (aliasMatches.size > 1) {
            // If one candidate is an exact normalized subset or exact base version, check if exact match exists
            val exactBase = aliasMatches.firstOrNull { it.normalizedLabel == querySimplified }
            if (exactBase != null) {
                return buildLaunchResult(exactBase)
            }
            return AppResolutionResult.Ambiguous(aliasMatches)
        }

        // 5. Package Contains Substring Match (e.g., com.dts.freefireth contains "freefire")
        val pkgContainsMatches = installedApps.filter {
            simplifyForComparison(it.packageName).contains(querySimplified)
        }
        if (pkgContainsMatches.size == 1) {
            return buildLaunchResult(pkgContainsMatches.first())
        } else if (pkgContainsMatches.size > 1) {
            return AppResolutionResult.Ambiguous(pkgContainsMatches)
        }

        // 6. Label StartsWith or Contains Match
        val labelContainsMatches = installedApps.filter {
            it.normalizedLabel.contains(querySimplified) || querySimplified.contains(it.normalizedLabel)
        }
        if (labelContainsMatches.size == 1) {
            return buildLaunchResult(labelContainsMatches.first())
        } else if (labelContainsMatches.size > 1) {
            // Check if one starts with query exactly
            val startsWith = labelContainsMatches.filter { it.normalizedLabel.startsWith(querySimplified) }
            if (startsWith.size == 1) {
                return buildLaunchResult(startsWith.first())
            }
            return AppResolutionResult.Ambiguous(labelContainsMatches)
        }

        // 7. Safe Fuzzy Matching via Levenshtein Distance
        // Tolerates typos, phonetics, and transcription mistakes (e.g. "free fair", "watsapp")
        val fuzzyMatches = mutableListOf<Pair<LaunchableApp, Int>>()
        for (app in installedApps) {
            val dist = computeLevenshteinDistance(querySimplified, app.normalizedLabel)
            val maxLen = maxOf(querySimplified.length, app.normalizedLabel.length)
            // Allow up to 2 edits for words >= 5 characters, or 1 edit for 4 characters
            val threshold = if (maxLen >= 6) 2 else if (maxLen >= 4) 1 else 0
            if (dist <= threshold) {
                fuzzyMatches.add(Pair(app, dist))
            } else {
                // Also test against aliases
                for (alias in app.aliases) {
                    val aliasDist = computeLevenshteinDistance(querySimplified, simplifyForComparison(alias))
                    if (aliasDist <= threshold) {
                        fuzzyMatches.add(Pair(app, aliasDist))
                        break
                    }
                }
            }
        }

        if (fuzzyMatches.isNotEmpty()) {
            val bestScore = fuzzyMatches.minOf { it.second }
            val bestMatches = fuzzyMatches.filter { it.second == bestScore }.map { it.first }.distinctBy { it.packageName }
            if (bestMatches.size == 1) {
                return buildLaunchResult(bestMatches.first())
            } else {
                return AppResolutionResult.Ambiguous(bestMatches)
            }
        }

        return AppResolutionResult.NotInstalled(raw)
    }

    /**
     * Builds and validates the explicit or package launch Intent for an application.
     */
    private fun buildLaunchResult(app: LaunchableApp): AppResolutionResult {
        var intent: Intent? = packageManager.getLaunchIntentForPackage(app.packageName)

        if (intent == null && app.activityName != null) {
            intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
            }
        }

        if (intent == null) {
            return AppResolutionResult.ExecutionFailed(app, "No launchable intent found for package.")
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return AppResolutionResult.Success(app, intent)
    }

    /**
     * Executes opening of the resolved application, rigorously verifying Android OS execution
     * and returning the mandatory structured result.
     */
    fun launchApplication(appNameOrCommand: String): ToolExecutionResult {
        Log.i("OpenAppLifecycle", "[LIFECYCLE_VERIFY] Starting multi-tier resolution for '$appNameOrCommand'")
        val resolution = resolveApp(appNameOrCommand)

        return when (resolution) {
            is AppResolutionResult.Success -> {
                val app = resolution.app
                val intent = resolution.launchIntent
                Log.i("OpenAppLifecycle", "[LIFECYCLE_VERIFY] App resolved successfully: '${app.label}' (${app.packageName})")

                try {
                    // Pre-verify that Android can resolve the activity
                    if (intent.resolveActivity(packageManager) == null) {
                        Log.w("OpenAppLifecycle", "[LIFECYCLE_EXECUTE] Pre-verification FAILED: Intent cannot be resolved by PackageManager for '${app.label}'")
                        return ToolExecutionResult(
                            success = false,
                            message = "${app.label} is installed, but Android could not launch it.",
                            status = ActionResultStatus.FAILED,
                            errorReason = "Intent could not be resolved by PackageManager.",
                            targetAppOrFeature = app.label
                        )
                    }

                    // Perform real Android execution
                    Log.i("OpenAppLifecycle", "[LIFECYCLE_EXECUTE] Launching activity for '${app.label}' via context.startActivity()")
                    context.startActivity(intent)

                    com.example.domain.actions.DeviceContextManager.getInstance(context).updateForegroundApp(app.packageName, app.label)

                    ToolExecutionResult(
                        success = true,
                        message = "Opening ${app.label}.",
                        status = ActionResultStatus.SUCCESS,
                        output = "Successfully launched ${app.label} (${app.packageName}).",
                        targetAppOrFeature = app.label
                    )
                } catch (e: ActivityNotFoundException) {
                    ToolExecutionResult(
                        success = false,
                        message = "${app.label} is installed, but Android could not launch it.",
                        status = ActionResultStatus.FAILED,
                        errorReason = "ActivityNotFoundException: ${e.message}",
                        targetAppOrFeature = app.label
                    )
                } catch (e: SecurityException) {
                    ToolExecutionResult(
                        success = false,
                        message = "${app.label} requires special system permissions to open.",
                        status = ActionResultStatus.PERMISSION_REQUIRED,
                        errorReason = "SecurityException: ${e.message}",
                        targetAppOrFeature = app.label
                    )
                } catch (e: Exception) {
                    ToolExecutionResult(
                        success = false,
                        message = "${app.label} is installed, but Android could not launch it.",
                        status = ActionResultStatus.FAILED,
                        errorReason = "Execution exception: ${e.message}",
                        targetAppOrFeature = app.label
                    )
                }
            }

            is AppResolutionResult.Ambiguous -> {
                val appNames = resolution.candidates.map { it.label }.distinct()
                val promptList = appNames.joinToString(", ")
                ToolExecutionResult(
                    success = false,
                    message = "I found more than one matching app. Which one should I open? ($promptList)",
                    status = ActionResultStatus.AMBIGUOUS,
                    candidates = appNames,
                    targetAppOrFeature = appNameOrCommand
                )
            }

            is AppResolutionResult.NotInstalled -> {
                // Natural not-installed response according to specification
                val clean = normalizeCommand(resolution.requestedQuery)
                val display = if (clean.isNotBlank()) clean.replaceFirstChar { it.uppercase() } else resolution.requestedQuery
                ToolExecutionResult(
                    success = false,
                    message = "I couldn't find $display installed on this phone.",
                    status = ActionResultStatus.NOT_INSTALLED,
                    errorReason = "Application not present in PackageManager or LauncherApps.",
                    targetAppOrFeature = display
                )
            }

            is AppResolutionResult.ExecutionFailed -> {
                ToolExecutionResult(
                    success = false,
                    message = "${resolution.app.label} is installed, but Android could not launch it.",
                    status = ActionResultStatus.FAILED,
                    errorReason = resolution.reason,
                    targetAppOrFeature = resolution.app.label
                )
            }
        }
    }

    /**
     * Standard Levenshtein Distance for fuzzy string matching.
     */
    private fun computeLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,      // deletion
                    min(
                        dp[i][j - 1] + 1,  // insertion
                        dp[i - 1][j - 1] + cost // substitution
                    )
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
