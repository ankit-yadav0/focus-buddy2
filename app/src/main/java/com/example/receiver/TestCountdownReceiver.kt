package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.scheduler.TestCountdownScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TestCountdownReceiver : BroadcastReceiver() {
    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                TestCountdownScheduler.updateNotification(context)
            } catch (e: Exception) {
                Log.e("TestCountdownReceiver", "Failed to update test countdown notification", e)
            } finally {
                TestCountdownScheduler.scheduleNextUpdate(context)
                pendingResult.finish()
            }
        }
    }
}
