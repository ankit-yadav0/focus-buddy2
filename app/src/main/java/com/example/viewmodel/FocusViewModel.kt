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
import com.example.data.LockedApp
import com.example.data.LongTermBlock
import com.example.data.WebsiteBlock
import com.example.update.UpdateInfo
import com.example.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean = false,
    val isLocked: Boolean = false
)

class FocusViewModel(
    private val repository: FocusRepository,
    private val context: Context
) : ViewModel() {

    val youtubeBlockShorts = MutableStateFlow(false)
    val instagramBlockReels = MutableStateFlow(false)
    val snapchatBlockSpotlight = MutableStateFlow(false)
    val accentTheme = MutableStateFlow("Phosphor Mint")

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    // Emits a short user-facing message whenever an action (add/remove a
    // long-term or website block) is skipped because a Strict Mode rule-editing
    // lock is on.
    val actionBlockedMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            youtubeBlockShorts.value = prefs.getBoolean("youtube_block_shorts", false)
            instagramBlockReels.value = prefs.getBoolean("instagram_block_reels", false)
            snapchatBlockSpotlight.value = prefs.getBoolean("snapchat_block_spotlight", false)
            accentTheme.value = prefs.getString("accent_theme", "Phosphor Mint") ?: "Phosphor Mint"
            repository.getSetting("accent_theme")?.let { savedTheme ->
                accentTheme.value = savedTheme
            }
            _isInitialized.value = true
        }
    }

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps = _isLoadingApps.asStateFlow()

    val activeSession: StateFlow<FocusSession?> = repository.activeSession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isStrictModeActive: StateFlow<Boolean> = repository.activeSession
        .map { session -> session?.let { it.isActive && it.isStrict && System.currentTimeMillis() < it.endTime } ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val blockedApps: StateFlow<List<BlockedApp>> = repository.allBlockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lockedApps: StateFlow<List<LockedApp>> = repository.allLockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appListState: StateFlow<List<AppInfo>> = kotlinx.coroutines.flow.combine(_installedApps, blockedApps) { installed, blocked ->
        val blockedPackages = blocked.map { it.packageName }.toSet()
        installed.map { app ->
            app.copy(isBlocked = blockedPackages.contains(app.packageName))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appLockListState: StateFlow<List<AppInfo>> = kotlinx.coroutines.flow.combine(_installedApps, lockedApps) { installed, locked ->
        val lockedPackages = locked.map { it.packageName }.toSet()
        installed.map { app ->
            app.copy(isLocked = lockedPackages.contains(app.packageName))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLongTermBlocks: StateFlow<List<LongTermBlock>> = repository.allLongTermBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeLongTermBlocks: StateFlow<List<LongTermBlock>> = repository.activeLongTermBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWebsiteBlocks: StateFlow<List<WebsiteBlock>> = repository.allWebsiteBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeWebsiteBlocks: StateFlow<List<WebsiteBlock>> = repository.activeWebsiteBlocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInstalledApps()
        checkAndCleanupExpiredSession()
        startExpiredBlocksDeactivationLoop()
        com.example.ui.helper.SmartNightModeManager.startMonitoring(viewModelScope)
    }

    private fun checkAndCleanupExpiredSession() {
        viewModelScope.launch {
            val active = repository.getActiveSessionSync()
            if (active != null && active.isActive && System.currentTimeMillis() >= active.endTime) {
                repository.stopActiveSession("Expired")
            }
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

    fun setYoutubeBlockShorts(value: Boolean) {
        youtubeBlockShorts.value = value
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("youtube_block_shorts", value)
            .apply()
    }

    fun setInstagramBlockReels(value: Boolean) {
        instagramBlockReels.value = value
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("instagram_block_reels", value)
            .apply()
    }

    fun setSnapchatBlockSpotlight(value: Boolean) {
        snapchatBlockSpotlight.value = value
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("snapchat_block_spotlight", value)
            .apply()
    }

    fun toggleAppBlocked(app: AppInfo) {
        viewModelScope.launch {
            if (app.isBlocked) {
                repository.removeBlockedApp(app.packageName)
            } else {
                repository.addBlockedApp(BlockedApp(app.packageName, app.appName))
            }
        }
    }

    // --- App Lock (PIN-gated apps) ---

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

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

    fun startStrictSession(durationSeconds: Long) {
        viewModelScope.launch {
            val active = repository.getActiveSessionSync()
            if (active != null && active.isActive) {
                if (System.currentTimeMillis() < active.endTime) {
                    // A session is already genuinely running - don't allow starting
                    // another on top of it.
                    return@launch
                }
                // The previous session's timer has already run out but nothing has
                // flipped its isActive flag to false yet (the watchdog runs on its own
                // few-second cycle). Close it out here first - otherwise both rows would
                // briefly have isActive = 1, and the "get the active session" query
                // (LIMIT 1, no ordering) could just as easily return this stale, already-
                // expired row instead of the new one we're about to insert.
                repository.stopActiveSession("Expired")
            }
            val now = System.currentTimeMillis()
            val durationMinutes = ((durationSeconds + 59) / 60).toInt().coerceAtLeast(1)
            val session = FocusSession(
                startTime = now,
                durationMinutes = durationMinutes,
                endTime = now + durationSeconds * 1000L,
                isActive = true,
                isStrict = true,
                plannedDurationMinutes = durationMinutes,
                origin = "STRICT_MANUAL"
            )
            repository.insertSession(session)
        }
    }

    fun stopActiveSession() {
        viewModelScope.launch {
            repository.stopActiveSession()
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

    private suspend fun isRulesEditingLocked(): Boolean {
        val active = repository.getActiveSessionSync()
        val strictActive = active?.let { it.isActive && it.isStrict && System.currentTimeMillis() < it.endTime } ?: false
        if (!strictActive) return false
        val mode = repository.getSetting("strict_restriction_editing_mode") ?: "ALL"
        if (mode == "ALL") return true
        return repository.getSetting("strict_restrict_rules_specific")?.toBoolean() ?: true
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

    fun isAccessibilityServiceEnabled(): Boolean {
        if (com.example.service.FocusAccessibilityService.instance != null) {
            return true
        }
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

    // --- In-app update ---

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    private val _isCheckingForUpdate = MutableStateFlow(false)
    val isCheckingForUpdate = _isCheckingForUpdate.asStateFlow()

    fun checkForUpdate() {
        viewModelScope.launch {
            _isCheckingForUpdate.value = true
            _updateInfo.value = UpdateManager.checkForUpdate(context, force = true)
            _isCheckingForUpdate.value = false
        }
    }

    fun startUpdateDownload() {
        val info = _updateInfo.value ?: return
        UpdateManager.startDownload(context, info.downloadUrl, info.versionName)
    }

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
