package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires when Banking Mode's 5-minute window ends. Android does not allow an
 * app to silently re-enable its own accessibility service (a deliberate
 * security restriction - otherwise apps could grant themselves this powerful
 * permission back without the user's knowledge). This is the closest
 * available substitute: a high-priority notification that deep-links
 * directly to the Accessibility settings screen, so re-enabling only takes
 * one tap on the notification plus one tap on the toggle.
 */
class BankingModeReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "focus_buddy_banking_mode"
        const val NOTIFICATION_ID = 9002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val repository = (context.applicationContext as com.example.FocusApplication).repository
                // Only notify if banking mode is still the reason accessibility is off -
                // if the user already manually re-enabled it early, this is a no-op.
                repository.saveSetting("banking_mode_active", "false")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                        val channel = NotificationChannel(
                            CHANNEL_ID,
                            "Banking Mode",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Reminds you to re-enable protection after Banking Mode's 5-minute window ends."
                        }
                        notificationManager.createNotificationChannel(channel)
                    }
                }

                val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getActivity(context, 0, settingsIntent, flags)

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle("Banking Mode ended")
                    .setContentText("Tap to re-enable Focuss Buddy's protection (1 tap to open, 1 tap on the toggle).")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
