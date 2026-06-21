package com.local.paralleleyeconverter

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings

private const val PREFS_NAME = "converter_state"
private const val KEY_TARGET_PACKAGE = "target_package"

object ForegroundAppHelper {
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun readForegroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        return readUsageEvents(context, 120000L)
            .lastOrNull { !isSystemOrSelf(context, it.packageName) }
            ?.packageName
    }

    fun readLastTargetCandidatePackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val homePackages = readHomePackages(context)
        return readUsageEvents(context, 180000L)
            .lastOrNull { event ->
                !isSystemOrSelf(context, event.packageName) &&
                    !homePackages.contains(event.packageName)
            }
            ?.packageName
    }

    private fun readUsageEvents(context: Context, windowMs: Long): List<ForegroundEvent> {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val start = end - windowMs
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        val foregroundEvents = mutableListOf<ForegroundEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val activityResumed =
                android.os.Build.VERSION.SDK_INT >= 29 &&
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            val movedToForeground =
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || activityResumed
            if (movedToForeground && !event.packageName.isNullOrBlank()) {
                foregroundEvents.add(ForegroundEvent(event.packageName, event.timeStamp))
            }
        }
        return foregroundEvents.sortedBy { it.timeMs }
    }

    fun saveTargetPackage(context: Context, packageName: String?) {
        if (packageName.isNullOrBlank()) return
        if (isSystemOrSelf(context, packageName)) return
        if (readHomePackages(context).contains(packageName)) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TARGET_PACKAGE, packageName)
            .apply()
    }

    fun markInitialTargetPackage(context: Context) {
        saveTargetPackage(context, readLastTargetCandidatePackage(context))
    }

    fun readTargetPackage(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TARGET_PACKAGE, null)
    }

    fun usageSettingsIntent(): android.content.Intent {
        return android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun isSystemOrSelf(context: Context, packageName: String): Boolean {
        return packageName == context.packageName || packageName == "com.android.systemui"
    }

    private fun readHomePackages(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, 0)
        }
        return resolveInfos.mapNotNull { it.activityInfo?.packageName }.toSet()
    }
}

private data class ForegroundEvent(val packageName: String, val timeMs: Long)
