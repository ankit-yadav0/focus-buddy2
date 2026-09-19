package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.FocusApplication
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StrictScheduleReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val scheduleId = intent.getIntExtra("extra_schedule_id", -1)
                val action = intent.action
                if (scheduleId != -1 && action != null) {
                    val repository = (context.applicationContext as FocusApplication).repository
                    val schedule = repository.getScheduleById(scheduleId)
                    if (schedule != null && schedule.isEnabled) {
                        if (action == AlarmScheduler.ACTION_START_SCHEDULE) {
                            val startTotal = schedule.startHour * 60 + schedule.startMinute
                            val endTotal = schedule.endHour * 60 + schedule.endMinute
                            val durationMinutes = if (endTotal > startTotal) {
                                endTotal - startTotal
                            } else {
                                (24 * 60 - startTotal) + endTotal
                            }.coerceAtLeast(1)

                            repository.startScheduledStrictSession(schedule.id, durationMinutes)
                        } else if (action == AlarmScheduler.ACTION_STOP_SCHEDULE) {
                            repository.stopScheduledStrictSession(schedule.id)
                        }
                        // Re-arm the next occurrence
                        AlarmScheduler.scheduleWindow(context, schedule)
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("StrictScheduleReceiver", "Error handling strict schedule", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
