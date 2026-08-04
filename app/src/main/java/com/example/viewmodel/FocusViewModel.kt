package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BlockedApp
import com.example.data.FocusRepository
import com.example.data.FocusSession
import com.example.data.LongTermBlock
import com.example.data.Analytics
import com.example.data.WebsiteBlock
import com.example.data.ChatMessage
import com.example.data.StrictSchedule
import com.example.data.TestEntry
import com.example.scheduler.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun todayDateKey(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean = false
)

data class DailyAnalytics(
    val totalActualFocusTimeSeconds: Long = 0L,
    val completedSessions: Int = 0,
    val endedEarlySessions: Int = 0,
    val expiredSessions: Int = 0,
    val successRate: Int = 0
)

data class TrendPoint(
    val dateLabel: String,
    val successRate: Int,
    val actualFocusMinutes: Float
)

data class CalendarDay(
    val dateString: String,
    val dayOfMonth: Int,
    val dayOfWeek: String,
    val isProductive: Boolean,
    val isMissed: Boolean,
    val isToday: Boolean
)

data class AdvancedAnalytics(
    val todayFocusTimeSeconds: Long = 0L,
    val todaySessionsCompleted: Int = 0,
    val todaySessionsEndedEarly: Int = 0,
    val weeklyFocusHours: Float = 0f,
    val weeklySessionsCount: Int = 0,
    val weeklySuccessRate: Int = 0,
    val monthlyFocusHours: Float = 0f,
    val longestSessionMinutes: Int = 0,
    val longestStreak: Int = 0,
    val currentStreak: Int = 0,
    val totalFocusDays: Int = 0,
    val productivityScore: Int = 0,
    val calendarDays: List<CalendarDay> = emptyList()
)

class FocusViewModel(
    private val repository: FocusRepository,
    private val context: Context
) : ViewModel() {

    val youtubeBlockShorts = MutableStateFlow(false)
    val instagramBlockReels = MutableStateFlow(false)
    val snapchatBlockSpotlight = MutableStateFlow(false)
    val accentTheme = MutableStateFlow("Sunset Orange")

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            val savedYoutubeShorts = prefs.getBoolean("youtube_block_shorts", false)
            val savedInstagramReels = prefs.getBoolean("instagram_block_reels", false)
            val savedSnapchatSpotlight = prefs.getBoolean("snapchat_block_spotlight", false)
            val savedAccentTheme = prefs.getString("accent_theme", "Sunset Orange") ?: "Sunset Orange"

            youtubeBlockShorts.value = savedYoutubeShorts
            instagramBlockReels.value = savedInstagramReels
            snapchatBlockSpotlight.value = savedSnapchatSpotlight
            accentTheme.value = savedAccentTheme

            _isInitialized.value = true
        }
    }

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps = _isLoadingApps.asStateFlow()

    val activeSession: StateFlow<FocusSession?> = repository.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _showReflectionPrompt = MutableStateFlow(false)
    val showReflectionPrompt: StateFlow<Boolean> = _showReflectionPrompt.asStateFlow()

    fun saveReflectionNote(text: String) {
        viewModelScope.launch {
            repository.addReflectionNote(text)
            _showReflectionPrompt.value = false
        }
    }

    fun dismissReflectionPrompt() {
        _showReflectionPrompt.value = false
    }

    val isStrictModeActive: StateFlow<Boolean> = activeSession
        .map { session ->
            session?.let { it.isActive && it.isStrict && System.currentTimeMillis() < it.endTime } ?: false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val blockedApps: StateFlow<List<BlockedApp>> = repository.allBlockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appListState: StateFlow<List<AppInfo>> = combine(_installedApps, blockedApps) { installed, blocked ->
        val blockedPackages = blocked.map { it.packageName }.toSet()
        installed.map { app ->
            app.copy(isBlocked = blockedPackages.contains(app.packageName))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Phase 2 flows
    val allLongTermBlocks: StateFlow<List<LongTermBlock>> = repository.allLongTermBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeLongTermBlocks: StateFlow<List<LongTermBlock>> = repository.activeLongTermBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map of target (package/domain) -> minutes used today, for showing daily-limit progress.
    val todayUsageMinutes: StateFlow<Map<String, Int>> = repository
        .getUsageForDate(todayDateKey())
        .map { list -> list.associate { it.target to it.minutesUsed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allWebsiteBlocks: StateFlow<List<WebsiteBlock>> = repository.allWebsiteBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWebsiteBlocks: StateFlow<List<WebsiteBlock>> = repository.activeWebsiteBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val analytics: StateFlow<Analytics?> = repository.analytics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allSchedules: StateFlow<List<StrictSchedule>> = repository.allSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addSchedule(context: Context, schedule: StrictSchedule) {
        viewModelScope.launch {
            val id = repository.addSchedule(schedule)
            if (schedule.isEnabled) {
                AlarmScheduler.scheduleWindow(context, schedule.copy(id = id.toInt()))
            }
        }
    }

    fun updateSchedule(context: Context, schedule: StrictSchedule) {
        viewModelScope.launch {
            repository.updateSchedule(schedule)
            AlarmScheduler.cancelWindow(context, schedule.id)
            if (schedule.isEnabled) {
                AlarmScheduler.scheduleWindow(context, schedule)
            }
        }
    }

    fun deleteSchedule(context: Context, schedule: StrictSchedule) {
        viewModelScope.launch {
            val currentlyActive = repository.getActiveSessionSync()
            if (currentlyActive != null && currentlyActive.origin == "SCHEDULE:${schedule.id}") {
                return@launch
            }
            repository.deleteSchedule(schedule.id)
            AlarmScheduler.cancelWindow(context, schedule.id)
        }
    }

    fun toggleSchedule(context: Context, schedule: StrictSchedule) {
        val flipped = schedule.copy(isEnabled = !schedule.isEnabled)
        updateSchedule(context, flipped)
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        return AlarmScheduler.hasExactAlarmPermission(context)
    }

    fun requestExactAlarmPermission(context: Context) {
        AlarmScheduler.requestExactAlarmPermission(context)
    }

    private fun isToday(timestamp: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance()
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
               cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }

    val manualFocusTimeOffset = MutableStateFlow(0L)
    val manualCompletedOffset = MutableStateFlow(0)
    val manualEarlyExitsOffset = MutableStateFlow(0)

    fun setAccentTheme(themeName: String) {
        accentTheme.value = themeName
        viewModelScope.launch {
            repository.saveSetting("accent_theme", themeName)
        }
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("accent_theme", themeName)
            .apply()
    }



    fun setYoutubeBlockShorts(value: Boolean) {
        if (isStrictModeActive.value) return
        youtubeBlockShorts.value = value
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("youtube_block_shorts", value)
            .apply()
    }

    fun setInstagramBlockReels(value: Boolean) {
        if (isStrictModeActive.value) return
        instagramBlockReels.value = value
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("instagram_block_reels", value)
            .apply()
    }

    fun setSnapchatBlockSpotlight(value: Boolean) {
        if (isStrictModeActive.value) return
        snapchatBlockSpotlight.value = value
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("snapchat_block_spotlight", value)
            .apply()
    }

    private val dbDailyAnalytics: StateFlow<DailyAnalytics> = repository.allSessions
        .map { sessions ->
            val todaySessions = sessions.filter { isToday(it.startTime) }
            val completed = todaySessions.count { it.sessionStatus == "Completed" }
            val endedEarly = todaySessions.count { it.sessionStatus == "Ended Early" }
            val expired = todaySessions.count { it.sessionStatus == "Expired" }
            val totalActualSeconds = todaySessions.sumOf { it.actualDurationSeconds }
            val total = completed + endedEarly + expired
            val successRate = if (total == 0) 0 else ((completed + expired) * 100 / total)
            DailyAnalytics(
                totalActualFocusTimeSeconds = totalActualSeconds,
                completedSessions = completed,
                endedEarlySessions = endedEarly,
                expiredSessions = expired,
                successRate = successRate
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyAnalytics())

    val dailyAnalytics: StateFlow<DailyAnalytics> = combine(
        dbDailyAnalytics,
        manualFocusTimeOffset,
        manualCompletedOffset,
        manualEarlyExitsOffset
    ) { db, focusOffset, completedOffset, earlyOffset ->
        val finalCompleted = (db.completedSessions + completedOffset).coerceAtLeast(0)
        val finalEarlyExits = (db.endedEarlySessions + earlyOffset).coerceAtLeast(0)
        val finalFocusTime = (db.totalActualFocusTimeSeconds + focusOffset).coerceAtLeast(0)
        val total = finalCompleted + finalEarlyExits + db.expiredSessions
        val successRate = if (total == 0) 0 else ((finalCompleted + db.expiredSessions) * 100 / total)
        DailyAnalytics(
            totalActualFocusTimeSeconds = finalFocusTime,
            completedSessions = finalCompleted,
            endedEarlySessions = finalEarlyExits,
            expiredSessions = db.expiredSessions,
            successRate = successRate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyAnalytics())

    fun incrementFocusTime(minutes: Int) {
        manualFocusTimeOffset.value += minutes * 60L
    }

    fun decrementFocusTime(minutes: Int) {
        val current = manualFocusTimeOffset.value
        val dbTime = dbDailyAnalytics.value.totalActualFocusTimeSeconds
        if (dbTime + current - (minutes * 60L) >= 0) {
            manualFocusTimeOffset.value -= minutes * 60L
        }
    }

    fun incrementCompletedSessions() {
        manualCompletedOffset.value += 1
    }

    fun decrementCompletedSessions() {
        val current = manualCompletedOffset.value
        val dbCompleted = dbDailyAnalytics.value.completedSessions
        if (dbCompleted + current - 1 >= 0) {
            manualCompletedOffset.value -= 1
        }
    }

    fun incrementEarlyExits() {
        manualEarlyExitsOffset.value += 1
    }

    fun decrementEarlyExits() {
        val current = manualEarlyExitsOffset.value
        val dbEarly = dbDailyAnalytics.value.endedEarlySessions
        if (dbEarly + current - 1 >= 0) {
            manualEarlyExitsOffset.value -= 1
        }
    }

    fun resetDailyCounters() {
        manualFocusTimeOffset.value = 0L
        manualCompletedOffset.value = 0
        manualEarlyExitsOffset.value = 0
    }

    val weeklyTrends: StateFlow<List<TrendPoint>> = repository.allSessions
        .map { sessions ->
            val sdf = java.text.SimpleDateFormat("E", java.util.Locale.getDefault())
            val list = mutableListOf<TrendPoint>()
            for (i in 6 downTo 0) {
                val calendar = java.util.Calendar.getInstance()
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -i)
                
                val startOfDay = calendar.apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val endOfDay = calendar.apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 23)
                    set(java.util.Calendar.MINUTE, 59)
                    set(java.util.Calendar.SECOND, 59)
                    set(java.util.Calendar.MILLISECOND, 999)
                }.timeInMillis
                
                val dayLabel = sdf.format(calendar.time)
                val daySessions = sessions.filter { it.startTime in startOfDay..endOfDay }
                
                val completed = daySessions.count { it.sessionStatus == "Completed" }
                val endedEarly = daySessions.count { it.sessionStatus == "Ended Early" }
                val expired = daySessions.count { it.sessionStatus == "Expired" }
                val totalActualSeconds = daySessions.sumOf { it.actualDurationSeconds }
                
                val total = completed + endedEarly + expired
                val successRate = if (total == 0) 0 else ((completed + expired) * 100 / total)
                val actualMinutes = totalActualSeconds / 60f
                
                list.add(TrendPoint(dayLabel, successRate, actualMinutes))
            }
            list
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<FocusSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val maturedSessions: StateFlow<List<FocusSession>> = repository.maturedSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val advancedAnalytics: StateFlow<AdvancedAnalytics> = repository.allSessions
        .map { sessions ->
            val nowCal = java.util.Calendar.getInstance()
            val todayStart = nowCal.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val todayEnd = nowCal.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }.timeInMillis

            val weekCal = java.util.Calendar.getInstance()
            weekCal.add(java.util.Calendar.DAY_OF_YEAR, -6)
            val weekStart = weekCal.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val monthCal = java.util.Calendar.getInstance()
            monthCal.add(java.util.Calendar.DAY_OF_YEAR, -29)
            val monthStart = monthCal.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todaySessions = sessions.filter { it.startTime in todayStart..todayEnd }
            val todayFocusTimeSeconds = todaySessions.sumOf { it.actualDurationSeconds }
            val todaySessionsCompleted = todaySessions.count { it.sessionStatus == "Completed" || it.sessionStatus == "Expired" }
            val todaySessionsEndedEarly = todaySessions.count { it.sessionStatus == "Ended Early" }

            val weeklySessions = sessions.filter { it.startTime in weekStart..System.currentTimeMillis() }
            val weeklyActualSeconds = weeklySessions.sumOf { it.actualDurationSeconds }
            val weeklyFocusHours = weeklyActualSeconds / 3600f
            val weeklySessionsCount = weeklySessions.size
            val weeklyCompleted = weeklySessions.count { it.sessionStatus == "Completed" || it.sessionStatus == "Expired" }
            val weeklySuccessRate = if (weeklySessionsCount == 0) 0 else (weeklyCompleted * 100 / weeklySessionsCount)

            val monthlySessions = sessions.filter { it.startTime in monthStart..System.currentTimeMillis() }
            val monthlyActualSeconds = monthlySessions.sumOf { it.actualDurationSeconds }
            val monthlyFocusHours = monthlyActualSeconds / 3600f
            val longestSessionSeconds = monthlySessions.maxOfOrNull { it.actualDurationSeconds } ?: 0L
            val longestSessionMinutes = (longestSessionSeconds / 60L).toInt()

            val sdfDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val completedDaysSet = sessions
                .filter { it.sessionStatus == "Completed" || it.sessionStatus == "Expired" }
                .map { sdfDate.format(java.util.Date(it.startTime)) }
                .toSet()

            val totalFocusDays = completedDaysSet.size
            val todayStr = sdfDate.format(java.util.Date())
            var currentStreak = 0
            var longestStreak = 0
            
            if (completedDaysSet.isNotEmpty()) {
                val sortedDates = completedDaysSet.map { sdfDate.parse(it)!! }.sorted()
                val yesterdayCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
                val yesterdayStr = sdfDate.format(yesterdayCal.time)
                
                val checkCal = java.util.Calendar.getInstance()
                var streakCount = 0
                if (completedDaysSet.contains(todayStr)) {
                    streakCount = 1
                    checkCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                    while (completedDaysSet.contains(sdfDate.format(checkCal.time))) {
                        streakCount++
                        checkCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                    }
                } else if (completedDaysSet.contains(yesterdayStr)) {
                    streakCount = 1
                    checkCal.add(java.util.Calendar.DAY_OF_YEAR, -2)
                    while (completedDaysSet.contains(sdfDate.format(checkCal.time))) {
                        streakCount++
                        checkCal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                    }
                }
                currentStreak = streakCount

                if (sortedDates.isNotEmpty()) {
                    val firstDate = sortedDates.first()
                    val lastDate = sortedDates.last()
                    
                    val loopCal = java.util.Calendar.getInstance().apply { time = firstDate }
                    val targetCal = java.util.Calendar.getInstance().apply { time = lastDate }
                    
                    var currentRun = 0
                    while (!loopCal.after(targetCal)) {
                        val dateStr = sdfDate.format(loopCal.time)
                        if (completedDaysSet.contains(dateStr)) {
                            currentRun++
                            if (currentRun > longestStreak) {
                                longestStreak = currentRun
                            }
                        } else {
                            currentRun = 0
                        }
                        loopCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }

            val monthlyCompleted = monthlySessions.count { it.sessionStatus == "Completed" || it.sessionStatus == "Expired" }
            val monthlyEndedEarly = monthlySessions.count { it.sessionStatus == "Ended Early" }
            val monthlyActualMinutes = monthlyActualSeconds / 60f
            val rawScore = (monthlyCompleted * 15 + monthlyActualMinutes * 1.5f - monthlyEndedEarly * 10).toInt()
            val productivityScore = rawScore.coerceIn(0, 100)

            val calendarDays = mutableListOf<CalendarDay>()
            val sdfDayOfWeek = java.text.SimpleDateFormat("E", java.util.Locale.getDefault())
            
            for (i in 27 downTo 0) {
                val loopCal = java.util.Calendar.getInstance()
                loopCal.add(java.util.Calendar.DAY_OF_YEAR, -i)
                val dateStr = sdfDate.format(loopCal.time)
                
                val daySessions = sessions.filter { sdfDate.format(java.util.Date(it.startTime)) == dateStr }
                val hasCompleted = daySessions.any { it.sessionStatus == "Completed" || it.sessionStatus == "Expired" }
                val hasMissed = daySessions.isNotEmpty() && !hasCompleted
                
                calendarDays.add(
                    CalendarDay(
                        dateString = dateStr,
                        dayOfMonth = loopCal.get(java.util.Calendar.DAY_OF_MONTH),
                        dayOfWeek = sdfDayOfWeek.format(loopCal.time),
                        isProductive = hasCompleted,
                        isMissed = hasMissed,
                        isToday = dateStr == todayStr
                    )
                )
            }

            AdvancedAnalytics(
                todayFocusTimeSeconds = todayFocusTimeSeconds,
                todaySessionsCompleted = todaySessionsCompleted,
                todaySessionsEndedEarly = todaySessionsEndedEarly,
                weeklyFocusHours = weeklyFocusHours,
                weeklySessionsCount = weeklySessionsCount,
                weeklySuccessRate = weeklySuccessRate,
                monthlyFocusHours = monthlyFocusHours,
                longestSessionMinutes = longestSessionMinutes,
                longestStreak = longestStreak,
                currentStreak = currentStreak,
                totalFocusDays = totalFocusDays,
                productivityScore = productivityScore,
                calendarDays = calendarDays
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdvancedAnalytics())

    init {
        com.example.ui.helper.SmartNightModeManager.startMonitoring(viewModelScope)
        loadInstalledApps()
        checkAndCleanupExpiredSession()
        startExpiredBlocksDeactivationLoop()

        // Observe session completion to trigger reflection prompt
        viewModelScope.launch {
            repository.sessionCompletedNaturally.collect { completed ->
                if (completed) {
                    _showReflectionPrompt.value = true
                }
            }
        }

        // Load accent theme setting from the database
        viewModelScope.launch {
            repository.getSetting("accent_theme")?.let { savedTheme ->
                accentTheme.value = savedTheme
            }
        }

        // Track Strict Mode session transitions to back up / restore setting states
        viewModelScope.launch {
            var wasStrictModeActive = false
            activeSession.collect { session ->
                val now = System.currentTimeMillis()
                val isStrictActive = session?.let { it.isActive && it.isStrict && now < it.endTime } ?: false
                
                if (isStrictActive && !wasStrictModeActive) {
                    // Strict Mode just started! Save the state.
                    val prefs = context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("saved_youtube_block_shorts", youtubeBlockShorts.value)
                        .putBoolean("saved_instagram_block_reels", instagramBlockReels.value)
                        .putBoolean("saved_snapchat_block_spotlight", snapchatBlockSpotlight.value)
                        .apply()
                } else if (!isStrictActive && wasStrictModeActive) {
                    // Strict Mode just ended! Restore the saved state.
                    val prefs = context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
                    val savedShorts = prefs.getBoolean("saved_youtube_block_shorts", youtubeBlockShorts.value)
                    val savedReels = prefs.getBoolean("saved_instagram_block_reels", instagramBlockReels.value)
                    val savedSpotlight = prefs.getBoolean("saved_snapchat_block_spotlight", snapchatBlockSpotlight.value)
                    
                    youtubeBlockShorts.value = savedShorts
                    instagramBlockReels.value = savedReels
                    snapchatBlockSpotlight.value = savedSpotlight
                    
                    prefs.edit()
                        .putBoolean("youtube_block_shorts", savedShorts)
                        .putBoolean("instagram_block_reels", savedReels)
                        .putBoolean("snapchat_block_spotlight", savedSpotlight)
                        .apply()
                }
                wasStrictModeActive = isStrictActive
            }
        }


    }

    private fun checkAndCleanupExpiredSession() {
        viewModelScope.launch {
            val active = repository.getActiveSessionSync()
            if (active != null && active.isActive && System.currentTimeMillis() >= active.endTime) {
                repository.stopActiveSession("Expired")
            }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            val apps = withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
                    resolvedInfos.map { info ->
                        val packageName = info.activityInfo.packageName
                        val appName = info.loadLabel(pm).toString()
                        AppInfo(packageName, appName)
                    }.distinctBy { it.packageName }.sortedBy { it.appName }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun toggleAppBlocked(app: AppInfo) {
        viewModelScope.launch {
            val active = repository.getActiveSessionSync()
            // If strict mode focus session is active, changes are completely blocked
            val isStrictSessionActive = active?.let { it.isActive && it.isStrict && System.currentTimeMillis() < it.endTime } ?: false
            if (isStrictSessionActive) {
                return@launch
            }
            if (app.isBlocked) {
                repository.removeBlockedApp(app.packageName)
            } else {
                repository.addBlockedApp(BlockedApp(app.packageName, app.appName))
            }
        }
    }

    fun startFocusSession(minutes: Int, isStrict: Boolean, totalMs: Long? = null) {
        viewModelScope.launch {
            repository.startFocusSession(minutes, isStrict, totalMs)
        }
    }

    fun startFocusSessionWithDuration(days: Int, hours: Int, minutes: Int, seconds: Int, isStrict: Boolean) {
        val totalMs = (days.toLong() * 24 * 3600 + hours.toLong() * 3600 + minutes.toLong() * 60 + seconds.toLong()) * 1000L
        val computedMinutes = (totalMs / 60000L).toInt().coerceAtLeast(1)
        viewModelScope.launch {
            repository.startFocusSession(computedMinutes, isStrict, totalMs)
        }
    }

    fun stopActiveSession() {
        viewModelScope.launch {
            repository.stopActiveSession()
        }
    }

    fun deactivateStrictMode() {
        viewModelScope.launch {
            repository.deactivateStrictMode()
        }
    }

    private fun startExpiredBlocksDeactivationLoop() {
        viewModelScope.launch {
            while (true) {
                try {
                    repository.deactivateExpiredBlocks(System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.e("FocusViewModel", "Error deactivating expired blocks", e)
                }
                kotlinx.coroutines.delay(10000L) // Check every 10 seconds
            }
        }
    }

    fun cleanDomain(input: String): String {
        var clean = input.lowercase().trim()
        if (clean.startsWith("http://")) {
            clean = clean.substring(7)
        } else if (clean.startsWith("https://")) {
            clean = clean.substring(8)
        }
        val slashIndex = clean.indexOf('/')
        if (slashIndex != -1) {
            clean = clean.substring(0, slashIndex)
        }
        val colonIndex = clean.indexOf(':')
        if (colonIndex != -1) {
            clean = clean.substring(0, colonIndex)
        }
        if (clean.startsWith("www.")) {
            clean = clean.substring(4)
        }
        return clean
    }

    // Long Term Block operations
    fun addLongTermBlock(
        type: String,
        target: String,
        targetLabel: String,
        reason: String,
        startDate: Long,
        endDate: Long,
        dailyLimitMinutes: Int = 0
    ) {
        viewModelScope.launch {
            val block = LongTermBlock(
                type = type,
                target = target,
                targetLabel = targetLabel,
                reason = reason,
                startDate = startDate,
                endDate = endDate,
                isActive = true,
                dailyLimitMinutes = dailyLimitMinutes
            )
            repository.addLongTermBlock(block)
        }
    }

    fun removeLongTermBlock(id: Int) {
        viewModelScope.launch {
            val block = repository.getLongTermBlockById(id)
            val now = System.currentTimeMillis()
            if (block != null && now >= block.startDate && now <= block.endDate && block.isActive) {
                Log.w("FocusViewModel", "Cannot delete active long-term app block!")
                return@launch
            }
            repository.removeLongTermBlock(id)
        }
    }

    fun addWebsiteBlock(domain: String, reason: String, startDate: Long, endDate: Long) {
        viewModelScope.launch {
            val cleaned = cleanDomain(domain)
            val block = WebsiteBlock(
                domain = cleaned,
                reason = reason,
                startDate = startDate,
                endDate = endDate,
                isActive = true
            )
            repository.addWebsiteBlock(block)
        }
    }

    fun removeWebsiteBlock(id: Int) {
        viewModelScope.launch {
            val block = repository.getWebsiteBlockById(id)
            val now = System.currentTimeMillis()
            if (block != null && now >= block.startDate && now <= block.endDate && block.isActive) {
                Log.w("FocusViewModel", "Cannot delete active website block!")
                return@launch
            }
            repository.removeWebsiteBlock(id)
        }
    }

    fun incrementBlockedLaunches() {
        viewModelScope.launch {
            repository.incrementBlockedLaunches()
        }
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        // 1. Check if the service instance is currently running
        if (com.example.service.FocusAccessibilityService.instance != null) {
            return true
        }

        // 2. Query AccessibilityManager for enabled services matching our package and class name
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        if (am != null) {
            try {
                val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
                    ?: am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    ?: emptyList()
                for (enabledService in enabledServices) {
                    val serviceInfo = enabledService.resolveInfo?.serviceInfo
                    if (serviceInfo != null) {
                        val pkg = serviceInfo.packageName ?: ""
                        val name = serviceInfo.name ?: ""
                        if (pkg == context.packageName && name.endsWith("FocusAccessibilityService")) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FocusViewModel", "Error querying getEnabledAccessibilityServiceList", e)
            }
        }

        // 3. Fallback: Parse Secure settings
        try {
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )
            if (enabled == 1) {
                val settingValue = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                if (!settingValue.isNullOrEmpty()) {
                    val splitter = TextUtils.SimpleStringSplitter(':')
                    splitter.setString(settingValue)
                    while (splitter.hasNext()) {
                        val accessService = splitter.hasNext().let { splitter.next() }
                        if (accessService.contains(context.packageName) && accessService.contains("FocusAccessibilityService")) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FocusViewModel", "Error checking secure settings accessibility list", e)
        }
        return false
    }

    fun isUsageStatsPermissionGranted(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.noteOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun setBatteryOptimizationPromptShown() {
        viewModelScope.launch {
            repository.saveSetting("battery_optimization_prompt_shown", "true")
        }
    }

    suspend fun isBatteryOptimizationPromptShown(): Boolean {
        return repository.getSetting("battery_optimization_prompt_shown") == "true"
    }

    suspend fun saveSetting(key: String, value: String) {
        repository.saveSetting(key, value)
    }

    suspend fun getSetting(key: String): String? {
        return repository.getSetting(key)
    }

    suspend fun getStudyPlanCompletionPercentage(): Float? {
        val plan = getSetting("saved_study_plan")
        if (plan.isNullOrBlank()) return null
        
        val checkedLinesStr = getSetting("study_plan_checked_lines") ?: ""
        val checkedLines = checkedLinesStr.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()

        var totalTasks = 0
        var checkedTasks = 0
        plan.lines().forEachIndexed { index, rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("- ")) {
                totalTasks++
                if (checkedLines.contains(index)) {
                    checkedTasks++
                }
            }
        }
        
        if (totalTasks == 0) return 0f
        return (checkedTasks.toFloat() / totalTasks) * 100f
    }

    fun importTestSchedule(rawText: String, onResult: (com.example.planner.TestImportResult) -> Unit) {
        viewModelScope.launch {
            val result = com.example.planner.TestScheduleParser.parse(rawText)
            if (result.imported.isNotEmpty()) {
                repository.importTests(result.imported)
            }
            onResult(result)
        }
    }

    fun seedTestScheduleIfNeeded() {
        viewModelScope.launch {
            val alreadySeeded = repository.getSetting("test_schedule_seeded_v1")
            if (alreadySeeded == "true") return@launch
            val result = com.example.planner.TestScheduleParser.parse(
                com.example.data.TestScheduleSeedData.rawScheduleText
            )
            if (result.imported.isNotEmpty()) {
                repository.importTests(result.imported)
            }
            repository.saveSetting("test_schedule_seeded_v1", "true")
            com.example.scheduler.TestCountdownScheduler.scheduleNextUpdate(context)
        }
    }

    val allTests: StateFlow<List<TestEntry>> = repository.allTests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteTest(id: Int) {
        viewModelScope.launch {
            repository.deleteTest(id)
        }
    }

    fun deleteAllTests() {
        viewModelScope.launch {
            repository.deleteAllTests()
        }
    }

    val savedStudyPlan: kotlinx.coroutines.flow.Flow<String?> = repository.getSettingFlow("saved_study_plan")

    override fun onCleared() {
        super.onCleared()
    }
}

class FocusViewModelFactory(
    private val repository: FocusRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FocusViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
