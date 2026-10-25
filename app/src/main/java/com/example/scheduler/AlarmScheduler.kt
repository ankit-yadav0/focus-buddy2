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
import java.util.Calendar

object AlarmScheduler {
    const val ACTION_START_SCHEDULE = "com.example.action.START_SCHEDULE"
    const val ACTION_STOP_SCHEDULE = "com.example.action.STOP_SCHEDULE"

    fun getNextOccurrence(hour: Int, minute: Int, daysOfWeekStr: String): Long {
        val enabledDays = daysOfWeekStr.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet() // 1 = Monday, 7 = Sunday
        if (enabledDays.isEmpty()) return 0L

        val nowMillis = System.currentTimeMillis()
        for (offset in 0..14) {
            val candidate = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // Calendar.SUNDAY is 1, Calendar.MONDAY is 2, ..., Calendar.SATURDAY is 7
            // Convert to ISO 1 (Monday) .. 7 (Sunday)
            val calDay = candidate.get(Calendar.DAY_OF_WEEK)
            val isoDay = if (calDay == Calendar.SUNDAY) 7 else calDay - 1
            if (enabledDays.contains(isoDay)) {
                if (candidate.timeInMillis > nowMillis) {
                    return candidate.timeInMillis
                }
            }
        }
        return 0L
    }

    /**
     * If [nowMillis] is currently inside an active window of [schedule], returns the epoch millis
     * when that current window ends; otherwise returns null.
     */
    fun getCurrentWindowEndMillis(schedule: StrictSchedule, nowMillis: Long = System.currentTimeMillis()): Long? {
        if (!schedule.isEnabled) return null
        val enabledDays = schedule.daysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
        if (enabledDays.isEmpty()) return null

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val calDay = cal.get(Calendar.DAY_OF_WEEK)
        val isoDayToday = if (calDay == Calendar.SUNDAY) 7 else calDay - 1
        val isoDayYesterday = if (isoDayToday == 1) 7 else isoDayToday - 1

        val nowMinOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startTotalMin = schedule.startHour * 60 + schedule.startMinute
        val endTotalMin = schedule.endHour * 60 + schedule.endMinute

        if (endTotalMin > startTotalMin) {
            if (isoDayToday in enabledDays && nowMinOfDay in startTotalMin until endTotalMin) {
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, schedule.endHour)
                    set(Calendar.MINUTE, schedule.endMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (endCal.timeInMillis > nowMillis) return endCal.timeInMillis
            }
        } else {
            if (isoDayToday in enabledDays && nowMinOfDay >= startTotalMin) {
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, schedule.endHour)
                    set(Calendar.MINUTE, schedule.endMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (endCal.timeInMillis > nowMillis) return endCal.timeInMillis
            } else if (isoDayYesterday in enabledDays && nowMinOfDay < endTotalMin) {
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, schedule.endHour)
                    set(Calendar.MINUTE, schedule.endMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (endCal.timeInMillis > nowMillis) return endCal.timeInMillis
            }
        }
        return null
    }

    /**
     * If [nowMillis] is currently inside an active window of [schedule], returns the remaining
     * duration in minutes (at least 1); otherwise returns null.
     */
    fun getRemainingMinutesInCurrentWindow(schedule: StrictSchedule, nowMillis: Long = System.currentTimeMillis()): Int? {
        val endMillis = getCurrentWindowEndMillis(schedule, nowMillis) ?: return null
        val diffMs = endMillis - nowMillis
        if (diffMs <= 0L) return null
        return ((diffMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    }

    fun scheduleWindow(context: Context, schedule: StrictSchedule) {
        if (!schedule.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val startMillis = getNextOccurrence(schedule.startHour, schedule.startMinute, schedule.daysOfWeek)

        // Computed as a fixed duration added to startMillis (not independently via
        // getNextOccurrence) so this is correct for both same-day and midnight-crossing
        // windows. If we are currently inside an active window (e.g. after a reboot or
        // enabling a schedule mid-window), arm the current window's STOP alarm first.
        val startTotalMin = schedule.startHour * 60 + schedule.startMinute
        var endTotalMin = schedule.endHour * 60 + schedule.endMinute
        if (endTotalMin <= startTotalMin) endTotalMin += 24 * 60
        val durationMinutes = (endTotalMin - startTotalMin).coerceAtLeast(1)
        val currentWindowEndMillis = getCurrentWindowEndMillis(schedule)
        val endMillis = currentWindowEndMillis ?: if (startMillis > 0L) startMillis + durationMinutes * 60_000L else 0L

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
            setAlarmSafely(alarmManager, startMillis, startPendingIntent)
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
            setAlarmSafely(alarmManager, endMillis, endPendingIntent)
        }
    }

    /**
     * Exact alarm when allowed. On Android 12+ the "Alarms & reminders" permission can be missing or
     * revoked, and setExact*() then throws SecurityException - which used to crash whoever called
     * scheduleWindow() (e.g. enabling a schedule from the UI). Fall back to an inexact alarm instead,
     * so the window still fires, just possibly a little late.
     */
    private fun setAlarmSafely(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            android.util.Log.w("AlarmScheduler", "Exact alarms not permitted - falling back to an inexact alarm", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    /** Arms only the next START alarm, leaving any already-armed STOP alarm untouched. */
    fun scheduleNextStart(context: Context, schedule: StrictSchedule) {
        if (!schedule.isEnabled) return
        val startMillis = getNextOccurrence(schedule.startHour, schedule.startMinute, schedule.daysOfWeek)
        if (startMillis <= 0L) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val startIntent = Intent(context, StrictScheduleReceiver::class.java).apply {
            action = ACTION_START_SCHEDULE
            putExtra("extra_schedule_id", schedule.id)
        }
        setAlarmSafely(alarmManager, startMillis, PendingIntent.getBroadcast(context, schedule.id * 2, startIntent, flags))
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
