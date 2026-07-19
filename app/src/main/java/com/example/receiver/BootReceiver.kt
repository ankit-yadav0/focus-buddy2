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
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received broadcast: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            receiverScope.launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val activeSession = db.focusSessionDao().getActiveSessionSync()
                    if (activeSession != null && activeSession.isActive && activeSession.isStrict) {
                        Log.d("BootReceiver", "Active strict session found during $action. Checking accessibility service.")
                        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
                        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                        val isServiceEnabled = enabledServices?.any { info ->
                            val sInfo = info.resolveInfo?.serviceInfo
                            sInfo != null && sInfo.packageName == context.packageName && sInfo.name.endsWith("FocusAccessibilityService")
                        } == true

                        if (!isServiceEnabled) {
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
                                .setContentText("Focus Buddy needs accessibility access re-granted to enforce your active strict focus session.")
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

                    // Reschedule enabled schedules on boot
                    try {
                        val enabledSchedules = db.strictScheduleDao().getEnabledSchedulesSync()
                        com.example.scheduler.AlarmScheduler.rescheduleAll(context, enabledSchedules)
                        Log.d("BootReceiver", "Rescheduled ${enabledSchedules.size} enabled schedules on boot.")
                    } catch (e: Exception) {
                        Log.e("BootReceiver", "Error rescheduling schedules on boot", e)
                    }

                    // Re-arm the test countdown notification updater on boot
                    try {
                        com.example.scheduler.TestCountdownScheduler.scheduleNextUpdate(context)
                    } catch (e: Exception) {
                        Log.e("BootReceiver", "Error re-arming test countdown updater on boot", e)
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
