package com.example.service

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

/**
 * Works out which app is in front WITHOUT the accessibility service (it is switched off during a
 * Banking Pause). Uses UsageStatsManager, which needs "Usage access" (Settings > Special app access).
 */
object ForegroundProbe {

    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    } catch (e: Exception) {
        false
    }

    /** Package of the newest "activity came to the foreground" event in [fromMs, toMs], or null if none. */
    @Suppress("DEPRECATION")
    fun latestResumedPackage(context: Context, fromMs: Long, toMs: Long): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(fromMs, toMs) ?: return null
            val ev = UsageEvents.Event()
            var last: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                // MOVE_TO_FOREGROUND == ACTIVITY_RESUMED (both 1); the old name works on every API level.
                if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = ev.packageName
            }
            last
        } catch (e: Exception) {
            null
        }
    }
}
