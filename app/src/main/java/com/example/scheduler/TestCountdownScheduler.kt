package com.example.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import java.util.concurrent.TimeUnit

object TestCountdownScheduler {

    const val ACTION_UPDATE_COUNTDOWN = "com.example.action.UPDATE_TEST_COUNTDOWN"
    private const val NOTIFICATION_ID = 4501
    private const val CHANNEL_ID = "test_countdown_channel"
    private const val UPDATE_INTERVAL_MILLIS = 60 * 60 * 1000L

    fun scheduleNextUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.example.receiver.TestCountdownReceiver::class.java).apply {
            action = ACTION_UPDATE_COUNTDOWN
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, 5001, intent, flags)
        val triggerAt = System.currentTimeMillis() + UPDATE_INTERVAL_MILLIS
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (e: SecurityException) {
        }
    }

    fun cancelUpdates(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.example.receiver.TestCountdownReceiver::class.java).apply {
            action = ACTION_UPDATE_COUNTDOWN
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        alarmManager.cancel(PendingIntent.getBroadcast(context, 5001, intent, flags))
    }

    suspend fun updateNotification(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val nextTest = db.testEntryDao().getNextTest(System.currentTimeMillis())

        if (nextTest == null) {
            clearNotification(context)
            return
        }

        ensureChannel(context)

        val remainingMillis = (nextTest.dateMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val days = TimeUnit.MILLISECONDS.toDays(remainingMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis) % 24

        val countdownText = when {
            days > 0 -> "$days din $hours ghante baaki"
            hours > 0 -> "$hours ghante baaki"
            else -> "Aaj hai!"
        }
        val title = "${nextTest.testType} ${nextTest.testNumber} (${nextTest.level})"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("deep_link_route", "test_calendar")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 5002, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(countdownText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        try {
            notificationManager?.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
        }
    }

    fun clearNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (notificationManager?.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID, "Test Countdown", NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows days/hours remaining until your next scheduled test"
                        setShowBadge(false)
                    }
                    notificationManager?.createNotificationChannel(channel)
                }
            }
        }
    }
