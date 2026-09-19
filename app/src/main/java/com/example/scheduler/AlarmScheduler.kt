package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.data.StrictSchedule
import com.example.receiver.StrictScheduleReceiver
import java.time.ZonedDateTime

object AlarmScheduler {
    const val ACTION_START_SCHEDULE = "com.example.action.START_SCHEDULE"
    const val ACTION_STOP_SCHEDULE = "com.example.action.STOP_SCHEDULE"

    fun getNextOccurrence(hour: Int, minute: Int, daysOfWeekStr: String): Long {
        val enabledDays = daysOfWeekStr.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        if (enabledDays.isEmpty()) return 0L

        val now = ZonedDateTime.now()
        for (offset in 0..14) {
            val candidateDay = now.plusDays(offset.toLong())
            val dayOfWeekVal = candidateDay.dayOfWeek.value // 1 = Monday, 7 = Sunday
            if (enabledDays.contains(dayOfWeekVal)) {
                val candidateTime = candidateDay
                    .withHour(hour)
                    .withMinute(minute)
                    .withSecond(0)
                    .withNano(0)
                if (candidateTime.isAfter(now)) {
                    return candidateTime.toInstant().toEpochMilli()
                }
            }
        }
        return 0L
    }

    fun scheduleWindow(context: Context, schedule: StrictSchedule) {
        if (!schedule.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val startMillis = getNextOccurrence(schedule.startHour, schedule.startMinute, schedule.daysOfWeek)

        // Computed as a fixed duration added to startMillis (not independently via
        // getNextOccurrence) so this is correct for both same-day and midnight-crossing
        // windows - see the bug description above for why the independent-lookup
        // approach breaks for overnight schedules.
        val startTotalMin = schedule.startHour * 60 + schedule.startMinute
        var endTotalMin = schedule.endHour * 60 + schedule.endMinute
        if (endTotalMin <= startTotalMin) endTotalMin += 24 * 60
        val durationMinutes = (endTotalMin - startTotalMin).coerceAtLeast(1)
        val endMillis = if (startMillis > 0L) startMillis + durationMinutes * 60_000L else 0L

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        if (startMillis > 0L) {
            val startIntent = Intent(context, StrictScheduleReceiver::class.java).apply {
                action = ACTION_START_SCHEDULE
                putExtra("extra_schedule_id", schedule.id)
            }
            val startPendingIntent = PendingIntent.getBroadcast(
                context,
                schedule.id * 2,
                startIntent,
                flags
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startMillis, startPendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, startMillis, startPendingIntent)
            }
        }

        if (endMillis > 0L) {
            val endIntent = Intent(context, StrictScheduleReceiver::class.java).apply {
                action = ACTION_STOP_SCHEDULE
                putExtra("extra_schedule_id", schedule.id)
            }
            val endPendingIntent = PendingIntent.getBroadcast(
                context,
                schedule.id * 2 + 1,
                endIntent,
                flags
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMillis, endPendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, endMillis, endPendingIntent)
            }
        }
    }

    fun cancelWindow(context: Context, scheduleId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val startIntent = Intent(context, StrictScheduleReceiver::class.java).apply {
            action = ACTION_START_SCHEDULE
        }
        val startPendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId * 2,
            startIntent,
            flags
        )
        alarmManager.cancel(startPendingIntent)
        startPendingIntent.cancel()

        val endIntent = Intent(context, StrictScheduleReceiver::class.java).apply {
            action = ACTION_STOP_SCHEDULE
        }
        val endPendingIntent = PendingIntent.getBroadcast(
            context,
            scheduleId * 2 + 1,
            endIntent,
            flags
        )
        alarmManager.cancel(endPendingIntent)
        endPendingIntent.cancel()
    }

    fun rescheduleAll(context: Context, schedules: List<StrictSchedule>) {
        for (schedule in schedules) {
            cancelWindow(context, schedule.id)
            if (schedule.isEnabled) {
                scheduleWindow(context, schedule)
            }
        }
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
}
