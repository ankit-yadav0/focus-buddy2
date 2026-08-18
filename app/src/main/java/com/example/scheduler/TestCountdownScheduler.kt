package com.example.scheduler

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * The test-countdown notification feature has been removed. This object now only
 * exists to clean up after itself: dismiss any already-showing persistent
 * notification and cancel any alarm that was scheduled by an older version of the
 * app, so nothing is left stuck around after the update installs.
 */
object TestCountdownScheduler {

    const val ACTION_UPDATE_COUNTDOWN = "com.example.action.UPDATE_TEST_COUNTDOWN"
    private const val NOTIFICATION_ID = 4501

    fun cancelUpdates(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.example.receiver.TestCountdownReceiver::class.java).apply {
            action = ACTION_UPDATE_COUNTDOWN
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        alarmManager.cancel(PendingIntent.getBroadcast(context, 5001, intent, flags))
    }

    fun clearNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}
