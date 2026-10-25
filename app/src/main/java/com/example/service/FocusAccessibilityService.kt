package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.BlockActivity
import com.example.MainActivity
import com.example.FocusApplication
import com.example.R
import com.example.TimeUtils
import com.example.TrustedClock
import com.example.data.LongTermBlock
import com.example.data.WebsiteBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.provider.Settings
import android.view.WindowManager
import android.view.View
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.ViewGroup
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: FocusAccessibilityService? = null
            private set

        private const val NOTIFICATION_CHANNEL_ID = "focus_buddy_protection"

        // How long the user may be in another app and still come back to an unlocked App Lock app
        // without re-entering the PIN (quick calculator / copy-paste detours).
        private const val UNLOCK_GRACE_MS = 60_000L
        private const val FOREGROUND_NOTIFICATION_ID = 4201

        // Minimum spacing between processed TYPE_WINDOW_CONTENT_CHANGED events.
        private const val CONTENT_CHANGED_THROTTLE_MS = 300L

        // Safety-net cap on how long the instant-block overlay can stay up if
        // BlockActivity never calls dismissInstantOverlay() (e.g. it fails to launch).
        private const val OVERLAY_SAFETY_TIMEOUT_MS = 4_000L

        // Colour of the text-less backdrop shown while BlockActivity / the PIN screen
        // loads (same as BlockActivity's top gradient colour).
        private const val USE_APP_BACKDROP = true
        private const val BACKDROP_COLOR = 0xFF0F0C20.toInt()

        private const val SETTINGS_CONTENT_THROTTLE_MS = 50L

        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
            "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
            "com.sec.android.app.sbrowser", "com.microsoft.emmx",
            "com.duckduckgo.mobile.android", "com.brave.browser",
            "com.heytap.browser", "com.coloros.browser", "com.android.browser",
            "com.vivaldi.browser", "com.kiwibrowser.browser", "com.UCMobile.intl"
        )

        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp", "com.whatsapp.w4b"
        )

        private val TELEGRAM_PACKAGES = setOf(
            "org.telegram.messenger", "org.telegram.messenger.web",
            "org.thunderdog.challegram", "tw.nekomimi.nekogram"
        )

        private val DEFAULT_MORNING_DISTRACTION_PACKAGES = setOf(
            "com.instagram.android", "com.google.android.youtube", "com.snapchat.android",
            "com.facebook.katana", "com.facebook.lite", "com.twitter.android",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.reddit.frontpage",
            "com.netflix.mediaclient", "in.startv.hotstar", "com.amazon.avod.thirdpartyclient"
        )

        // Settings + the system package installer: read for the Strict Mode Settings gate and the
        // Uninstall Friction Guard.
        private val SYSTEM_GUARD_PACKAGES = setOf(
            "com.android.settings", "com.android.packageinstaller", "com.google.android.packageinstaller"
        )

        // Windows that pop over a locked app without the user actually leaving it (Play services
        // dialogs, file / photo pickers, the share sheet).
        private val EXTRA_TRANSIENT_PACKAGES = setOf(
            "com.google.android.gms", "com.android.documentsui", "com.google.android.documentsui",
            "com.android.intentresolver", "com.android.providers.media.module",
            "com.google.android.providers.media.module"
        )

        @Volatile
        private var pendingUninstallAllowedUntilMs = 0L

        fun allowPendingUninstall(durationMs: Long = 90_000L) {
            pendingUninstallAllowedUntilMs = System.currentTimeMillis() + durationMs
        }

        fun isPendingUninstallAllowed(): Boolean {
            return System.currentTimeMillis() < pendingUninstallAllowedUntilMs
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var isSessionActive = false
    private var sessionEndTime: Long = 0
    private var isSessionStrict = false
    private var blockedPackages = setOf<String>()
    private var restrictSettingsFullyEnabled = false
    private var restrictUninstallEnabled = false
    // Banking Pause (see BankingPause.kt): chosen banking apps switch this service off while open.
    private var bankingPauseEnabled = false
    private var bankingPackages: Set<String> = emptySet()
    private var longTermBlocks = listOf<LongTermBlock>()
    private var activeWebsitesList = listOf<WebsiteBlock>()
    private var lastContentChangedProcessTime = 0L

    // New productivity features state
    private var mindfulDelayEnabled = false
    private var mindfulDelayPackages: Set<String> = emptySet()
    private val mindfulPassUntilMs = ConcurrentHashMap<String, Long>()
    private var blockedKeywordsList: List<String> = emptyList()
    private var morningLockEnabled = false
    private var maturedSessionsList = listOf<com.example.data.FocusSession>()

    // App Lock (PIN-gated apps)
    private var lockedPackages = setOf<String>()
    private var unlockedPackage: String? = null
    private var unlockedLeftAtMs = 0L

    // Daily time-quota tracking
    private var quotaTrackingBlockId: Int? = null
    private var quotaTrackingPackage: String? = null
    private var quotaTrackingStartMs: Long = 0L
    private var quotaTickerJob: kotlinx.coroutines.Job? = null
    private var quotaHeartbeatJob: kotlinx.coroutines.Job? = null

    private val quotaPersistMutex = Mutex()
    private val quotaAccountedUntil = HashMap<Int, Long>()

    // Drops App Lock's unlock grant (and stops quota tracking) when the screen turns off.
    private var screenOffReceiverRegistered = false
    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                unlockedPackage = null
                unlockedLeftAtMs = 0L
                mindfulPassUntilMs.clear()
                stopQuotaTrackingAndPersist()
            }
        }
    }

    // Debounce so the foreground heartbeat doesn't re-fire a block while the previous one is still loading.
    private var lastBlockTriggerAtMs = 0L

    private var overlayView: View? = null
    private val windowManager: WindowManager by lazy { getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager }
    private var currentBlockedPackage: String? = null
    private var lastPermissionRequestTime = 0L

    private var settingsHarmlessStreak = 0
    private var settingsGateShownAtMs = 0L
    private val SETTINGS_RELEASE_MIN_ELAPSED_MS = 200L
    private val SETTINGS_RELEASE_MIN_STREAK = 2

    private var lastSettingsBounceAtMs = 0L
    private val SETTINGS_BOUNCE_DEBOUNCE_MS = 450L
    private var lastDeviceWideBlockAtMs = 0L
    private val DEVICE_WIDE_BLOCK_DEBOUNCE_MS = 1500L
    private var settingsWatchJob: Job? = null
    private var settingsWatchUntilMs = 0L
    private val SETTINGS_WATCH_INTERVAL_MS = 120L
    private val SETTINGS_WATCH_WINDOW_MS = 2500L

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("FocusService", "Accessibility Service Created")
        startForegroundProtection()
        
        try {
            val repository = (application as FocusApplication).repository

            // Observe active session
            serviceScope.launch {
                repository.activeSession.collectLatest { session ->
                    if (session != null && session.isActive) {
                        isSessionActive = true
                        sessionEndTime = session.endTime
                        isSessionStrict = session.isStrict
                    } else {
                        isSessionActive = false
                        sessionEndTime = 0
                        isSessionStrict = false
                    }
                    Log.d("FocusService", "Session updated: active=$isSessionActive, end=$sessionEndTime, strict=$isSessionStrict")
                }
            }

            // Observe blocked apps
            serviceScope.launch {
                repository.allBlockedApps.collectLatest { apps ->
                    blockedPackages = apps.map { it.packageName }.toSet()
                    Log.d("FocusService", "Blocked apps updated: count=${blockedPackages.size}")
                }
            }

            // Observe PIN-locked apps
            serviceScope.launch {
                repository.allLockedApps.collectLatest { apps ->
                    lockedPackages = apps.map { it.packageName }.toSet()
                    Log.d("FocusService", "Locked apps updated: count=${lockedPackages.size}")
                }
            }

            // Observe the Strict Mode wizard's "Phone Settings" restriction toggle
            serviceScope.launch {
                repository.getSettingFlow("strict_restrict_settings").collectLatest { value ->
                    restrictSettingsFullyEnabled = value?.toBoolean() ?: false
                }
            }

            // Observe the Strict Mode wizard's "App Uninstallation" restriction toggle
            serviceScope.launch {
                repository.getSettingFlow("strict_restrict_uninstall").collectLatest { value ->
                    restrictUninstallEnabled = value?.toBoolean() ?: false
                }
            }

            // Observe Banking Pause settings
            serviceScope.launch {
                repository.getSettingFlow("banking_pause_enabled").collectLatest { value ->
                    bankingPauseEnabled = value?.toBoolean() ?: false
                }
            }
            serviceScope.launch {
                repository.getSettingFlow("banking_pause_packages").collectLatest { value ->
                    bankingPackages = BankingPausePolicy.parsePackages(value)
                }
            }

            // Observe Mindful Launch Delay (7-Second Breathing Gate) settings
            serviceScope.launch {
                repository.getSettingFlow("mindful_delay_enabled").collectLatest { value ->
                    mindfulDelayEnabled = value?.toBoolean() ?: false
                }
            }
            serviceScope.launch {
                repository.getSettingFlow("mindful_delay_packages").collectLatest { value ->
                    mindfulDelayPackages = value
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.toSet()
                        ?: emptySet()
                }
            }

            // Observe Keyword & Topic Blocker list
            serviceScope.launch {
                repository.getSettingFlow("blocked_keywords").collectLatest { value ->
                    blockedKeywordsList = value
                        ?.split(",")
                        ?.map { it.trim().lowercase() }
                        ?.filter { it.length >= 3 }
                        ?.distinct()
                        ?: emptyList()
                }
            }

            // Observe Morning "First Task" Lock setting & matured sessions
            serviceScope.launch {
                repository.getSettingFlow("morning_lock_enabled").collectLatest { value ->
                    morningLockEnabled = value?.toBoolean() ?: false
                }
            }
            serviceScope.launch {
                repository.maturedSessions.collectLatest { sessions ->
                    maturedSessionsList = sessions
                }
            }

            // Observe active long-term blocks
            serviceScope.launch {
                repository.activeLongTermBlocks.collectLatest { blocks ->
                    longTermBlocks = blocks
                    Log.d("FocusService", "Active Long-Term blocks updated: count=${blocks.size}")
                }
            }

            // Observe active website blocks
            serviceScope.launch {
                repository.activeWebsiteBlocks.collectLatest { blocks ->
                    activeWebsitesList = blocks
                    Log.d("FocusService", "Active Website blocks updated: count=${blocks.size}")
                }
            }
        } catch (e: Exception) {
            Log.e("FocusService", "Error initializing focus service", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundProtection()
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo = serviceInfo?.apply {
            flags = flags or 
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or 
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        startQuotaHeartbeat()

        if (BankingPause.onServiceConnected(this)) recheckForegroundSoon()

        if (!screenOffReceiverRegistered) {
            try {
                registerReceiver(screenOffReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF))
                screenOffReceiverRegistered = true
            } catch (e: Exception) {
                Log.e("FocusService", "Could not register screen-off receiver", e)
            }
        }
    }

    private fun startForegroundProtection() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(NotificationManager::class.java)
                if (notificationManager?.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Focuss Buddy Protection",
                        NotificationManager.IMPORTANCE_MIN
                    ).apply {
                        description = "Keeps app and website blocking running in the background"
                        setShowBadge(false)
                    }
                    notificationManager?.createNotificationChannel(channel)
                }
            }

            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Focuss Buddy is protecting your focus")
                .setContentText("Protection service is running")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setSilent(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("FocusService", "Could not start foreground protection", e)
        }
    }

    private fun isTransientSystemPackage(packageName: String): Boolean {
        if (packageName == "com.android.systemui") return true
        if (packageName == "android") return true
        if (packageName == "com.android.permissioncontroller" ||
            packageName == "com.google.android.permissioncontroller"
        ) return true
        if (EXTRA_TRANSIENT_PACKAGES.contains(packageName)) return true
        val lower = packageName.lowercase()
        if (lower.contains("dialer") || lower.contains("telecom") || lower.contains("incallui")) return true
        val currentImePackage = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore("/")
        } catch (e: Exception) {
            null
        }
        return packageName == currentImePackage
    }

    // ---- Strict Mode: Power menu guard ---------------------------------------------
    private var lastPowerMenuDismissAtMs = 0L
    private var powerMenuRecheckJob: Job? = null
    private val POWER_MENU_DEBOUNCE_MS = 400L

    private fun isPowerMenuCandidatePackage(pkg: String): Boolean =
        PowerMenuMatcher.isPackageCandidate(pkg)

    private fun isPowerMenuEvent(event: AccessibilityEvent): Boolean {
        if (PowerMenuMatcher.isPowerMenuClass(event.className?.toString())) return true
        return powerMenuTextOnScreen()
    }

    private fun powerMenuTextOnScreen(): Boolean {
        val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: return false
        try {
            if (!PowerMenuMatcher.isPackageCandidate(root.packageName?.toString().orEmpty())) return false
            for (label in PowerMenuMatcher.LABELS) {
                val hits = root.findAccessibilityNodeInfosByText(label) ?: continue
                var matched = false
                for (n in hits) {
                    if (!matched && (PowerMenuMatcher.isPowerMenuLabel(n.text?.toString()) ||
                            PowerMenuMatcher.isPowerMenuLabel(n.contentDescription?.toString()))
                    ) {
                        matched = true
                    }
                    n.recycle()
                }
                if (matched) return true
            }
            return false
        } catch (e: Exception) {
            return false
        } finally {
            root.recycle()
        }
    }

    private fun schedulePowerMenuRecheck() {
        if (powerMenuRecheckJob?.isActive == true) return
        powerMenuRecheckJob = serviceScope.launch {
            repeat(2) {
                delay(180)
                val strict = isSessionActive && TrustedClock.now() < sessionEndTime && isSessionStrict
                if (!strict) return@launch
                if (powerMenuTextOnScreen()) {
                    dismissPowerMenu()
                    return@launch
                }
            }
        }
    }

    private fun dismissPowerMenu() {
        val now = TrustedClock.now()
        if (now - lastPowerMenuDismissAtMs < POWER_MENU_DEBOUNCE_MS) return
        lastPowerMenuDismissAtMs = now
        try {
            @Suppress("DEPRECATION")
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        } catch (e: Exception) {
            // SecurityException on Android 12+, ignore
        }
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
        } catch (e: Exception) {
            Log.e("FocusService", "Power menu BACK failed", e)
        }
        serviceScope.launch {
            delay(250)
            if (powerMenuTextOnScreen()) {
                try { performGlobalAction(GLOBAL_ACTION_BACK) } catch (e: Exception) { }
            }
        }
        serviceScope.launch {
            try { (application as FocusApplication).repository.incrementBlockedLaunches() }
            catch (e: Exception) { Log.e("FocusService", "incrementBlockedLaunches failed", e) }
        }
    }
    // ---------------------------------------------------------------------------------

    private fun recheckForegroundSoon() {
        serviceScope.launch {
            for (attempt in 0 until 4) {
                delay(if (attempt == 0) 250L else 400L)
                val root = try { rootInActiveWindow } catch (e: Exception) { null }
                val pkg = try { root?.packageName?.toString() } finally { root?.recycle() }
                if (pkg != null && pkg != applicationContext.packageName) {
                    @Suppress("DEPRECATION")
                    val ev = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                    ev.packageName = pkg
                    try {
                        onAccessibilityEvent(ev)
                    } catch (e: Exception) {
                        Log.e("FocusService", "Foreground re-check failed", e)
                    } finally {
                        try { ev.recycle() } catch (e: Exception) { }
                    }
                    return@launch
                }
            }
        }
    }

    private fun shouldPauseFor(pkg: String): Boolean {
        if (pkg == applicationContext.packageName) return false
        val longTermTargets = longTermBlocks.filter { it.type == "APP" }.map { it.target }.toSet()
        if (!BankingPausePolicy.isEligible(pkg, blockedPackages, longTermTargets)) return false
        if (isHomeLauncherPackage(pkg)) return false
        if (lockedPackages.contains(pkg) && unlockedPackage != pkg) return false
        return true
    }

    private fun isHomeLauncherPackage(packageName: String): Boolean {
        return try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(i, 0).any { it.activityInfo.packageName == packageName }
        } catch (e: Exception) { false }
    }

    /** Grants a temporary pass (default 10 minutes) after the user completes the 7-second Mindful Breathing Gate. */
    fun grantMindfulLaunchPass(packageName: String, durationMs: Long = 10 * 60 * 1000L) {
        mindfulPassUntilMs[packageName] = TrustedClock.now() + durationMs
    }

    /** Returns how many minutes of Focus Sessions have been completed today (local timezone). */
    private fun getTodayCompletedFocusMinutes(now: Long): Int {
        val todayDay = TimeUtils.localEpochDay(now)
        var totalMinutes = 0
        for (session in maturedSessionsList) {
            val refTime = if (session.actualEndTime > 0L) session.actualEndTime else session.endTime
            if (TimeUtils.localEpochDay(refTime) == todayDay) {
                val mins = (session.actualDurationSeconds / 60L).toInt().coerceAtLeast(session.durationMinutes)
                totalMinutes += mins
            }
        }
        return totalMinutes
    }

    private var lastYouTubeSearchAutoClickMs = 0L

    /** Returns true if local trusted time is between 5:00 AM and 8:59:59 AM and the user hasn't finished 25m of focus today. */
    private fun isMorningFirstTaskLockActive(now: Long): Boolean {
        if (!morningLockEnabled) return false
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour !in 5..8) return false
        return getTodayCompletedFocusMinutes(now) < 25
    }

    private fun getTodayNineAmWallOrTrustedMillis(now: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis.coerceAtLeast(now + 60_000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && 
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        val now = TrustedClock.now()
        val isStrictModeActive = isSessionActive && now < sessionEndTime && isSessionStrict

        // Strict Mode: block the Power-off / Restart menu (runs before every throttle).
        if (isStrictModeActive &&
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            isPowerMenuCandidatePackage(packageName)
        ) {
            if (isPowerMenuEvent(event)) {
                dismissPowerMenu()
            } else {
                schedulePowerMenuRecheck()
            }
        }

        // Banking Pause
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            bankingPauseEnabled &&
            bankingPackages.contains(packageName) &&
            shouldPauseFor(packageName) &&
            BankingPause.begin(this, packageName)
        ) {
            Log.d("FocusService", "Banking Pause: switching service off for $packageName")
            disableSelf()
            return
        }

        // Strict-Mode Settings gate
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            packageName == "com.android.settings" &&
            overlayView == null &&
            isStrictModeActive &&
            Settings.canDrawOverlays(this)
        ) {
            showOverlay(packageName, opaque = false)
            settingsHarmlessStreak = 0
            settingsGateShownAtMs = now
        }

        if (overlayView != null && packageName == applicationContext.packageName) {
            removeOverlay()
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val minGap = if (packageName == "com.android.settings") SETTINGS_CONTENT_THROTTLE_MS else CONTENT_CHANGED_THROTTLE_MS
            if (now - lastContentChangedProcessTime < minGap) return
            lastContentChangedProcessTime = now
        }

        // If a quota-tracked app is no longer in the foreground, stop the ticker and persist.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            quotaTrackingPackage != null && quotaTrackingPackage != packageName &&
            !isTransientSystemPackage(packageName)
        ) {
            stopQuotaTrackingAndPersist()
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            unlockedPackage != null && unlockedPackage != packageName &&
            packageName != applicationContext.packageName &&
            !isTransientSystemPackage(packageName) &&
            (isHomeLauncherPackage(packageName) || lockedPackages.contains(packageName) || blockedPackages.contains(packageName))
        ) {
            unlockedPackage = null
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && unlockedPackage != null) {
            if (unlockedPackage == packageName) {
                if (unlockedLeftAtMs != 0L && now - unlockedLeftAtMs > UNLOCK_GRACE_MS) {
                    unlockedPackage = null
                }
                unlockedLeftAtMs = 0L
            } else if (packageName != applicationContext.packageName && !isTransientSystemPackage(packageName)) {
                if (unlockedLeftAtMs == 0L) unlockedLeftAtMs = now
            }
        }

        val isBrowser = BROWSER_PACKAGES.contains(packageName)
        val hasKeywordBlockingForPkg = blockedKeywordsList.isNotEmpty() &&
            (isBrowser || packageName == "com.google.android.youtube")
        val readsScreenText = SYSTEM_GUARD_PACKAGES.contains(packageName) ||
            (isStrictModeActive && isContentBlockEnabled(packageName)) ||
            hasKeywordBlockingForPkg
        val needsTree = isBrowser || readsScreenText
        val rootNode = if (needsTree) (try { rootInActiveWindow } catch (e: Exception) { null }) else null
        val usingFallbackSource = needsTree && rootNode == null
        val nodeToUse = if (needsTree) (rootNode ?: try { event.source } catch (e: Exception) { null }) else null

        try {
            val screenTexts = mutableListOf<String>()
            if (readsScreenText && nodeToUse != null) {
                collectScreenText(nodeToUse, screenTexts, 0)
            }

            // Don't block our own app or common system tasks
            val ourPackage = applicationContext.packageName
            if (packageName == ourPackage) return

            val appName = applicationContext.getString(R.string.app_name)

            // Strict-Mode Guard
            if (isStrictModeActive) {
                if (restrictUninstallEnabled &&
                    (packageName == "com.android.packageinstaller" || packageName == "com.google.android.packageinstaller") &&
                    screenTexts.any { it.contains("uninstall", ignoreCase = true) }
                ) {
                    if (now - lastDeviceWideBlockAtMs >= DEVICE_WIDE_BLOCK_DEBOUNCE_MS) {
                        lastDeviceWideBlockAtMs = now
                        triggerBlockActivity(
                            packageName,
                            isLongTerm = false,
                            reason = "Uninstalling apps is blocked in Strict Mode.",
                            endDate = sessionEndTime
                        )
                    }
                    return
                }

                if (packageName == "com.android.settings") {
                    if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) startSettingsWatch()
                    if (handleSettingsScreen(nodeToUse, packageName, appName, now)) return
                }
            }

            // Always-on Uninstall Friction Guard for Focuss Buddy
            run {
                if (isPendingUninstallAllowed()) return@run

                val isUninstallerScreen = (packageName == "com.android.packageinstaller" || packageName == "com.google.android.packageinstaller") &&
                    screenTexts.any { it.contains(appName, ignoreCase = true) } &&
                    screenTexts.any { it.contains("uninstall", ignoreCase = true) }
                val isOwnAppInfoWithUninstall = packageName == "com.android.settings" &&
                    screenTexts.any { it.contains(appName, ignoreCase = true) } &&
                    screenTexts.any { it.contains("Uninstall", ignoreCase = true) }

                if (isUninstallerScreen || isOwnAppInfoWithUninstall) {
                    Log.d("FocusService", "Detected external uninstall attempt - redirecting to in-app flow")
                    val redirectIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("deep_link_route", "uninstall_reflection")
                    }
                    startActivity(redirectIntent)
                    return
                }
            }

            if (packageName == "com.android.settings" || packageName == "android") return

            val repository = (application as FocusApplication).repository

            // Only handle app-level blocking on WINDOW_STATE_CHANGED to maximize performance
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                Log.d("FocusService", "Window state changed: $packageName")

                // 1. Check if focus session is active and app is blocked
                if (isSessionActive && now < sessionEndTime) {
                    if (blockedPackages.contains(packageName)) {
                        Log.d("FocusService", "Blocking app due to active session: $packageName")
                        triggerBlockActivity(packageName, isLongTerm = false, reason = "", endDate = sessionEndTime)
                        return
                    }
                } else if (isSessionActive && now >= sessionEndTime) {
                    // Expired focus session
                    serviceScope.launch {
                        try {
                            repository.stopActiveSession("Expired")
                        } catch (e: Exception) {
                            Log.e("FocusService", "Error stopping expired session", e)
                        }
                    }
                }

                // 1b. Morning "First Task" Lock (5:00 AM - 9:00 AM until 25 min Focus Session completed today)
                if (isMorningFirstTaskLockActive(now)) {
                    val isMorningLockedApp = blockedPackages.contains(packageName) ||
                        mindfulDelayPackages.contains(packageName) ||
                        DEFAULT_MORNING_DISTRACTION_PACKAGES.contains(packageName)
                    if (isMorningLockedApp && !isHomeLauncherPackage(packageName) && !isTransientSystemPackage(packageName)) {
                        val doneMins = getTodayCompletedFocusMinutes(now)
                        Log.d("FocusService", "Blocking app due to Morning First-Task Lock: $packageName ($doneMins/25 min)")
                        triggerBlockActivity(
                            packageName,
                            isLongTerm = false,
                            reason = "Morning First-Task Lock (5 AM – 9 AM): Complete 25 minutes of Focus today ($doneMins/25 min completed) to unlock distracting apps.",
                            endDate = getTodayNineAmWallOrTrustedMillis(now)
                        )
                        return
                    }
                }

                // 2. Check for active Long-Term App Blocks
                val activeLongTermAppBlock = longTermBlocks.firstOrNull {
                    it.type == "APP" && it.target == packageName && now >= it.startDate && now <= it.endDate && it.isActive
                }
                if (activeLongTermAppBlock != null) {
                    val limit = activeLongTermAppBlock.dailyLimitSeconds
                    if (limit == null) {
                        Log.d("FocusService", "Blocking app due to Long-Term block: $packageName")
                        triggerBlockActivity(
                            packageName,
                            isLongTerm = true,
                            reason = activeLongTermAppBlock.reason,
                            endDate = activeLongTermAppBlock.endDate,
                            targetLabel = activeLongTermAppBlock.targetLabel,
                            type = "APP"
                        )
                        return
                    } else {
                        val today = currentEpochDay()
                        val usedToday = if (activeLongTermAppBlock.lastUsageResetEpochDay != today) 0L else activeLongTermAppBlock.usedSecondsToday
                        if (usedToday >= limit) {
                            Log.d("FocusService", "Blocking app - daily quota used up: $packageName")
                            triggerBlockActivity(
                                packageName,
                                isLongTerm = true,
                                reason = "Daily time limit reached: ${activeLongTermAppBlock.reason}",
                                endDate = activeLongTermAppBlock.endDate,
                                targetLabel = activeLongTermAppBlock.targetLabel,
                                type = "APP"
                            )
                            return
                        } else if (quotaTrackingPackage != packageName) {
                            startQuotaTracking(activeLongTermAppBlock)
                        }
                    }
                }

                // 2b. Check for App Lock (PIN-gated apps)
                if (lockedPackages.contains(packageName) && unlockedPackage != packageName) {
                    Log.d("FocusService", "Prompting for PIN - locked app opened: $packageName")
                    triggerAppLockPrompt(packageName)
                    return
                }

                // 2c. Check for Mindful Launch Delay (7-Second Breathing Gate)
                if (mindfulDelayEnabled && mindfulDelayPackages.contains(packageName)) {
                    val passUntil = mindfulPassUntilMs[packageName] ?: 0L
                    if (now >= passUntil) {
                        Log.d("FocusService", "Triggering 7s Mindful Breathing Gate for: $packageName")
                        triggerMindfulDelayGate(packageName)
                        return
                    }
                }
            }

            // 3. Check for active Long-Term Website Blocks (Inspect browser URL/node contents)
            if (isBrowser) {
                val activeWebBlocks = activeWebsitesList.filter {
                    now >= it.startDate && now <= it.endDate && it.isActive
                }
                if (activeWebBlocks.isNotEmpty()) {
                    val blockedDomains = activeWebBlocks.map { it.domain }.toSet()
                    val foundBlockedWeb = findBlockedWebsite(nodeToUse, blockedDomains)
                    if (foundBlockedWeb != null) {
                        val matchingBlock = activeWebBlocks.first { it.domain == foundBlockedWeb }
                        Log.d("FocusService", "Blocking website due to Long-Term block: $foundBlockedWeb")
                        triggerBlockActivity(
                            packageName,
                            isLongTerm = true,
                            reason = matchingBlock.reason,
                            endDate = matchingBlock.endDate,
                            targetLabel = matchingBlock.domain,
                            type = "WEBSITE"
                        )
                        return
                    }
                }
            }

            // 3b. Keyword & Topic Blocker (Browser + YouTube)
            if (hasKeywordBlockingForPkg && nodeToUse != null) {
                val matchedKeyword = findMatchedBlockedKeyword(nodeToUse, screenTexts, isBrowser)
                if (matchedKeyword != null) {
                    Log.d("FocusService", "Keyword Blocker triggered for '$matchedKeyword' in $packageName")
                    val fallbackEnd = if (isSessionActive && now < sessionEndTime) sessionEndTime else (now + 15 * 60 * 1000L)
                    triggerContentBlockActivity(
                        packageName,
                        "Blocked Keyword: \"$matchedKeyword\"",
                        fallbackEnd
                    )
                    return
                }
            }

            // 4. Content-Level Blocking (YouTube Shorts, YouTube Study Mode, Instagram Reels, Snapchat Spotlight, WhatsApp/Telegram Status & Channels)
            // Content-Level Blocking must only be active during Strict Mode.
            if (isStrictModeActive) {
                val prefs = getSharedPreferences("focuss_buddy_settings", MODE_PRIVATE)
                val ytShortsBlocked = prefs.getBoolean("youtube_block_shorts", false)
                val ytStudyMode = prefs.getBoolean("youtube_study_mode", false)

                if (packageName == "com.google.android.youtube" && (ytShortsBlocked || ytStudyMode)) {
                    val hasShortsText = screenTexts.any { it.contains("Shorts", ignoreCase = true) }
                    val hasShortsClass = event.className?.toString()?.lowercase()?.contains("shorts") == true ||
                                         event.className?.toString()?.lowercase()?.contains("reel") == true
                    val hasShortsId = checkNodeForShortsSpecifics(nodeToUse)
                    val hasShortsDescription = screenTexts.any { it.contains("shorts player", ignoreCase = true) || it.contains("reel player", ignoreCase = true) }
                    val hasRemixAction = screenTexts.any { it.equals("Remix", ignoreCase = true) }

                    val signalCount = listOf(hasShortsClass, hasShortsId, hasShortsDescription, hasRemixAction).count { it }
                    val isShortsDetected = hasShortsDescription || signalCount >= 2 || (hasShortsText && signalCount >= 1)

                    if (isShortsDetected) {
                        Log.d("FocusService", "Content-Level Block: YouTube Shorts detected")
                        triggerContentBlockActivity(packageName, "YouTube Shorts")
                        return
                    }

                    if (ytStudyMode && nodeToUse != null) {
                        // Block comments panel inside video player
                        val isCommentsPanelOpen = screenTexts.any {
                            it.equals("Comments", ignoreCase = true) || it.startsWith("Comments ", ignoreCase = true)
                        } && (checkNodeForIdKeyword(nodeToUse, "comment") || checkNodeForIdKeyword(nodeToUse, "engagement_panel"))

                        if (isCommentsPanelOpen) {
                            Log.d("FocusService", "YouTube Study Mode: Comments panel blocked")
                            triggerContentBlockActivity(packageName, "YouTube Comments (Study Mode)")
                            return
                        }

                        // Allow Search & Lecture Video Player, block Home Feed recommendations
                        val isSearching = checkNodeForIdKeyword(nodeToUse, "search_edit_text") ||
                            checkNodeForIdKeyword(nodeToUse, "search_box") ||
                            checkNodeForIdKeyword(nodeToUse, "search_query") ||
                            hasFocusedEditText(nodeToUse) ||
                            screenTexts.any {
                                it.contains("Search YouTube", ignoreCase = true) ||
                                it.equals("Search filters", ignoreCase = true)
                            }
                        val isVideoPlayerExpanded = checkNodeForIdKeyword(nodeToUse, "watch_next_results") ||
                            screenTexts.any {
                                it.equals("Subscribe", ignoreCase = true) ||
                                it.equals("Subscribed", ignoreCase = true) ||
                                it.equals("Remix", ignoreCase = true) ||
                                it.equals("Download", ignoreCase = true) ||
                                it.equals("Clip", ignoreCase = true)
                            }

                        if (!isSearching && !isVideoPlayerExpanded && isYouTubeHomeFeedActive(nodeToUse, screenTexts)) {
                            // First try to auto-open YouTube Search so the student lands directly on lecture search
                            if (now - lastYouTubeSearchAutoClickMs > 2500L && clickYouTubeSearchButton(nodeToUse)) {
                                lastYouTubeSearchAutoClickMs = now
                                Log.d("FocusService", "YouTube Study Mode: Auto-opened YouTube Search from Home feed")
                                return
                            }
                            Log.d("FocusService", "YouTube Study Mode: Home feed recommendations blocked")
                            triggerContentBlockActivity(packageName, "YouTube Home Feed (Study Mode — Use Search for Lectures)")
                            return
                        }
                    }
                }

                if (packageName == "com.instagram.android" && prefs.getBoolean("instagram_block_reels", false)) {
                    val isReels = event.className?.toString()?.lowercase()?.contains("clips") == true ||
                            event.className?.toString()?.lowercase()?.contains("reels") == true ||
                            checkNodeForIdKeyword(nodeToUse, "clips") ||
                            checkNodeForIdKeyword(nodeToUse, "reels") ||
                            checkNodeForReelsSpecifics(nodeToUse)
                    if (isReels) {
                        Log.d("FocusService", "Content-Level Block: Instagram Reels detected")
                        triggerContentBlockActivity(packageName, "Instagram Reels")
                        return
                    }
                }

                if (packageName == "com.snapchat.android" && prefs.getBoolean("snapchat_block_spotlight", false)) {
                    val isSpotlight = event.className?.toString()?.lowercase()?.contains("spotlight") == true ||
                            checkNodeForIdKeyword(nodeToUse, "spotlight") ||
                            checkNodeForSpotlightSpecifics(nodeToUse)
                    if (isSpotlight) {
                        Log.d("FocusService", "Content-Level Block: Snapchat Spotlight detected")
                        triggerContentBlockActivity(packageName, "Snapchat Spotlight")
                        return
                    }
                }

                // Feature 2: WhatsApp / Telegram "Chat-Only Mode" (Status, Stories & Channels Block)
                if (prefs.getBoolean("whatsapp_block_status", false)) {
                    if (WHATSAPP_PACKAGES.contains(packageName)) {
                        val cls = event.className?.toString()?.lowercase() ?: ""
                        val isStatusPlayback = cls.contains("statusplayback") ||
                            cls.contains("newsletter") ||
                            checkNodeForIdKeyword(nodeToUse, "status_playback") ||
                            checkNodeForIdKeyword(nodeToUse, "status_container")
                        val isInsideDirectChat = checkNodeForIdKeyword(nodeToUse, "entry") ||
                            checkNodeForIdKeyword(nodeToUse, "conversation_contact") ||
                            screenTexts.any { it.contains("Type a message", ignoreCase = true) || it.equals("Message", ignoreCase = true) }
                        val isUpdatesTabScreen = !isInsideDirectChat && (
                            (screenTexts.any { it.equals("Status", ignoreCase = true) } &&
                                screenTexts.any {
                                    it.equals("Channels", ignoreCase = true) ||
                                    it.equals("Find channels", ignoreCase = true) ||
                                    it.equals("Recent updates", ignoreCase = true) ||
                                    it.equals("Viewed updates", ignoreCase = true) ||
                                    it.equals("Add status", ignoreCase = true) ||
                                    it.equals("My status", ignoreCase = true)
                                }) ||
                            isWhatsAppUpdatesTabSelected(nodeToUse)
                        )
                        if (isStatusPlayback || isUpdatesTabScreen) {
                            Log.d("FocusService", "Content-Level Block: WhatsApp Status/Channels detected")
                            triggerContentBlockActivity(packageName, "WhatsApp Status & Channels (Chat-Only Mode)")
                            return
                        }
                    } else if (TELEGRAM_PACKAGES.contains(packageName)) {
                        val cls = event.className?.toString()?.lowercase() ?: ""
                        val isTelegramStoriesOrChannels = cls.contains("stories") ||
                            cls.contains("storyviewer") ||
                            screenTexts.any {
                                it.equals("Hidden stories", ignoreCase = true) ||
                                it.equals("My Stories", ignoreCase = true) ||
                                it.equals("Similar Channels", ignoreCase = true)
                            }
                        if (isTelegramStoriesOrChannels) {
                            Log.d("FocusService", "Content-Level Block: Telegram Stories/Channels detected")
                            triggerContentBlockActivity(packageName, "Telegram Stories & Channels (Chat-Only Mode)")
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FocusService", "Error handling accessibility event", e)
        } finally {
            if (usingFallbackSource) {
                nodeToUse?.recycle()
            } else {
                rootNode?.recycle()
            }
        }
    }

    /** True when any Strict Mode content-level toggle for this app is on. */
    private fun isContentBlockEnabled(packageName: String): Boolean {
        val prefs = getSharedPreferences("focuss_buddy_settings", MODE_PRIVATE)
        return when {
            packageName == "com.google.android.youtube" ->
                prefs.getBoolean("youtube_block_shorts", false) || prefs.getBoolean("youtube_study_mode", false)
            packageName == "com.instagram.android" ->
                prefs.getBoolean("instagram_block_reels", false)
            packageName == "com.snapchat.android" ->
                prefs.getBoolean("snapchat_block_spotlight", false)
            WHATSAPP_PACKAGES.contains(packageName) || TELEGRAM_PACKAGES.contains(packageName) ->
                prefs.getBoolean("whatsapp_block_status", false)
            else -> false
        }
    }

    private fun isYouTubeHomeFeedActive(root: AccessibilityNodeInfo?, screenTexts: List<String>): Boolean {
        if (root == null) return false
        if (isSelectedTabMatching(root, "Home")) return true
        // Fallback signal: typical YouTube home feed filter chips when Home tab is visible
        val homeChips = setOf("all", "new to you", "mixes", "gaming", "live", "music")
        val chipMatches = screenTexts.count { homeChips.contains(it.trim().lowercase()) }
        return chipMatches >= 2 && checkNodeForIdKeyword(root, "results")
    }

    private fun isWhatsAppUpdatesTabSelected(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return isSelectedTabMatching(root, "Updates") || isSelectedTabMatching(root, "Status")
    }

    private fun isSelectedTabMatching(node: AccessibilityNodeInfo?, tabKeyword: String): Boolean {
        if (node == null) return false
        if (node.isSelected) {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            if (text.contains(tabKeyword, ignoreCase = true) || desc.contains(tabKeyword, ignoreCase = true)) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val matched = isSelectedTabMatching(child, tabKeyword)
            child.recycle()
            if (matched) return true
        }
        return false
    }

    private fun findMatchedBlockedKeyword(
        root: AccessibilityNodeInfo,
        screenTexts: List<String>,
        isBrowser: Boolean
    ): String? {
        if (blockedKeywordsList.isEmpty()) return null
        if (isBrowser) {
            val urlNode = findUrlBarNode(root)
            try {
                if (urlNode != null && !urlNode.isFocused && !urlNode.isAccessibilityFocused) {
                    val urlText = urlNode.text?.toString()?.lowercase() ?: ""
                    for (kw in blockedKeywordsList) {
                        if (kw.isNotEmpty() && urlText.contains(kw)) {
                            return kw
                        }
                    }
                }
            } finally {
                if (urlNode != null && urlNode !== root) {
                    urlNode.recycle()
                }
            }
        }
        // Check visible titles / headings (skip while an EditText is actively focused so user can type)
        if (hasFocusedEditText(root)) return null
        val defaultFilterChips = setOf("all", "gaming", "movies", "music", "live", "mixes", "news", "sports", "new to you")
        for (txt in screenTexts) {
            val trimmed = txt.trim().lowercase()
            // Ignore standalone category filter chip labels on YouTube home/search bar
            if (defaultFilterChips.contains(trimmed)) continue
            for (kw in blockedKeywordsList) {
                if (kw.isNotEmpty() && containsKeywordAsWordOrPhrase(trimmed, kw)) {
                    return kw
                }
            }
        }
        return null
    }

    private fun clickYouTubeSearchButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val desc = node.contentDescription?.toString() ?: ""
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if ((desc.equals("Search", ignoreCase = true) || id.endsWith("menu_item_search") || id.endsWith("search_button")) &&
            node.isClickable
        ) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val clicked = clickYouTubeSearchButton(child)
            child.recycle()
            if (clicked) return true
        }
        return false
    }

    private fun containsKeywordAsWordOrPhrase(text: String, keyword: String): Boolean {
        var startIndex = 0
        while (startIndex <= text.length - keyword.length) {
            val idx = text.indexOf(keyword, startIndex)
            if (idx < 0) return false
            val beforeOk = idx == 0 || !text[idx - 1].isLetterOrDigit()
            val endIdx = idx + keyword.length
            val afterOk = endIdx >= text.length || !text[endIdx].isLetterOrDigit()
            if (beforeOk && afterOk) return true
            startIndex = idx + 1
        }
        return false
    }

    private fun hasFocusedEditText(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val cls = node.className?.toString() ?: ""
        if ((node.isFocused || node.isAccessibilityFocused) && cls.contains("EditText", ignoreCase = true)) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val focused = hasFocusedEditText(child)
            child.recycle()
            if (focused) return true
        }
        return false
    }

    private fun triggerMindfulDelayGate(packageName: String) {
        lastBlockTriggerAtMs = TrustedClock.now()
        if (Settings.canDrawOverlays(this)) {
            showOverlay(packageName)
        } else {
            triggerOverlayPermissionRequest()
        }
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("BLOCKED_PACKAGE", packageName)
            putExtra("IS_MINDFUL_DELAY", true)
        }
        startActivity(intent)
    }

    private fun currentEpochDay(): Long = TimeUtils.localEpochDay(TrustedClock.now())

    private fun startQuotaTracking(block: LongTermBlock) {
        quotaTrackingBlockId = block.id
        quotaTrackingPackage = block.target
        quotaTrackingStartMs = TrustedClock.now()

        quotaTickerJob?.cancel()
        quotaTickerJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2_000L)
                val currentBlockId = quotaTrackingBlockId ?: break
                val currentStartMs = quotaTrackingStartMs
                val exceeded = persistQuotaProgress(currentBlockId, currentStartMs)
                if (exceeded) {
                    quotaTrackingBlockId = null
                    quotaTrackingPackage = null
                    quotaTickerJob = null
                    triggerBlockActivity(
                        block.target,
                        isLongTerm = true,
                        reason = "Daily time limit reached: ${block.reason}",
                        endDate = block.endDate,
                        targetLabel = block.targetLabel,
                        type = "APP"
                    )
                    break
                }
            }
        }
    }

    private fun startQuotaHeartbeat() {
        quotaHeartbeatJob?.cancel()
        quotaHeartbeatJob = serviceScope.launch {
            while (true) {
                delay(3_000L)
                try {
                    val now = TrustedClock.now()

                    if (isSessionActive && now >= sessionEndTime) {
                        (application as FocusApplication).repository.stopActiveSession("Expired")
                    }

                    val sessionBlocking = isSessionActive && now < sessionEndTime && blockedPackages.isNotEmpty()
                    val hasAppBlocks = longTermBlocks.any { it.type == "APP" }
                    val morningActive = isMorningFirstTaskLockActive(now)
                    if (!sessionBlocking && !hasAppBlocks && !morningActive && quotaTrackingPackage == null) continue

                    val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: continue
                    val fgPackage = root.packageName?.toString()
                    @Suppress("DEPRECATION")
                    root.recycle()
                    if (fgPackage == null || fgPackage == applicationContext.packageName) continue

                    if (quotaTrackingPackage != null && quotaTrackingPackage != fgPackage && !isTransientSystemPackage(fgPackage)) {
                        stopQuotaTrackingAndPersist()
                    }

                    if (now - lastBlockTriggerAtMs < 2_500L) continue

                    // 1. Focus session block
                    if (sessionBlocking && blockedPackages.contains(fgPackage)) {
                        Log.d("FocusService", "Heartbeat: blocked app already in foreground: $fgPackage")
                        triggerBlockActivity(fgPackage, isLongTerm = false, reason = "", endDate = sessionEndTime)
                        continue
                    }

                    // 1b. Morning First-Task Lock
                    if (morningActive) {
                        val isMorningLockedApp = blockedPackages.contains(fgPackage) ||
                            mindfulDelayPackages.contains(fgPackage) ||
                            DEFAULT_MORNING_DISTRACTION_PACKAGES.contains(fgPackage)
                        if (isMorningLockedApp && !isHomeLauncherPackage(fgPackage) && !isTransientSystemPackage(fgPackage)) {
                            val doneMins = getTodayCompletedFocusMinutes(now)
                            triggerBlockActivity(
                                fgPackage,
                                isLongTerm = false,
                                reason = "Morning First-Task Lock (5 AM – 9 AM): Complete 25 minutes of Focus today ($doneMins/25 min completed) to unlock distracting apps.",
                                endDate = getTodayNineAmWallOrTrustedMillis(now)
                            )
                            continue
                        }
                    }

                    // 2. Long-term app blocks
                    if (quotaTrackingPackage == fgPackage) continue
                    val activeBlock = longTermBlocks.firstOrNull {
                        it.type == "APP" && it.target == fgPackage && it.isActive &&
                            now >= it.startDate && now <= it.endDate
                    } ?: continue

                    val limitSeconds = activeBlock.dailyLimitSeconds
                    if (limitSeconds == null) {
                        triggerBlockActivity(
                            fgPackage,
                            isLongTerm = true,
                            reason = activeBlock.reason,
                            endDate = activeBlock.endDate,
                            targetLabel = activeBlock.targetLabel,
                            type = "APP"
                        )
                        continue
                    }

                    val today = currentEpochDay()
                    val usedMillis = if (activeBlock.lastUsageResetEpochDay != today) 0L else activeBlock.usedMillisToday
                    if (usedMillis >= limitSeconds * 1000L) {
                        Log.d("FocusService", "Quota heartbeat: $fgPackage already over quota, blocking")
                        triggerBlockActivity(
                            fgPackage,
                            isLongTerm = true,
                            reason = "Daily time limit reached: ${activeBlock.reason}",
                            endDate = activeBlock.endDate,
                            targetLabel = activeBlock.targetLabel,
                            type = "APP"
                        )
                    } else {
                        Log.d("FocusService", "Quota heartbeat: resuming untracked foreground app $fgPackage")
                        startQuotaTracking(activeBlock)
                    }
                } catch (e: Exception) {
                    Log.e("FocusService", "Error in foreground heartbeat", e)
                }
            }
        }
    }

    private suspend fun persistQuotaProgress(blockId: Int, startMs: Long): Boolean {
        val windowEnd = TrustedClock.now()
        return quotaPersistMutex.withLock {
            val accountedUntil = quotaAccountedUntil[blockId] ?: 0L
            val elapsedMillis = (windowEnd - maxOf(startMs, accountedUntil)).coerceAtLeast(0L)
            quotaAccountedUntil[blockId] = maxOf(windowEnd, accountedUntil)

            val repository = (application as FocusApplication).repository
            val block = repository.getLongTermBlockById(blockId) ?: return@withLock false
            val limit = block.dailyLimitSeconds ?: return@withLock false
            val today = currentEpochDay()

            val baseMillis = if (block.lastUsageResetEpochDay != today) 0L else block.usedMillisToday
            val newMillis = baseMillis + elapsedMillis
            val newSeconds = newMillis / 1000L
            repository.updateLongTermBlockUsage(blockId, newSeconds, newMillis, today)

            if (quotaTrackingBlockId == blockId) {
                quotaTrackingStartMs = windowEnd
            }

            newMillis >= limit * 1000L
        }
    }

    private fun stopQuotaTrackingAndPersist() {
        quotaTickerJob?.cancel()
        quotaTickerJob = null

        val blockId = quotaTrackingBlockId
        val startMs = quotaTrackingStartMs

        quotaTrackingBlockId = null
        quotaTrackingPackage = null

        if (blockId != null) {
            serviceScope.launch {
                try {
                    persistQuotaProgress(blockId, startMs)
                } catch (e: Exception) {
                    Log.e("FocusService", "Error persisting quota progress", e)
                }
            }
        }
    }

    private fun closeBlockedAppThenBlock(packageName: String) {
        if (Settings.canDrawOverlays(this)) {
            showOverlay(packageName)
        } else {
            triggerOverlayPermissionRequest()
        }
    }

    private fun handleSettingsScreen(
        root: AccessibilityNodeInfo?,
        packageName: String,
        appName: String,
        now: Long
    ): Boolean {
        val extraDeviceWideKeywords = if (restrictSettingsFullyEnabled) {
            listOf("Modify system settings", "Usage access", "Battery optimization")
        } else {
            emptyList()
        }
        when (SettingsScreenClassifier.classify(root, appName, extraDeviceWideKeywords)) {
            SettingsScreenClassifier.ScreenType.PROTECTED_APP_DETAIL -> {
                if (now - lastSettingsBounceAtMs >= SETTINGS_BOUNCE_DEBOUNCE_MS) {
                    Log.d("FocusService", "Strict Mode: Bouncing back from our own app's Settings detail screen")
                    lastSettingsBounceAtMs = now
                    settingsHarmlessStreak = 0
                    settingsGateShownAtMs = now
                    bounceBackFromSettingsBypass(packageName)
                }
                return true
            }
            SettingsScreenClassifier.ScreenType.PROTECTED_DEVICE_WIDE -> {
                if (now - lastDeviceWideBlockAtMs >= DEVICE_WIDE_BLOCK_DEBOUNCE_MS) {
                    Log.d("FocusService", "Strict Mode: Blocking settings bypass action in $packageName")
                    lastDeviceWideBlockAtMs = now
                    settingsHarmlessStreak = 0
                    settingsGateShownAtMs = now
                    triggerBlockActivity(packageName, isLongTerm = false, reason = "Settings bypass action is blocked in Strict Mode.", endDate = sessionEndTime)
                }
                return true
            }
            SettingsScreenClassifier.ScreenType.LIST, SettingsScreenClassifier.ScreenType.SAFE -> {
                if (overlayView != null && currentBlockedPackage == packageName) {
                    settingsHarmlessStreak++
                    val elapsedMs = now - settingsGateShownAtMs
                    if (settingsHarmlessStreak >= SETTINGS_RELEASE_MIN_STREAK &&
                        elapsedMs >= SETTINGS_RELEASE_MIN_ELAPSED_MS
                    ) {
                        removeOverlay()
                    }
                }
            }
        }
        return false
    }

    private fun startSettingsWatch() {
        settingsWatchUntilMs = TrustedClock.now() + SETTINGS_WATCH_WINDOW_MS
        if (settingsWatchJob?.isActive == true) return
        settingsWatchJob = serviceScope.launch {
            val appName = applicationContext.getString(R.string.app_name)
            while (TrustedClock.now() < settingsWatchUntilMs) {
                delay(SETTINGS_WATCH_INTERVAL_MS)
                val strict = isSessionActive && TrustedClock.now() < sessionEndTime && isSessionStrict
                if (!strict) break
                val root = try { rootInActiveWindow } catch (e: Exception) { null } ?: continue
                try {
                    if (root.packageName?.toString() != "com.android.settings") break
                    handleSettingsScreen(root, "com.android.settings", appName, TrustedClock.now())
                } finally {
                    root.recycle()
                }
            }
        }
    }

    private fun bounceBackFromSettingsBypass(packageName: String) {
        val repository = (application as FocusApplication).repository
        serviceScope.launch {
            try {
                repository.incrementBlockedLaunches()
            } catch (e: Exception) {
                Log.e("FocusService", "Error incrementing blocked launch", e)
            }
        }

        try {
            val wentBack = performGlobalAction(GLOBAL_ACTION_BACK)
            if (!wentBack) {
                Log.d("FocusService", "GLOBAL_ACTION_BACK returned false for $packageName")
            }
        } catch (e: Exception) {
            Log.e("FocusService", "Error dispatching GLOBAL_ACTION_BACK", e)
        }

        removeOverlay()

        serviceScope.launch {
            delay(220)
            try {
                performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (e: Exception) {
                Log.e("FocusService", "Error dispatching second GLOBAL_ACTION_BACK", e)
            }
        }
    }

    private fun triggerBlockActivity(
        packageName: String,
        isLongTerm: Boolean,
        reason: String,
        endDate: Long,
        targetLabel: String = "",
        type: String = "APP"
    ) {
        val repository = (application as FocusApplication).repository
        serviceScope.launch {
            try {
                repository.incrementBlockedLaunches()
            } catch (e: Exception) {
                Log.e("FocusService", "Error incrementing blocked launch", e)
            }
        }

        lastBlockTriggerAtMs = TrustedClock.now()
        closeBlockedAppThenBlock(packageName)

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("BLOCKED_PACKAGE", packageName)
            putExtra("IS_LONG_TERM", isLongTerm)
            putExtra("LONG_TERM_REASON", reason)
            putExtra("LONG_TERM_END_DATE", endDate)
            putExtra("LONG_TERM_TARGET_LABEL", targetLabel)
            putExtra("LONG_TERM_TYPE", type)
            putExtra("END_TIME", endDate)
        }
        startActivity(intent)
    }

    private fun triggerAppLockPrompt(packageName: String) {
        if (Settings.canDrawOverlays(this)) {
            showOverlay(packageName)
        } else {
            triggerOverlayPermissionRequest()
        }
        val intent = Intent(this, com.example.AppLockUnlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("LOCKED_PACKAGE", packageName)
        }
        startActivity(intent)
    }

    fun grantAppUnlock(packageName: String) {
        unlockedLeftAtMs = 0L
        unlockedPackage = packageName
    }

    private fun extractHost(urlText: String): String {
        var url = urlText.trim().lowercase()
        if (url.isEmpty()) return ""

        if (url.startsWith("http://")) {
            url = url.substring(7)
        } else if (url.startsWith("https://")) {
            url = url.substring(8)
        }

        val firstSlash = url.indexOf('/')
        val firstQuestion = url.indexOf('?')
        val firstHash = url.indexOf('#')
        val firstColon = url.indexOf(':')
        
        var endIndex = url.length
        if (firstSlash in 0 until endIndex) endIndex = firstSlash
        if (firstQuestion in 0 until endIndex) endIndex = firstQuestion
        if (firstHash in 0 until endIndex) endIndex = firstHash
        if (firstColon in 0 until endIndex) endIndex = firstColon

        return url.substring(0, endIndex).trim()
    }

    private fun findUrlBarNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val id = node.viewIdResourceName ?: ""
            val isUrlBar = id.endsWith("/url_bar") ||
                    id.endsWith("/url_bar_title") ||
                    id.endsWith("/url_bar_text") ||
                    id.endsWith("/url_field") ||
                    id.endsWith("/location_bar_edit_text") ||
                    id.endsWith("/omnibarTextInput") ||
                    id.endsWith("/search_box_text") ||
                    id.contains("mozac_browser_toolbar_url_view")

            if (isUrlBar) {
                while (queue.isNotEmpty()) {
                    val rem = queue.removeFirst()
                    if (rem !== root) rem.recycle()
                }
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            if (node !== root) node.recycle()
        }
        return null
    }

    private fun findBlockedWebsite(node: AccessibilityNodeInfo?, blockedWebsites: Set<String>): String? {
        val urlBarNode = findUrlBarNode(node) ?: return null
        try {
            if (urlBarNode.isFocused || urlBarNode.isAccessibilityFocused) {
                return null
            }

            val urlText = urlBarNode.text?.toString() ?: return null
            val host = extractHost(urlText)
            if (host.isEmpty()) return null

            val cleanHost = host.removePrefix("www.")
            for (web in blockedWebsites) {
                val cleanWeb = extractHost(web).removePrefix("www.")
                if (cleanWeb.isNotEmpty() && (cleanHost == cleanWeb || cleanHost.endsWith("." + cleanWeb))) {
                    return web
                }
            }
            return null
        } finally {
            if (urlBarNode !== node) {
                urlBarNode.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Log.d("FocusService", "Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        if (instance == this) {
            instance = null
        }
        if (screenOffReceiverRegistered) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (e: Exception) {
                Log.e("FocusService", "Error unregistering screen-off receiver", e)
            }
            screenOffReceiverRegistered = false
        }
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e("FocusService", "Error stopping foreground", e)
        }
        serviceJob.cancel()
    }

    private fun checkNodeForIdKeyword(node: AccessibilityNodeInfo?, keyword: String): Boolean {
        if (node == null) return false
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (id.contains(keyword)) {
            if (!id.contains("tab") && !id.contains("button") && !id.contains("icon") && !id.contains("tray")) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = checkNodeForIdKeyword(child, keyword)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun triggerContentBlockActivity(
        packageName: String,
        contentType: String,
        customEndTime: Long = sessionEndTime
    ) {
        val repository = (application as FocusApplication).repository
        serviceScope.launch {
            try {
                repository.incrementBlockedLaunches()
            } catch (e: Exception) {
                Log.e("FocusService", "Error incrementing blocked launch", e)
            }
        }

        lastBlockTriggerAtMs = TrustedClock.now()
        closeBlockedAppThenBlock(packageName)

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("BLOCKED_PACKAGE", packageName)
            putExtra("IS_CONTENT_LEVEL", true)
            putExtra("CONTENT_TYPE", contentType)
            putExtra("END_TIME", customEndTime)
        }
        startActivity(intent)
    }

    private fun collectScreenText(node: AccessibilityNodeInfo?, list: MutableList<String>, depth: Int) {
        if (node == null || depth > 50) return
        val text = node.text?.toString()
        if (!text.isNullOrEmpty()) list.add(text)
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrEmpty()) list.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScreenText(child, list, depth + 1)
            child.recycle()
        }
    }

    private fun checkNodeForShortsSpecifics(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (id.contains("shorts_player") || id.contains("shorts_video") || id.contains("shorts_reel") || id.contains("reel_player")) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = checkNodeForShortsSpecifics(child)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun checkNodeForReelsSpecifics(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if ((id.contains("clips_video_container") || id.contains("clips_viewer") || id.contains("reel_viewer") || id.contains("reels_viewer")) &&
            !id.contains("tab") && !id.contains("button") && !id.contains("icon") && !id.contains("tray")
        ) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = checkNodeForReelsSpecifics(child)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun checkNodeForSpotlightSpecifics(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val id = node.viewIdResourceName?.lowercase() ?: ""
        if (id.contains("spotlight") && !id.contains("tab") && !id.contains("button") && !id.contains("icon")) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = checkNodeForSpotlightSpecifics(child)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun showOverlay(packageName: String, opaque: Boolean = true) {
        try {
            val color = if (opaque && USE_APP_BACKDROP) BACKDROP_COLOR else Color.TRANSPARENT
            if (overlayView == null) {
                val cover = View(this).apply { setBackgroundColor(color) }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
                windowManager.addView(cover, params)
                overlayView = cover
                currentBlockedPackage = packageName
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (overlayView == cover) removeOverlay()
                }, OVERLAY_SAFETY_TIMEOUT_MS)
            } else {
                currentBlockedPackage = packageName
                if (opaque) overlayView?.setBackgroundColor(color)
            }
        } catch (e: Exception) {
            Log.e("FocusService", "Error adding overlay view", e)
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        currentBlockedPackage = null
        val removeAction = Runnable {
            try {
                if (view.isAttachedToWindow) {
                    windowManager.removeViewImmediate(view)
                } else {
                    windowManager.removeView(view)
                }
                Log.d("FocusService", "Removed overlay view")
            } catch (e: Exception) {
                Log.e("FocusService", "Failed to remove overlay view", e)
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            removeAction.run()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(removeAction)
        }
    }

    fun dismissInstantOverlay() {
        removeOverlay()
    }

    private fun triggerOverlayPermissionRequest() {
        val now = TrustedClock.now()
        if (now - lastPermissionRequestTime > 10000L) {
            lastPermissionRequestTime = now
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                Log.d("FocusService", "Triggered overlay permission request intent")
            } catch (e: Exception) {
                Log.e("FocusService", "Failed to start overlay permission activity", e)
            }
        }
    }
}
