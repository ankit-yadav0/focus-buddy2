package com.example.receiver

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import com.example.FocusApplication
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.example.TrustedClock

class BootReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private companion object {
        const val BOOT_WORK_TIMEOUT_MS = 8_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received broadcast: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Re-anchor the tamper-resistant clock as early as possible after boot.
            val now = TrustedClock.now(context)
            val pendingResult = goAsync()
            receiverScope.launch {
                try {
                  // goAsync() gives roughly 10 seconds before the system ANRs the process. On a slow
                  // 2GB device a cold database open can eat into that, so cap the work well under it
                  // (the finally block below always releases the broadcast).
                  withTimeout(BOOT_WORK_TIMEOUT_MS) {
                    // A Banking Pause interrupted by a reboot must not leave protection off.
                    val restoredFromPause = com.example.service.BankingPause.restoreAfterBoot(context)

                    val db = AppDatabase.getDatabase(context)
                    val repository = (context.applicationContext as? FocusApplication)?.repository

                    // Reschedule enabled schedules on boot, and start any schedule whose window is active right now
                    try {
                        val enabledSchedules = db.strictScheduleDao().getEnabledSchedulesSync()
                        com.example.scheduler.AlarmScheduler.rescheduleAll(context, enabledSchedules)
                        for (schedule in enabledSchedules) {
                            val remainingMin = com.example.scheduler.AlarmScheduler.getRemainingMinutesInCurrentWindow(schedule)
                            if (remainingMin != null && remainingMin > 0) {
                                repository?.startScheduledStrictSession(schedule.id, remainingMin)
                            }
                        }
                        Log.d("BootReceiver", "Rescheduled ${enabledSchedules.size} enabled schedules on boot.")
                    } catch (e: Exception) {
                        Log.e("BootReceiver", "Error rescheduling schedules on boot", e)
                    }

                    var activeSession = db.focusSessionDao().getActiveSessionSync()
                    if (activeSession != null && activeSession.isActive && now >= activeSession.endTime) {
                        repository?.stopActiveSession("Expired")
                        activeSession = db.focusSessionDao().getActiveSessionSync()
                    }

                    if (activeSession != null && activeSession.isActive && activeSession.isStrict && now < activeSession.endTime) {
                        Log.d("BootReceiver", "Active strict session found during $action. Checking accessibility service.")
                        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                        val isServiceEnabled = enabledServices?.any { info ->
                            val sInfo = info.resolveInfo?.serviceInfo
                            sInfo != null && sInfo.packageName == context.packageName && sInfo.name.endsWith("FocusAccessibilityService")
                        } == true

                        // With WRITE_SECURE_SETTINGS (Banking Pause setup) the service can simply be switched
                        // back on; otherwise the user has to do it from the notification below.
                        val restoredAutomatically = !isServiceEnabled && !restoredFromPause &&
                            com.example.service.BankingPause.restoreAccessibilityIfPossible(context)

                        if (!isServiceEnabled && !restoredFromPause && !restoredAutomatically) {
                            Log.d("BootReceiver", "Service is NOT enabled. Posting re-grant notification.")
                            val channelId = "focus_buddy_reenable"
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                if (notificationManager.getNotificationChannel(channelId) == null) {
                                    val channel = NotificationChannel(
                                        channelId,
                                        "Accessibility Access Re-enable",
                                        NotificationManager.IMPORTANCE_HIGH
                                    ).apply {
                                        description = "Prompts the user to re-enable accessibility service when in a strict session after a reboot."
                                    }
                                    notificationManager.createNotificationChannel(channel)
                                }
                            }

                            val mainIntent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            } else {
                                PendingIntent.FLAG_UPDATE_CURRENT
                            }
                            val pendingIntent = PendingIntent.getActivity(context, 0, mainIntent, pendingIntentFlags)

                            val builder = NotificationCompat.Builder(context, channelId)
                                .setContentTitle("Accessibility Permission Needed")
                                .setContentText("Focuss Buddy needs accessibility access re-granted to enforce your active strict focus session.")
                                .setSmallIcon(R.mipmap.ic_launcher)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true)

                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.notify(9001, builder.build())
                        } else {
                            Log.d("BootReceiver", "Accessibility service is already enabled. System will handle rebinding automatically.")
                        }
                    } else {
                        Log.d("BootReceiver", "No active strict session. Accessibility check not required.")
                    }
                  }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error while handling broadcast $action", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
