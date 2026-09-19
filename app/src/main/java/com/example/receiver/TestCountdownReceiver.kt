package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.scheduler.TestCountdownScheduler

/**
 * Kept only so a stale alarm scheduled by an older version of the app (before the
 * test-countdown notification feature was removed) has somewhere safe to land -
 * it just cleans up and does not reschedule itself.
 */
class TestCountdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TestCountdownScheduler.clearNotification(context)
        TestCountdownScheduler.cancelUpdates(context)
    }
}
