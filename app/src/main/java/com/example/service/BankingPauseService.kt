package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.FocusApplication
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground watcher that lives while Focuss Buddy's accessibility service is switched off for a
 * banking app. It cannot use the accessibility service to see the foreground app (that is the thing
 * that is off), so it polls UsageStats and asks [BankingPausePolicy] when to resume.
 */
class BankingPauseService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pkg = intent?.getStringExtra(BankingPause.EXTRA_PACKAGE) ?: BankingPause.pausedPackage(this)
        startAsForeground(pkg)
        if (pkg == null || !BankingPause.isPaused(this)) {
            // Nothing to watch (stale restart): never linger as a foreground service.
            stopSelf()
            return START_NOT_STICKY
        }
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { watch(pkg) }
        }
        return START_STICKY
    }

    private fun startAsForeground(pkg: String?) {
        try {
            BankingPause.ensureChannel(this)
            val label = pkg?.let {
                try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString()
                } catch (e: Exception) {
                    it
                }
            } ?: "a banking app"
            val notification = NotificationCompat.Builder(this, BankingPause.CHANNEL_ID)
                .setContentTitle("Banking Pause: $label")
                .setContentText("Focuss Buddy resumes automatically when you leave the app.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(0, "Resume now", BankingPause.resumeNowIntent(this))
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, BankingPause.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(BankingPause.NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not go foreground", e)
        }
    }

    private suspend fun watch(triggerPkg: String) {
        val app = applicationContext
        val banking = try {
            val csv = (application as FocusApplication).repository.getSetting("banking_pause_packages")
            BankingPausePolicy.parsePackages(csv) + triggerPkg
        } catch (e: Exception) {
            setOf(triggerPkg)
        }
        val startedElapsed = BankingPause.startedElapsed(app)
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        var current = triggerPkg
        // Replay everything since the pause began (also right after a sticky restart).
        var cursor = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L) - 1_500L
        var state = BankingPausePolicy.State()

        while (scope.isActive) {
            delay(POLL_MS)
            val wallNow = System.currentTimeMillis()
            val seen = ForegroundProbe.latestResumedPackage(app, cursor, wallNow)
            cursor = wallNow - 200L
            if (seen != null) current = seen

            val decision = BankingPausePolicy.decide(
                currentPkg = current,
                banking = banking,
                nowMs = SystemClock.elapsedRealtime(),
                startedMs = startedElapsed,
                interactive = power.isInteractive,
                usageAccess = ForegroundProbe.hasUsageAccess(app),
                prev = state
            )
            state = decision.next
            val reason = decision.resume
            if (reason != null) {
                BankingPause.resume(app, reason.name)
                return
            }
        }
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "BankingPauseService"
        const val POLL_MS = 500L
    }
}
