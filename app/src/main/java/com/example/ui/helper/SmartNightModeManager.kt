package com.example.ui.helper

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Calendar

object SmartNightModeManager {
    private val _isNightModeActive = MutableStateFlow(isNightTime())
    val isNightModeActive: StateFlow<Boolean> = _isNightModeActive

    private var checkJob: Job? = null

    fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 21 || hour < 6
    }

    fun startMonitoring(scope: CoroutineScope) {
        if (checkJob != null) return
        checkJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val current = isNightTime()
                if (_isNightModeActive.value != current) {
                    _isNightModeActive.value = current
                }
                delay(10000) // check every 10 seconds for real-time reactivity without overhead
            }
        }
    }

    fun stopMonitoring() {
        checkJob?.cancel()
        checkJob = null
    }
}
