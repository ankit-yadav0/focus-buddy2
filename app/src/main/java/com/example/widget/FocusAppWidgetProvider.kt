package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.TrustedClock
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Home Screen Widget ("Precision Instrument" theme) displaying Current Streak, Today's Focus Time,
 * and a 1-Tap "START FOCUS" action button.
 */
class FocusAppWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                updateWidgetsInternal(context.applicationContext, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                android.util.Log.e("FocusAppWidget", "Error updating widget", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            try {
                val appContext = context.applicationContext
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(ComponentName(appContext, FocusAppWidgetProvider::class.java))
                if (ids != null && ids.isNotEmpty()) {
                    val intent = Intent(appContext, FocusAppWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    appContext.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("FocusAppWidget", "Failed to request widget update", e)
            }
        }

        suspend fun updateWidgetsInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val db = AppDatabase.getDatabase(context)
            val sessions = db.focusSessionDao().getAllSessions().firstOrNull().orEmpty()
            val activeSession = db.focusSessionDao().getActiveSessionSync()
            val now = TrustedClock.now(context)

            val todayCal = Calendar.getInstance()
            val todayStart = (todayCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todaySeconds = sessions
                .filter { it.startTime >= todayStart }
                .sumOf { it.actualDurationSeconds }
            val h = todaySeconds / 3600
            val m = (todaySeconds % 3600) / 60
            val todayFormatted = if (h > 0) "${h}h ${m}m" else "${m}m"

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val completedDays = sessions
                .filter { it.sessionStatus == "Completed" || it.sessionStatus == "Expired" }
                .map { sdf.format(Date(it.startTime)) }
                .toSet()

            val todayStr = sdf.format(Date())
            val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yesterdayStr = sdf.format(yesterdayCal.time)
            val checkCal = Calendar.getInstance()
            var streak = 0
            if (completedDays.contains(todayStr)) {
                streak = 1
                checkCal.add(Calendar.DAY_OF_YEAR, -1)
                while (completedDays.contains(sdf.format(checkCal.time))) {
                    streak++
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                }
            } else if (completedDays.contains(yesterdayStr)) {
                streak = 1
                checkCal.add(Calendar.DAY_OF_YEAR, -2)
                while (completedDays.contains(sdf.format(checkCal.time))) {
                    streak++
                    checkCal.add(Calendar.DAY_OF_YEAR, -1)
                }
            }

            val isRunning = activeSession != null && activeSession.isActive && now < activeSession.endTime
            val buttonLabel = if (isRunning) {
                val remMin = ((activeSession!!.endTime - now + 59_999L) / 60_000L).coerceAtLeast(1L)
                "ACTIVE (${remMin}m left)"
            } else {
                "+ START FOCUS"
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val startTimerIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (!isRunning) {
                    putExtra("deep_link_route", "timer")
                }
            }
            val startPendingIntent = PendingIntent.getActivity(context, 9101, startTimerIntent, flags)

            val openHomeIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val openHomePendingIntent = PendingIntent.getActivity(context, 9102, openHomeIntent, flags)

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.focus_app_widget)
                views.setTextViewText(R.id.widget_streak_value, "\uD83D\uDD25 ${streak}d")
                views.setTextViewText(R.id.widget_today_value, todayFormatted)
                views.setTextViewText(R.id.widget_start_button, buttonLabel)
                views.setOnClickPendingIntent(R.id.widget_root, openHomePendingIntent)
                views.setOnClickPendingIntent(R.id.widget_start_button, startPendingIntent)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
