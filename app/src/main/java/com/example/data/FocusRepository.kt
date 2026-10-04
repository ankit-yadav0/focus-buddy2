package com.example.data

import com.example.TrustedClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SCHEDULE_STOP_SLACK_MS = 90_000L

class FocusRepository(
    private val blockedAppDao: BlockedAppDao,
    private val focusSessionDao: FocusSessionDao,
    private val longTermBlockDao: LongTermBlockDao,
    private val analyticsDao: AnalyticsDao,
    private val websiteBlockDao: WebsiteBlockDao,
    private val appSettingDao: AppSettingDao,
    private val strictScheduleDao: StrictScheduleDao,
    private val reflectionNoteDao: ReflectionNoteDao,
    private val lockedAppDao: LockedAppDao
) {
    val allLockedApps: Flow<List<LockedApp>> = lockedAppDao.getAllLockedApps()

    val sessionCompletedNaturally = kotlinx.coroutines.flow.MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    // start/stop are read-then-write on the single active-session row. Three callers (service event path,
    // service heartbeat, ViewModel) can close an expired session at the same instant; without this lock both
    // would finalize it and the analytics totals would be added twice.
    private val sessionMutex = Mutex()

    suspend fun addReflectionNote(text: String) {
        reflectionNoteDao.insertNote(ReflectionNote(timestamp = System.currentTimeMillis(), noteText = text))
    }

    val allBlockedApps: Flow<List<BlockedApp>> = blockedAppDao.getAllBlockedApps()
    val activeSession: Flow<FocusSession?> = focusSessionDao.getActiveSession()
    val allSessions: Flow<List<FocusSession>> = focusSessionDao.getAllSessions()
    val maturedSessions: Flow<List<FocusSession>> = focusSessionDao.getMaturedSessions()

    val allLongTermBlocks: Flow<List<LongTermBlock>> = longTermBlockDao.getAllLongTermBlocks()
    val activeLongTermBlocks: Flow<List<LongTermBlock>> = longTermBlockDao.getActiveLongTermBlocks()
    val allWebsiteBlocks: Flow<List<WebsiteBlock>> = websiteBlockDao.getAllWebsiteBlocks()
    val activeWebsiteBlocks: Flow<List<WebsiteBlock>> = websiteBlockDao.getActiveWebsiteBlocks()
    val analytics: Flow<Analytics?> = analyticsDao.getAnalytics()
    val allSchedules: Flow<List<StrictSchedule>> = strictScheduleDao.getAllSchedules()

    suspend fun addSchedule(schedule: StrictSchedule): Long = strictScheduleDao.insertSchedule(schedule)
    suspend fun updateSchedule(schedule: StrictSchedule) = strictScheduleDao.updateSchedule(schedule)
    suspend fun deleteSchedule(id: Int) = strictScheduleDao.deleteSchedule(id)
    suspend fun getScheduleById(id: Int): StrictSchedule? = strictScheduleDao.getScheduleById(id)

    suspend fun startScheduledStrictSession(scheduleId: Int, durationMinutes: Int) {
        val active = focusSessionDao.getActiveSessionSync()
        // A running Strict Mode session is never replaced. A normal session, on the other hand, gives way
        // to the scheduled strict window - previously the window was silently skipped whenever ANY session
        // happened to be running, leaving the whole window unprotected.
        if (active != null && active.isStrict && TrustedClock.now() < active.endTime) return
        startFocusSession(durationMinutes, isStrict = true)
        val inserted = focusSessionDao.getActiveSessionSync()
        if (inserted != null && inserted.isStrict) {
            focusSessionDao.updateSession(inserted.copy(origin = "SCHEDULE:$scheduleId"))
        }
    }

    suspend fun stopScheduledStrictSession(scheduleId: Int) {
        val active = focusSessionDao.getActiveSessionSync()
        if (active != null && active.origin == "SCHEDULE:$scheduleId") {
            // The window-end alarm is a wall-clock alarm, so moving the clock forward makes it fire early.
            // The session itself expires on the trusted clock: only honour the alarm once it is (nearly) over.
            if (active.isStrict && active.endTime - TrustedClock.now() > SCHEDULE_STOP_SLACK_MS) return
            stopActiveSession(force = true)
        }
    }

    suspend fun getBlockedAppsList(): List<BlockedApp> = blockedAppDao.getBlockedAppsList()

    suspend fun getActiveSessionSync(): FocusSession? = focusSessionDao.getActiveSessionSync()

    suspend fun getActiveLongTermBlocksList(): List<LongTermBlock> = longTermBlockDao.getActiveLongTermBlocksList()

    suspend fun getActiveWebsiteBlocksList(): List<WebsiteBlock> = websiteBlockDao.getActiveWebsiteBlocksList()

    suspend fun addBlockedApp(app: BlockedApp) {
        blockedAppDao.insertApp(app)
    }

    suspend fun removeBlockedApp(packageName: String) {
        blockedAppDao.deleteApp(packageName)
    }

    suspend fun isAppBlocked(packageName: String): Boolean {
        return blockedAppDao.isAppBlocked(packageName)
    }

    suspend fun startFocusSession(durationMinutes: Int, isStrict: Boolean, totalMs: Long? = null): Unit = sessionMutex.withLock {
        val now = TrustedClock.now()
        val active = focusSessionDao.getActiveSessionSync()
        if (active != null) {
            // A running Strict Mode session can never be replaced: starting a fresh (non-strict) session
            // over it would skip the override challenge, the cooldown and the password gate entirely.
            if (active.isStrict && now < active.endTime) return@withLock
            finalizeSession(active, if (now >= active.endTime) "Expired" else "Ended Early", now)
        }

        val actualTotalMs = totalMs ?: (durationMinutes * 60 * 1000L)
        val endTime = now + actualTotalMs
        val computedMinutes = (actualTotalMs / 60000L).toInt().coerceAtLeast(1)
        val session = FocusSession(
            startTime = now,
            durationMinutes = computedMinutes,
            endTime = endTime,
            actualEndTime = 0L,
            plannedDurationMinutes = computedMinutes,
            actualDurationSeconds = 0L,
            // "Active" until it is finalized. It used to be inserted as "Completed", so every session
            // counted as a completed one (and bumped the streak) the moment it started.
            sessionStatus = "Active",
            isActive = true,
            isStrict = isStrict,
            plantStatus = "SEED",
            assetPath = "img_plant_seed"
        )
        focusSessionDao.insertSession(session)
    }

    /**
     * Ends the active session. A running Strict Mode session cannot be ended early through this call
     * (the UI hides the button, but the rule has to hold here too): it only ends when its timer runs
     * out, or via the Extreme Override flow, which clears isStrict first. [force] is for the
     * schedule-driven window end.
     */
    suspend fun stopActiveSession(status: String? = null, force: Boolean = false): Unit = sessionMutex.withLock {
        val active = focusSessionDao.getActiveSessionSync() ?: return@withLock
        val now = TrustedClock.now()
        if (!force && status == null && active.isStrict && now < active.endTime) return@withLock
        finalizeSession(active, status, now)
    }

    private suspend fun finalizeSession(active: FocusSession, status: String?, now: Long) {
        val isCompleted = now >= active.endTime
        val finalStatus = status ?: if (isCompleted) "Completed" else "Ended Early"

        // Actual duration in seconds, never more than what was planned
        val maxSeconds = ((active.endTime - active.startTime) / 1000L).coerceAtLeast(0L)
        val actualDurationSeconds = ((now - active.startTime) / 1000L).coerceAtLeast(0L).coerceAtMost(maxSeconds)

        val matured = finalStatus == "Completed" || finalStatus == "Expired"
        focusSessionDao.updateSession(
            active.copy(
                isActive = false,
                actualEndTime = now,
                actualDurationSeconds = actualDurationSeconds,
                sessionStatus = finalStatus,
                plantStatus = if (matured) "MATURED" else "WITHERED",
                assetPath = if (matured) "img_plant_matured" else "img_plant_withered"
            )
        )
        if (finalStatus == "Completed") {
            sessionCompletedNaturally.tryEmit(true)
        }

        // Static analytics totals, using ACTUAL duration only. Atomic SQL increments - the old
        // read-modify-write could lose an update when two writers overlapped.
        analyticsDao.insertIfAbsent(Analytics())
        analyticsDao.addSessionResult(
            completed = if (finalStatus == "Completed") 1 else 0,
            minutes = actualDurationSeconds / 60L
        )
    }

    // Long Term Block operations
    suspend fun addLongTermBlock(block: LongTermBlock) {
        longTermBlockDao.insertBlock(block)
    }

    suspend fun removeLongTermBlock(id: Int) {
        longTermBlockDao.deleteBlockById(id)
    }

    suspend fun getLongTermBlockById(id: Int): LongTermBlock? {
        return longTermBlockDao.getBlockById(id)
    }

    suspend fun getAllLongTermBlocksList(): List<LongTermBlock> {
        return longTermBlockDao.getAllLongTermBlocksList()
    }

    suspend fun getActiveQuotaBlockForPackage(packageName: String): LongTermBlock? {
        return longTermBlockDao.getActiveQuotaBlockForPackage(packageName)
    }

    suspend fun updateLongTermBlockUsage(id: Int, usedSeconds: Long, usedMillis: Long, epochDay: Long) {
        longTermBlockDao.updateUsage(id, usedSeconds, usedMillis, epochDay)
    }

    suspend fun addWebsiteBlock(block: WebsiteBlock) {
        websiteBlockDao.insertBlock(block)
    }

    suspend fun removeWebsiteBlock(id: Int) {
        websiteBlockDao.deleteBlockById(id)
    }

    suspend fun getWebsiteBlockById(id: Int): WebsiteBlock? {
        return websiteBlockDao.getBlockById(id)
    }

    suspend fun getAllWebsiteBlocksList(): List<WebsiteBlock> {
        return websiteBlockDao.getAllWebsiteBlocksList()
    }

    suspend fun deactivateExpiredBlocks(now: Long) {
        longTermBlockDao.deactivateExpiredBlocks(now)
        websiteBlockDao.deactivateExpiredBlocks(now)
    }

    // Analytics operations
    suspend fun incrementBlockedLaunches() {
        analyticsDao.insertIfAbsent(Analytics())
        analyticsDao.incrementBlockedLaunches()
    }

    // Only ever called from the "Extreme Override" deactivation flow, which itself
    // only exists when that method was explicitly chosen at Strict Mode activation
    // time (see StrictModeSetupWizardScreen / FocusViewModel.deactivateStrictModeViaOverride).
    suspend fun deactivateStrictModeOverride() {
        val active = focusSessionDao.getActiveSessionSync()
        if (active != null && active.isActive && active.isStrict) {
            focusSessionDao.updateSession(active.copy(isStrict = false))
        }
    }

    suspend fun insertSession(session: FocusSession) {
        focusSessionDao.insertSession(session)
    }

    suspend fun getSetting(key: String): String? {
        return appSettingDao.getSettingValue(key)
    }

    fun getSettingFlow(key: String): Flow<String?> {
        return appSettingDao.getSettingValueFlow(key)
    }

    suspend fun saveSetting(key: String, value: String) {
        appSettingDao.insertSetting(AppSetting(key, value))
    }

    // App Lock (PIN-gated apps) operations
    suspend fun getLockedAppsList(): List<LockedApp> {
        return lockedAppDao.getLockedAppsList()
    }

    suspend fun addLockedApp(app: LockedApp) {
        lockedAppDao.insertApp(app)
    }

    suspend fun removeLockedApp(packageName: String) {
        lockedAppDao.deleteApp(packageName)
    }

    suspend fun isAppLocked(packageName: String): Boolean {
        return lockedAppDao.isAppLocked(packageName)
    }
}
