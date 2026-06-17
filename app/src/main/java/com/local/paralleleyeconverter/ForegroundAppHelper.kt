package com.local.paralleleyeconverter

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
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
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val start = end - 15000L
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTime = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val activityResumed =
                android.os.Build.VERSION.SDK_INT >= 29 &&
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            val movedToForeground =
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND || activityResumed
            if (movedToForeground && event.timeStamp >= latestTime) {
                latestPackage = event.packageName
                latestTime = event.timeStamp
            }
        }
        return latestPackage?.takeIf { it != context.packageName && it != "com.android.systemui" }
    }

    fun saveTargetPackage(context: Context, packageName: String?) {
        if (packageName.isNullOrBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TARGET_PACKAGE, packageName)
            .apply()
    }

    fun readTargetPackage(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TARGET_PACKAGE, null)
    }

    fun usageSettingsIntent(): android.content.Intent {
        return android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
