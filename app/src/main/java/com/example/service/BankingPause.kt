package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.receiver.BankingPauseRecoveryReceiver

/**
 * Banking Pause glue: persistent "paused" marker, recovery alarm, begin / resume.
 *
 * Lifecycle:  FocusAccessibilityService sees a chosen banking app open -> begin() -> service
 * calls disableSelf(). BankingPauseService (foreground) watches which app is in front and calls
 * resume() when it is left. Safety nets so protection can never stay off: hard time cap, a
 * recovery alarm that fires even if the pause service dies, boot-time restore, and a
 * "tap to turn back on" notification if re-enabling fails.
 */
object BankingPause {
    private const val TAG = "BankingPause"
    private const val PREFS = "focuss_buddy_settings"
    private const val KEY_ACTIVE = "bp_active"
    private const val KEY_PKG = "bp_pkg"
    private const val KEY_STARTED = "bp_started_elapsed"

    const val ACTION_RESUME_NOW = "com.example.action.BANKING_RESUME_NOW"
    const val ACTION_RECOVER = "com.example.action.BANKING_RECOVER"
    const val EXTRA_PACKAGE = "extra_banking_package"

    private const val ALARM_REQUEST = 7001
    private const val RESUME_NOW_REQUEST = 7002
    const val CHANNEL_ID = "focus_buddy_banking_pause"
    const val NOTIFICATION_ID = 4303
    private const val FAILED_NOTIFICATION_ID = 4302
    private const val RETRY_DELAY_MS = 60_000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPaused(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)
    fun pausedPackage(context: Context): String? = prefs(context).getString(KEY_PKG, null)
    fun startedElapsed(context: Context): Long = prefs(context).getLong(KEY_STARTED, 0L)

    /** A pause is only ever started if protection can be switched back on and the app can be watched. */
    fun canPause(context: Context): Boolean =
        !isPaused(context) &&
            AccessibilityToggler.hasSecureSettingsPermission(context) &&
            ForegroundProbe.hasUsageAccess(context)

    /**
     * Records the pause, arms the recovery alarm and starts the watcher. The caller must then call
     * disableSelf(). Returns false (and changes nothing) if the pause could not be set up.
     */
    fun begin(context: Context, packageName: String): Boolean {
        val app = context.applicationContext
        if (!canPause(app)) return false
        val now = SystemClock.elapsedRealtime()
        // commit(), not apply(): the marker must be on disk before the service switches itself off.
        prefs(app).edit().putBoolean(KEY_ACTIVE, true).putString(KEY_PKG, packageName).putLong(KEY_STARTED, now).commit()
        scheduleAlarm(app, now + BankingPausePolicy.MAX_PAUSE_MS + BankingPausePolicy.DEADMAN_MARGIN_MS)
        return try {
            ContextCompat.startForegroundService(
                app,
                Intent(app, BankingPauseService::class.java).putExtra(EXTRA_PACKAGE, packageName)
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not start the pause watcher - not pausing", e)
            clearMarker(app)
            cancelAlarm(app)
            false
        }
    }

    /**
     * Switches protection back on and tears the pause down. Safe to call any number of times.
     * If re-enabling fails the marker stays, a "tap to turn on" notification is shown and a retry
     * alarm is armed.
     */
    fun resume(context: Context, reason: String): Boolean {
        val app = context.applicationContext
        val ok = AccessibilityToggler.isEnabledInSettings(app) || AccessibilityToggler.enable(app)
        Log.d(TAG, "resume($reason) -> $ok")
        if (ok) {
            finish(app)
        } else {
            postReenableFailedNotification(app)
            scheduleAlarm(app, SystemClock.elapsedRealtime() + RETRY_DELAY_MS)
        }
        return ok
    }

    /**
     * Called by the accessibility service when it (re)connects: any pause is over. Returns true if a
     * pause had been running, so the service knows the app in front was never checked while it was off.
     */
    fun onServiceConnected(context: Context): Boolean {
        if (!isPaused(context)) return false
        finish(context.applicationContext)
        return true
    }

    /** Boot: switch the service back on if a pause was interrupted by a reboot. */
    fun restoreAfterBoot(context: Context): Boolean {
        val app = context.applicationContext
        if (!isPaused(app)) return false
        return resume(app, "boot")
    }

    /** Strict Mode is running but the service is off: switch it on if we are allowed to. */
    fun restoreAccessibilityIfPossible(context: Context): Boolean {
        val app = context.applicationContext
        if (!AccessibilityToggler.hasSecureSettingsPermission(app)) return false
        return AccessibilityToggler.enable(app)
    }

    private fun finish(app: Context) {
        clearMarker(app)
        cancelAlarm(app)
        cancelFailedNotification(app)
        try {
            app.stopService(Intent(app, BankingPauseService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "stopService failed", e)
        }
    }

    private fun clearMarker(app: Context) {
        prefs(app).edit().remove(KEY_ACTIVE).remove(KEY_PKG).remove(KEY_STARTED).commit()
    }

    // ---- recovery alarm ----------------------------------------------------------------------

    private fun alarmIntent(app: Context): PendingIntent {
        val intent = Intent(app, BankingPauseRecoveryReceiver::class.java).setAction(ACTION_RECOVER)
        return PendingIntent.getBroadcast(app, ALARM_REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleAlarm(app: Context, triggerAtElapsedMs: Long) {
        try {
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedMs, alarmIntent(app))
        } catch (e: Exception) {
            Log.e(TAG, "Could not arm recovery alarm", e)
        }
    }

    private fun cancelAlarm(app: Context) {
        try {
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(alarmIntent(app))
        } catch (e: Exception) {
            Log.e(TAG, "Could not cancel recovery alarm", e)
        }
    }

    // ---- notifications -----------------------------------------------------------------------

    fun ensureChannel(app: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = app.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Banking Pause", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shown while Focuss Buddy is paused for a banking app"
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    /** "Resume now" action for the pause notification. */
    fun resumeNowIntent(app: Context): PendingIntent {
        val intent = Intent(app, BankingPauseRecoveryReceiver::class.java).setAction(ACTION_RESUME_NOW)
        return PendingIntent.getBroadcast(app, RESUME_NOW_REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun postReenableFailedNotification(app: Context) {
        try {
            ensureChannel(app)
            val open = PendingIntent.getActivity(
                app, 0, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(app, CHANNEL_ID)
                .setContentTitle("Focuss Buddy protection is OFF")
                .setContentText("Tap to turn Focuss Buddy accessibility back on.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            app.getSystemService(NotificationManager::class.java).notify(FAILED_NOTIFICATION_ID, n)
        } catch (e: Exception) {
            Log.e(TAG, "Could not post re-enable notification", e)
        }
    }

    private fun cancelFailedNotification(app: Context) {
        try {
            app.getSystemService(NotificationManager::class.java).cancel(FAILED_NOTIFICATION_ID)
        } catch (e: Exception) {
        }
    }
}
