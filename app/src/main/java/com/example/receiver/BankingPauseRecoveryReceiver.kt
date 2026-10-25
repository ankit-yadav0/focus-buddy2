package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.service.BankingPause

/**
 * Dead-man's switch for Banking Pause. Fires from the recovery alarm (the pause outlived its cap, or
 * the pause service died) and from the "Resume now" notification action. Both just switch Focuss
 * Buddy's protection back on. Ignored unless a pause is actually recorded, so a stale alarm can never
 * re-enable a service the user switched off on purpose.
 */
class BankingPauseRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != BankingPause.ACTION_RECOVER && action != BankingPause.ACTION_RESUME_NOW) return
        if (!BankingPause.isPaused(context)) return
        BankingPause.resume(context.applicationContext, action)
    }
}
