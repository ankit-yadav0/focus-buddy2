package com.example.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Switches Focuss Buddy's accessibility service back ON by editing
 * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES. Android only lets an app do that with
 * WRITE_SECURE_SETTINGS, which cannot be granted from the UI - it is granted once over adb:
 *   adb shell pm grant <applicationId> android.permission.WRITE_SECURE_SETTINGS
 * (Switching the service OFF needs no permission: the service calls disableSelf().)
 */
object AccessibilityToggler {
    private const val TAG = "AccessibilityToggler"

    fun componentFlat(context: Context): String =
        ComponentName(context.packageName, FocusAccessibilityService::class.java.name).flattenToString()

    fun hasSecureSettingsPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isEnabledInSettings(context: Context): Boolean = try {
        AccessibilityListUtil.contains(
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            componentFlat(context)
        )
    } catch (e: Exception) {
        false
    }

    /** Adds our service to the enabled list. True if it is (now) enabled in settings. */
    fun enable(context: Context): Boolean {
        if (!hasSecureSettingsPermission(context)) return false
        return try {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val flat = componentFlat(context)
            if (!AccessibilityListUtil.contains(current, flat)) {
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    AccessibilityListUtil.add(current, flat)
                )
            }
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not re-enable the accessibility service", e)
            false
        }
    }
}
