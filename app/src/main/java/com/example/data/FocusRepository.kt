package com.example.data

import kotlinx.coroutines.flow.Flow

class FocusRepository(
    private val blockedAppDao: BlockedAppDao,
    private val focusSessionDao: FocusSessionDao,
    private val longTermBlockDao: LongTermBlockDao,
    private val analyticsDao: AnalyticsDao,
    private val websiteBlockDao: WebsiteBlockDao,
    private val appSettingDao: AppSettingDao
) {
    val allBlockedApps: Flow<List<BlockedApp>> = blockedAppDao.getAllBlockedApps()
    val activeSession: Flow<FocusSession?> = focusSessionDao.getActiveSession()

    val allLongTermBlocks: Flow<List<LongTermBlock>> = longTermBlockDao.getAllLongTermBlocks()
    val activeLongTermBlocks: Flow<List<LongTermBlock>> = longTermBlockDao.getActiveLongTermBlocks()
    val allWebsiteBlocks: Flow<List<WebsiteBlock>> = websiteBlockDao.getAllWebsiteBlocks()
    val activeWebsiteBlocks: Flow<List<WebsiteBlock>> = websiteBlockDao.getActiveWebsiteBlocks()

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

    suspend fun stopActiveSession(status: String? = null) {
        val active = focusSessionDao.getActiveSessionSync()
        if (active != null) {
            val now = System.currentTimeMillis()
            val isCompleted = now >= active.endTime
            val finalStatus = status ?: if (isCompleted) "Completed" else "Ended Early"
            val maxSeconds = ((active.endTime - active.startTime) / 1000L).coerceAtLeast(0L)
            val actualDurationSeconds = ((now - active.startTime) / 1000L).coerceAtLeast(0L).coerceAtMost(maxSeconds)

            val updated = active.copy(
                isActive = false,
                actualEndTime = now,
                actualDurationSeconds = actualDurationSeconds,
                sessionStatus = finalStatus
            )
            focusSessionDao.updateSession(updated)
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

    suspend fun insertSession(session: FocusSession) {
        focusSessionDao.insertSession(session)
    }

    suspend fun incrementBlockedLaunches() {
        val current = analyticsDao.getAnalyticsSync() ?: Analytics()
        analyticsDao.insertAnalytics(current.copy(blockedAppLaunches = current.blockedAppLaunches + 1))
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
