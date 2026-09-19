package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.receiver.BankingModeReceiver

object BankingModeScheduler {
    const val BANKING_MODE_DURATION_MS = 5 * 60 * 1000L // 5 minutes, fixed by design
    private const val REQUEST_CODE = 7301

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BankingModeReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    /** Returns the epoch-ms timestamp banking mode will end at. */
    fun scheduleReminder(context: Context): Long {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + BANKING_MODE_DURATION_MS
        val pi = pendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
        return triggerAt
    }

    /** Cancels the pending reminder - used if the user manually re-enables early. */
    fun cancelReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        alarmManager.cancel(pi)
        pi.cancel()
    }
}
