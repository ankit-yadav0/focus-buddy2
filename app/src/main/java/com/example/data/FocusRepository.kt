package com.example.data

import kotlinx.coroutines.flow.Flow

class FocusRepository(
    private val blockedAppDao: BlockedAppDao,
    private val focusSessionDao: FocusSessionDao,
    private val longTermBlockDao: LongTermBlockDao,
    private val analyticsDao: AnalyticsDao,
    private val websiteBlockDao: WebsiteBlockDao,
    private val appSettingDao: AppSettingDao,
    private val chatMessageDao: ChatMessageDao,
    private val strictScheduleDao: StrictScheduleDao,
    private val reflectionNoteDao: ReflectionNoteDao,
    private val testEntryDao: TestEntryDao
) {
    val allTests: Flow<List<TestEntry>> = testEntryDao.getAllTests()
    suspend fun getNextTest(): TestEntry? = testEntryDao.getNextTest(System.currentTimeMillis())
    suspend fun importTests(tests: List<TestEntry>) = testEntryDao.insertAll(tests)
    suspend fun deleteTest(id: Int) = testEntryDao.deleteTest(id)
    suspend fun deleteAllTests() = testEntryDao.deleteAll()

    val allReflectionNotes: Flow<List<ReflectionNote>> = reflectionNoteDao.getAllNotes()
    val sessionCompletedNaturally = kotlinx.coroutines.flow.MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
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
    val allChatMessages: Flow<List<ChatMessage>> = chatMessageDao.getAllMessages()
    val allSchedules: Flow<List<StrictSchedule>> = strictScheduleDao.getAllSchedules()

    suspend fun addSchedule(schedule: StrictSchedule): Long = strictScheduleDao.insertSchedule(schedule)
    suspend fun updateSchedule(schedule: StrictSchedule) = strictScheduleDao.updateSchedule(schedule)
    suspend fun deleteSchedule(id: Int) = strictScheduleDao.deleteSchedule(id)
    suspend fun getEnabledSchedulesList(): List<StrictSchedule> = strictScheduleDao.getEnabledSchedulesSync()
    suspend fun getScheduleById(id: Int): StrictSchedule? = strictScheduleDao.getScheduleById(id)

    suspend fun startScheduledStrictSession(scheduleId: Int, durationMinutes: Int) {
        val active = focusSessionDao.getActiveSessionSync()
        if (active != null) return
        startFocusSession(durationMinutes, isStrict = true)
        val inserted = focusSessionDao.getActiveSessionSync()
        if (inserted != null) {
            focusSessionDao.updateSession(inserted.copy(origin = "SCHEDULE:$scheduleId"))
        }
    }

    suspend fun stopScheduledStrictSession(scheduleId: Int) {
        val active = focusSessionDao.getActiveSessionSync()
        if (active != null && active.origin == "SCHEDULE:$scheduleId") {
            stopActiveSession()
        }
    }

    suspend fun addChatMessage(message: ChatMessage) {
        chatMessageDao.insertMessage(message)
    }

    suspend fun clearChatHistory() {
        chatMessageDao.clearHistory()
    }

    suspend fun getBlockedAppsList(): List<BlockedApp> = blockedAppDao.getBlockedAppsList()

    suspend fun getActiveSessionSync(): FocusSession? = focusSessionDao.getActiveSessionSync()

    suspend fun getActiveLongTermBlocksList(): List<LongTermBlock> = longTermBlockDao.getActiveLongTermBlocksList()

    suspend fun getActiveWebsiteBlocksList(): List<WebsiteBlock> = websiteBlockDao.getActiveWebsiteBlocksList()

    suspend fun addBlockedApp(app: BlockedApp) {
        blockedAppDao.insertApp(app)
    }

    suspend fun addBlockedApps(apps: List<BlockedApp>) {
        blockedAppDao.insertApps(apps)
    }

    suspend fun removeBlockedApp(packageName: String) {
        blockedAppDao.deleteApp(packageName)
    }

    suspend fun isAppBlocked(packageName: String): Boolean {
        return blockedAppDao.isAppBlocked(packageName)
    }

    suspend fun startFocusSession(durationMinutes: Int, isStrict: Boolean, totalMs: Long? = null) {
        val active = focusSessionDao.getActiveSessionSync()
        if (active != null) {
            val now = System.currentTimeMillis()
            val finalStatus = if (now >= active.endTime) "Expired" else "Ended Early"
            val maxSeconds = ((active.endTime - active.startTime) / 1000L).coerceAtLeast(0L)
            val actualDurationSeconds = ((now - active.startTime) / 1000L).coerceAtLeast(0L).coerceAtMost(maxSeconds)
            val plantStatus = if (now >= active.endTime) "MATURED" else "WITHERED"
            val assetPath = if (now >= active.endTime) "img_plant_matured" else "img_plant_withered"
            focusSessionDao.updateSession(active.copy(
                isActive = false,
                actualEndTime = now,
                actualDurationSeconds = actualDurationSeconds,
                sessionStatus = finalStatus,
                plantStatus = plantStatus,
                assetPath = assetPath
            ))
        }

        val now = System.currentTimeMillis()
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
            sessionStatus = "Completed",
            isActive = true,
            isStrict = isStrict,
            plantStatus = "SEED",
            assetPath = "img_plant_seed"
        )
        focusSessionDao.insertSession(session)
    }

    suspend fun stopActiveSession(status: String? = null) {
        val active = focusSessionDao.getActiveSessionSync()
        if (active != null) {
            val now = System.currentTimeMillis()
            val isCompleted = now >= active.endTime
            val finalStatus = status ?: if (isCompleted) "Completed" else "Ended Early"
            
            // Calculate actual duration in seconds
            val maxSeconds = ((active.endTime - active.startTime) / 1000L).coerceAtLeast(0L)
            val actualDurationSeconds = ((now - active.startTime) / 1000L).coerceAtLeast(0L).coerceAtMost(maxSeconds)
            
            val plantStatus = if (finalStatus == "Completed" || finalStatus == "Expired") "MATURED" else "WITHERED"
            val assetPath = if (finalStatus == "Completed" || finalStatus == "Expired") "img_plant_matured" else "img_plant_withered"

            val updated = active.copy(
                isActive = false,
                actualEndTime = now,
                actualDurationSeconds = actualDurationSeconds,
                sessionStatus = finalStatus,
                plantStatus = plantStatus,
                assetPath = assetPath
            )
            focusSessionDao.updateSession(updated)
            if (finalStatus == "Completed") {
                sessionCompletedNaturally.tryEmit(true)
            }
            
            // Update static analytics table for compatibility using ACTUAL duration only
            val current = analyticsDao.getAnalyticsSync() ?: Analytics()
            val actualDurationMinutes = actualDurationSeconds / 60L
            val completedIncrement = if (finalStatus == "Completed") 1 else 0
            analyticsDao.insertAnalytics(current.copy(
                focusSessionsCompleted = current.focusSessionsCompleted + completedIncrement,
                totalFocusTimeMinutes = current.totalFocusTimeMinutes + actualDurationMinutes
            ))
        }
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

    suspend fun updateLongTermBlockUsage(id: Int, usedSeconds: Long, epochDay: Long) {
        longTermBlockDao.updateUsage(id, usedSeconds, epochDay)
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
        val current = analyticsDao.getAnalyticsSync() ?: Analytics()
        analyticsDao.insertAnalytics(current.copy(blockedAppLaunches = current.blockedAppLaunches + 1))
    }

    suspend fun deactivateStrictMode() {
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
}
