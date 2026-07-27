package com.example.ui.helper

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Banking Mode temporarily suspends ALL Focus Buddy blocking - active focus-session blocks,
 * Strict Mode, long-term app/website blocks, everything the AccessibilityService enforces -
 * for a fixed 5-minute window. This exists because some banking/UPI/net-banking apps refuse
 * to work correctly while any AccessibilityService is running on the device.
 *
 * It turns itself back off automatically once the window elapses (isActive() is a pure time
 * comparison, so nothing has to remember to re-enable protection) and it survives the app
 * process being killed and restarted because the end time is persisted to SharedPreferences.
 */
object BankingModeManager {
    private const val PREFS_NAME = "banking_mode_prefs"
    private const val KEY_END_TIME = "banking_mode_end_time"
    const val DURATION_MS = 5 * 60 * 1000L // 5 minutes

    private var prefs: SharedPreferences? = null

    private val _endTime = MutableStateFlow(0L)
    val endTime: StateFlow<Long> = _endTime

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        _endTime.value = p.getLong(KEY_END_TIME, 0L)
    }

    /** True while blocking should be bypassed. Automatically becomes false once time is up. */
    fun isActive(): Boolean = System.currentTimeMillis() < _endTime.value

    /** Starts (or restarts) the 5-minute bypass window. */
    fun activate() {
        val end = System.currentTimeMillis() + DURATION_MS
        _endTime.value = end
        prefs?.edit()?.putLong(KEY_END_TIME, end)?.apply()
    }

    /** Manually ends Banking Mode early, restoring normal blocking immediately. */
    fun deactivateNow() {
        _endTime.value = 0L
        prefs?.edit()?.putLong(KEY_END_TIME, 0L)?.apply()
    }

    fun remainingMs(): Long = (_endTime.value - System.currentTimeMillis()).coerceAtLeast(0L)
}
