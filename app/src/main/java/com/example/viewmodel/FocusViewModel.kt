package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BlockedApp
import com.example.data.LockedApp
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
import kotlinx.coroutines.flow.MutableSharedFlow
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

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean = false,
    val isLocked: Boolean = false
)

/** App entry for the launcher home/drawer screens - carries a real launcher icon,
 * unlike [AppInfo] which only backs the (icon-less) app-blocking selection list. */
data class LauncherAppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.Bitmap?
)

/** One entry in the launcher's "Today" usage mini-list - real system-wide foreground
 * time via UsageStatsManager, not limited to apps with a Focuss Buddy quota set up. */
data class AppUsageEntry(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.Bitmap?,
    val timeMillis: Long
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

    // Launcher icons are only ever displayed at 36dp (drawer/home grid) or 28dp
    // (pinned row). Decoding every installed app's icon at its full intrinsic
    // adaptive-icon resolution (often 300px+ per icon on modern densities) and
    // keeping all of them resident as ARGB_8888 Bitmaps was the actual cause of
    // the launcher lag on 2GB RAM devices - with 100-300 apps installed that's
    // tens of MB of icon bitmaps alone, plus GC churn every time the list reloads.
    // Decoding directly at display size cuts per-icon memory by roughly 10-20x.
    private val launcherIconPx: Int by lazy {
        (48f * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    }

    val youtubeBlockShorts = MutableStateFlow(false)
    val instagramBlockReels = MutableStateFlow(false)
    val snapchatBlockSpotlight = MutableStateFlow(false)
    val accentTheme = MutableStateFlow("Phosphor Mint")

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    // Emits a short user-facing message whenever an action (add/remove a
    // long-term or website block) is skipped because Strict Mode's rule-editing
    // lock is on. Previously these failed completely silently - the dialog would
    // close and everything looked like it worked, but nothing was saved, which
    // is exactly what made it look like the app "couldn't add" more blocks.
    val actionBlockedMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            val savedYoutubeShorts = prefs.getBoolean("youtube_block_shorts", false)
            val savedInstagramReels = prefs.getBoolean("instagram_block_reels", false)
            val savedSnapchatSpotlight = prefs.getBoolean("snapchat_block_spotlight", false)
            val savedAccentTheme = prefs.getString("accent_theme", "Phosphor Mint") ?: "Phosphor Mint"

            youtubeBlockShorts.value = savedYoutubeShorts
            instagramBlockReels.value = savedInstagramReels
            snapchatBlockSpotlight.value = savedSnapchatSpotlight
            accentTheme.value = savedAccentTheme

            _isInitialized.value = true
        }
    }

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    private val _launcherApps = MutableStateFlow<List<LauncherAppInfo>>(emptyList())
    val launcherApps = _launcherApps.asStateFlow()

    private val _isLoadingLauncherApps = MutableStateFlow(false)
    val isLoadingLauncherApps = _isLoadingLauncherApps.asStateFlow()

    /** Comma-separated package names pinned to the Launcher Mode study-apps grid. */
    val studyAppPackages: StateFlow<Set<String>> = repository.getSettingFlow("launcher_study_apps")
        .map { raw -> raw?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

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

    val lockedApps: StateFlow<List<LockedApp>> = repository.allLockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appLockListState: StateFlow<List<AppInfo>> = combine(_installedApps, lockedApps) { installed, locked ->
        val lockedPackages = locked.map { it.packageName }.toSet()
        installed.map { app ->
            app.copy(isLocked = lockedPackages.contains(app.packageName))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Phase 2 flows
    val allLongTermBlocks: StateFlow<List<LongTermBlock>> = repository.allLongTermBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeLongTermBlocks: StateFlow<List<LongTermBlock>> = repository.activeLongTermBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Package names that are locked right now - for the launcher's lock badges, not for
     * enforcement (the accessibility service is the source of truth there). Combines
     * plain blocked apps with quota-mode long-term blocks whose daily allowance is
     * already used up for today (a quota block with time still left isn't locked yet).
     */
    val lockedAppPackages: StateFlow<Set<String>> = combine(blockedApps, activeLongTermBlocks) { blocked, longTerm ->
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
        val fromBlockedApps = blocked.filter { it.isBlocked }.map { it.packageName }
        val fromLongTerm = longTerm.filter { block ->
            block.type == "APP" && (
                block.dailyLimitSeconds == null ||
                (block.lastUsageResetEpochDay == today && block.usedSecondsToday >= block.dailyLimitSeconds)
            )
        }.map { it.target }
        (fromBlockedApps + fromLongTerm).toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** packageName -> (usedSecondsToday, dailyLimitSeconds) for the launcher's per-app
     * quota progress ring. Only covers apps with a quota-mode long-term block set up. */
    val appQuotaInfo: StateFlow<Map<String, Pair<Long, Long>>> = activeLongTermBlocks
        .map { blocks ->
            blocks.filter { it.type == "APP" && it.dailyLimitSeconds != null }
                .associate { it.target to (it.usedSecondsToday to (it.dailyLimitSeconds ?: 0L)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Most-recently-launched-from-launcher packages, newest first, capped at 5. Local
     * tracking (not system usage stats) so it works without the usage-access permission
     * and only reflects apps actually opened through Launcher Mode. */
    val recentAppPackages: StateFlow<List<String>> = repository.getSettingFlow("launcher_recent_apps")
        .map { raw -> raw?.split(",")?.filter { it.isNotBlank() } ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun recordRecentApp(packageName: String) {
        viewModelScope.launch {
            val current = recentAppPackages.value.toMutableList()
            current.remove(packageName)
            current.add(0, packageName)
            repository.saveSetting("launcher_recent_apps", current.take(5).joinToString(","))
        }
    }

    val allWebsiteBlocks: StateFlow<List<WebsiteBlock>> = repository.allWebsiteBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWebsiteBlocks: StateFlow<List<WebsiteBlock>> = repository.activeWebsiteBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val analytics: StateFlow<Analytics?> = repository.analytics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allSchedules: StateFlow<List<StrictSchedule>> = repository.allSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Strict Mode setup wizard: Restriction Editing lock ---
    // "Restrict All" locks every editable category below while a strict session is
    // running. "Restrict Specific" locks only the categories the user opted into at
    // activation time. The blocked-apps list itself is always locked during any
    // strict session regardless of this setting (see toggleAppBlocked) - that
    // predates the wizard and was never configurable.
    data class StrictEditLock(val rulesLocked: Boolean, val schedulesLocked: Boolean)

    val strictEditLock: StateFlow<StrictEditLock> = combine(
        isStrictModeActive,
        repository.getSettingFlow("strict_restriction_editing_mode").map { it ?: "ALL" },
        repository.getSettingFlow("strict_restrict_rules_specific").map { it?.toBoolean() ?: true },
        repository.getSettingFlow("strict_restrict_schedules_specific").map { it?.toBoolean() ?: true }
    ) { strictActive, mode, restrictRules, restrictSchedules ->
        StrictEditLock(
            rulesLocked = strictActive && (mode == "ALL" || restrictRules),
            schedulesLocked = strictActive && (mode == "ALL" || restrictSchedules)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StrictEditLock(false, false))

    private suspend fun isRulesEditingLocked(): Boolean {
        val active = repository.getActiveSessionSync()
        val strictActive = active?.let { it.isActive && it.isStrict && System.currentTimeMillis() < it.endTime } ?: false
        if (!strictActive) return false
        val mode = repository.getSetting("strict_restriction_editing_mode") ?: "ALL"
        if (mode == "ALL") return true
        return repository.getSetting("strict_restrict_rules_specific")?.toBoolean() ?: true
    }

    private suspend fun isSchedulesEditingLocked(): Boolean {
        val active = repository.getActiveSessionSync()
        val strictActive = active?.let { it.isActive && it.isStrict && System.currentTimeMillis() < it.endTime } ?: false
        if (!strictActive) return false
        val mode = repository.getSetting("strict_restriction_editing_mode") ?: "ALL"
        if (mode == "ALL") return true
        return repository.getSetting("strict_restrict_schedules_specific")?.toBoolean() ?: true
    }

    // --- Strict Mode setup wizard: config persistence ---
    data class StrictModeWizardConfig(
        val restrictionEditingMode: String = "ALL",     // "ALL" | "SPECIFIC"
        val restrictRulesSpecific: Boolean = true,
        val restrictSchedulesSpecific: Boolean = true,
        val restrictUninstall: Boolean = false,
        val restrictSettings: Boolean = false,
        val requirePassword: Boolean = false,
        val deactivationMethod: String = "TIME_ONLY"    // "TIME_ONLY" | "EXTREME_OVERRIDE"
    )

    suspend fun getStrictModeWizardConfig(): StrictModeWizardConfig {
        return StrictModeWizardConfig(
            restrictionEditingMode = repository.getSetting("strict_restriction_editing_mode") ?: "ALL",
            restrictRulesSpecific = repository.getSetting("strict_restrict_rules_specific")?.toBoolean() ?: true,
            restrictSchedulesSpecific = repository.getSetting("strict_restrict_schedules_specific")?.toBoolean() ?: true,
            restrictUninstall = repository.getSetting("strict_restrict_uninstall")?.toBoolean() ?: false,
            restrictSettings = repository.getSetting("strict_restrict_settings")?.toBoolean() ?: false,
            requirePassword = repository.getSetting("strict_require_password")?.toBoolean() ?: false,
            deactivationMethod = repository.getSetting("strict_deactivation_method") ?: "TIME_ONLY"
        )
    }

    suspend fun saveStrictModeWizardConfig(config: StrictModeWizardConfig) {
        val active = repository.getActiveSessionSync()
        val strictActive = active?.let { it.isActive && it.isStrict && System.currentTimeMillis() < it.endTime } ?: false
        if (strictActive) return
        repository.saveSetting("strict_restriction_editing_mode", config.restrictionEditingMode)
        repository.saveSetting("strict_restrict_rules_specific", config.restrictRulesSpecific.toString())
        repository.saveSetting("strict_restrict_schedules_specific", config.restrictSchedulesSpecific.toString())
        repository.saveSetting("strict_restrict_uninstall", config.restrictUninstall.toString())
        repository.saveSetting("strict_restrict_settings", config.restrictSettings.toString())
        repository.saveSetting("strict_require_password", config.requirePassword.toString())
        repository.saveSetting("strict_deactivation_method", config.deactivationMethod)
    }

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun hasStrictModePassword(): Boolean {
        return !repository.getSetting("strict_password_hash").isNullOrBlank()
    }

    suspend fun setStrictModePassword(password: String) {
        repository.saveSetting("strict_password_hash", sha256(password))
    }

    suspend fun clearStrictModePassword() {
        repository.saveSetting("strict_password_hash", "")
    }

    suspend fun verifyStrictModePassword(password: String): Boolean {
        val stored = repository.getSetting("strict_password_hash") ?: return false
        if (stored.isBlank()) return false
        return stored == sha256(password)
    }

    /** True right now if a strict session is active AND the password gate is configured. */
    suspend fun isPasswordGateActive(): Boolean {
        val active = repository.getActiveSessionSync()
        val strictActive = active?.let { it.isActive && it.isStrict && System.currentTimeMillis() < it.endTime } ?: false
        if (!strictActive) return false
        val requirePassword = repository.getSetting("strict_require_password")?.toBoolean() ?: false
        return requirePassword && hasStrictModePassword()
    }

    fun addSchedule(context: Context, schedule: StrictSchedule) {
        viewModelScope.launch {
            if (isSchedulesEditingLocked()) return@launch
            val id = repository.addSchedule(schedule)
            if (schedule.isEnabled) {
                AlarmScheduler.scheduleWindow(context, schedule.copy(id = id.toInt()))
            }
        }
    }

    fun updateSchedule(context: Context, schedule: StrictSchedule) {
        viewModelScope.launch {
            if (isSchedulesEditingLocked()) return@launch
            repository.updateSchedule(schedule)
            AlarmScheduler.cancelWindow(context, schedule.id)
            if (schedule.isEnabled) {
                AlarmScheduler.scheduleWindow(context, schedule)
            }
        }
    }

    fun deleteSchedule(context: Context, schedule: StrictSchedule) {
        viewModelScope.launch {
            if (isSchedulesEditingLocked()) return@launch
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

    /**
     * Same launchable-app query as [loadInstalledApps], but also decodes each app's real
     * icon to a Bitmap for the Launcher Mode home/drawer screens. Kept as a separate,
     * on-demand load (rather than folded into [loadInstalledApps]) since icon decoding is
     * heavier and the app-blocking selection list never needs real icons.
     */
    fun loadLauncherApps() {
        viewModelScope.launch {
            _isLoadingLauncherApps.value = true
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
                        val icon = try {
                            info.loadIcon(pm).toBitmap(width = launcherIconPx, height = launcherIconPx)
                        } catch (e: Exception) {
                            null
                        }
                        LauncherAppInfo(packageName, appName, icon)
                    }.distinctBy { it.packageName }.sortedBy { it.appName }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _launcherApps.value = apps
            _isLoadingLauncherApps.value = false
        }
    }

    /** Pins/unpins [packageName] on the Launcher Mode home screen's study-apps grid. */
    fun toggleStudyApp(packageName: String) {
        viewModelScope.launch {
            val current = studyAppPackages.value
            val updated = if (packageName in current) current - packageName else current + packageName
            repository.saveSetting("launcher_study_apps", updated.joinToString(","))
        }
    }

    private val _todayTopApps = MutableStateFlow<List<AppUsageEntry>>(emptyList())
    val todayTopApps = _todayTopApps.asStateFlow()

    private val _isLoadingTodayTopApps = MutableStateFlow(false)
    val isLoadingTodayTopApps = _isLoadingTodayTopApps.asStateFlow()

    /**
     * Top-3 apps by real foreground time so far today, for the launcher's self-awareness
     * mini-list. Uses UsageStatsManager (system-wide, every app) rather than Focuss
     * Buddy's own quota tracking, which only covers apps someone has explicitly set a
     * long-term block/quota on. Silently returns an empty list if usage-access hasn't
     * been granted yet - callers show their own "grant access" prompt for that case via
     * [isUsageStatsPermissionGranted].
     */
    fun loadTodayTopApps() {
        if (!isUsageStatsPermissionGranted()) {
            _todayTopApps.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoadingTodayTopApps.value = true
            val entries = withContext(Dispatchers.IO) {
                try {
                    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                    val calendar = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val startOfDay = calendar.timeInMillis
                    val now = System.currentTimeMillis()
                    val stats = usm.queryUsageStats(
                        android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                        startOfDay,
                        now
                    ) ?: emptyList()

                    val pm = context.packageManager
                    val ourPackage = context.packageName
                    stats
                        .filter { it.totalTimeInForeground > 0 && it.packageName != ourPackage }
                        .sortedByDescending { it.totalTimeInForeground }
                        .take(3)
                        .mapNotNull { stat ->
                            try {
                                val appInfo = pm.getApplicationInfo(stat.packageName, 0)
                                val appName = pm.getApplicationLabel(appInfo).toString()
                                val icon = try {
                                    pm.getApplicationIcon(stat.packageName).toBitmap(width = launcherIconPx, height = launcherIconPx)
                                } catch (e: Exception) {
                                    null
                                }
                                AppUsageEntry(stat.packageName, appName, icon, stat.totalTimeInForeground)
                            } catch (e: Exception) {
                                null
                            }
                        }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            _todayTopApps.value = entries
            _isLoadingTodayTopApps.value = false
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

    // --- App Lock (PIN-gated apps) ---

    suspend fun hasAppLockPin(): Boolean {
        return repository.getSetting("app_lock_pin_hash") != null
    }

    suspend fun setAppLockPin(pin: String) {
        repository.saveSetting("app_lock_pin_hash", sha256(pin))
    }

    suspend fun verifyAppLockPin(pin: String): Boolean {
        val stored = repository.getSetting("app_lock_pin_hash") ?: return false
        return stored == sha256(pin)
    }

    fun toggleAppLocked(app: AppInfo) {
        viewModelScope.launch {
            if (app.isLocked) {
                repository.removeLockedApp(app.packageName)
            } else {
                repository.addLockedApp(LockedApp(app.packageName, app.appName))
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

    // Strict Mode has no in-app bypass by default: once a strict session starts, it
    // can only end when its timer reaches zero - UNLESS "Extreme Override" was
    // explicitly chosen as the deactivation method in the setup wizard, in which
    // case the flow below (300-word typing + 45-min cooldown + night blackout,
    // enforced by StrictOverrideScreen) is the only path in, and even it is
    // completely unavailable 10 PM-6 AM.

    val strictBypassRequestedAt: StateFlow<Long> = repository.getSettingFlow("strict_bypass_requested_at")
        .map { it?.toLongOrNull() ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    suspend fun getDeactivationMethod(): String = repository.getSetting("strict_deactivation_method") ?: "TIME_ONLY"

    val deactivationMethod: StateFlow<String> = repository.getSettingFlow("strict_deactivation_method")
        .map { it ?: "TIME_ONLY" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "TIME_ONLY")

    fun requestStrictModeOverride() {
        viewModelScope.launch {
            repository.saveSetting("strict_bypass_requested_at", System.currentTimeMillis().toString())
        }
    }

    fun cancelStrictModeOverrideRequest() {
        viewModelScope.launch {
            repository.saveSetting("strict_bypass_requested_at", "0")
        }
    }

    fun deactivateStrictModeViaOverride() {
        viewModelScope.launch {
            val method = repository.getSetting("strict_deactivation_method") ?: "TIME_ONLY"
            if (method != "EXTREME_OVERRIDE") return@launch
            repository.deactivateStrictModeOverride()
            repository.saveSetting("strict_bypass_requested_at", "0")
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
        dailyLimitSeconds: Long? = null
    ) {
        viewModelScope.launch {
            if (isRulesEditingLocked()) {
                actionBlockedMessage.tryEmit("Can't add new blocks while a Strict Mode session is running. Rule changes are locked until it ends.")
                return@launch
            }
            val block = LongTermBlock(
                type = type,
                target = target,
                targetLabel = targetLabel,
                reason = reason,
                startDate = startDate,
                endDate = endDate,
                isActive = true,
                dailyLimitSeconds = dailyLimitSeconds
            )
            repository.addLongTermBlock(block)
        }
    }

    fun removeLongTermBlock(id: Int) {
        viewModelScope.launch {
            if (isRulesEditingLocked()) {
                actionBlockedMessage.tryEmit("Can't remove blocks while a Strict Mode session is running. Rule changes are locked until it ends.")
                return@launch
            }
            val block = repository.getLongTermBlockById(id)
            val now = System.currentTimeMillis()
            if (block != null && now >= block.startDate && now <= block.endDate && block.isActive) {
                Log.w("FocusViewModel", "Cannot delete active long-term app block!")
                actionBlockedMessage.tryEmit("This block is currently active and can't be deleted until it ends.")
                return@launch
            }
            repository.removeLongTermBlock(id)
        }
    }

    fun addWebsiteBlock(domain: String, reason: String, startDate: Long, endDate: Long) {
        viewModelScope.launch {
            if (isRulesEditingLocked()) {
                actionBlockedMessage.tryEmit("Can't add new blocks while a Strict Mode session is running. Rule changes are locked until it ends.")
                return@launch
            }
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
            if (isRulesEditingLocked()) {
                actionBlockedMessage.tryEmit("Can't remove blocks while a Strict Mode session is running. Rule changes are locked until it ends.")
                return@launch
            }
            val block = repository.getWebsiteBlockById(id)
            val now = System.currentTimeMillis()
            if (block != null && now >= block.startDate && now <= block.endDate && block.isActive) {
                Log.w("FocusViewModel", "Cannot delete active website block!")
                actionBlockedMessage.tryEmit("This block is currently active and can't be deleted until it ends.")
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

    /**
     * Parses pipe-separated test schedule text (see BulkImportTestsScreen for the
     * expected format), saves any successfully-parsed rows, and reports back via
     * [onResult] with a summary of what was imported vs. skipped.
     */
    fun importTestSchedule(
        rawText: String,
        onResult: (com.example.planner.TestImportResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = com.example.planner.TestScheduleParser.parse(rawText)
            if (result.imported.isNotEmpty()) {
                repository.importTests(result.imported)
            }
            onResult(result)
        }
    }

    val savedStudyPlan: kotlinx.coroutines.flow.Flow<String?> = repository.getSettingFlow("saved_study_plan")

    // --- PYQ question bank + quiz ---
    suspend fun importPyqQuestions(questions: List<com.example.data.PyqQuestion>) =
        repository.importPyqQuestions(questions)
    suspend fun getPyqQuestionCount(): Int = repository.getPyqQuestionCount()
    suspend fun getAvailablePyqYears(): List<Int> = repository.getAvailablePyqYears()
    suspend fun getMatchingPyqCount(subject: String?, difficulty: String?): Int =
        repository.getMatchingPyqCount(subject, difficulty)
    suspend fun getRandomPyqQuestions(subject: String?, difficulty: String?, limit: Int): List<com.example.data.PyqQuestion> =
        repository.getRandomPyqQuestions(subject, difficulty, limit)
    suspend fun startPyqQuizAttempt(subjectFilter: String, difficultyFilter: String, requestedCount: Int): Int =
        repository.startPyqQuizAttempt(subjectFilter, difficultyFilter, requestedCount)
    suspend fun completePyqQuizAttempt(attemptId: Int, answers: List<com.example.data.PyqQuizAnswer>, totalTimeSeconds: Long) =
        repository.completePyqQuizAttempt(attemptId, answers, totalTimeSeconds)
    suspend fun getPyqAttemptById(id: Int): com.example.data.PyqQuizAttempt? = repository.getPyqAttemptById(id)
    suspend fun getPyqAnswersForAttempt(attemptId: Int): List<com.example.data.PyqQuizAnswer> =
        repository.getPyqAnswersForAttempt(attemptId)
    val allPyqAttempts: kotlinx.coroutines.flow.Flow<List<com.example.data.PyqQuizAttempt>> = repository.allPyqAttempts

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
