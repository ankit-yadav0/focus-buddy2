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
import com.example.UninstallFrictionActivity
import com.example.FocusApplication
import com.example.R
import com.example.data.LongTermBlock
import com.example.data.WebsiteBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.provider.Settings
import android.view.WindowManager
import android.view.View
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.ViewGroup

class FocusAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: FocusAccessibilityService? = null
            private set

        val currentPackage = kotlinx.coroutines.flow.MutableStateFlow("None")
        val currentActivity = kotlinx.coroutines.flow.MutableStateFlow("None")
        val visibleText = kotlinx.coroutines.flow.MutableStateFlow("No text detected")
        val lastEvent = kotlinx.coroutines.flow.MutableStateFlow("No event yet")
        val shortsDetectionStatus = kotlinx.coroutines.flow.MutableStateFlow("Shorts Not Detected")

        private const val NOTIFICATION_CHANNEL_ID = "focus_buddy_protection"
        private const val FOREGROUND_NOTIFICATION_ID = 4201

        // Minimum spacing between processed TYPE_WINDOW_CONTENT_CHANGED events. These events
        // fire very rapidly (every keystroke, scroll, animation frame) and re-walking the node
        // tree on every single one is a major source of CPU/GC pressure on low-RAM Go devices,
        // which in turn makes the process a more likely low-memory-killer target. Window state
        // changes (app/tab switches) are never throttled so blocking still reacts instantly.
        private const val CONTENT_CHANGED_THROTTLE_MS = 300L
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var isSessionActive = false
    private var sessionEndTime: Long = 0
    private var isSessionStrict = false
    private var blockedPackages = setOf<String>()
    private var restrictSettingsFullyEnabled = false
    private var longTermBlocks = listOf<LongTermBlock>()
    private var activeWebsitesList = listOf<WebsiteBlock>()
    private var lastContentChangedProcessTime = 0L

    // Daily time-quota tracking: while a quota-mode long-term-blocked app is in the
    // foreground, we track how long it's been open and periodically add that to its
    // persisted usedSecondsToday, so quota enforcement works both across app
    // launches (checked at window-state-change) and mid-session (via the ticker).
    private var quotaTrackingBlockId: Int? = null
    private var quotaTrackingPackage: String? = null
    private var quotaTrackingStartMs: Long = 0L
    private var quotaTickerJob: kotlinx.coroutines.Job? = null

    private var overlayView: View? = null
    private val windowManager: WindowManager by lazy { getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager }
    private var currentBlockedPackage: String? = null
    private var lastPermissionRequestTime = 0L

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

            // Observe the Strict Mode wizard's "Phone Settings" restriction toggle
            serviceScope.launch {
                repository.getSettingFlow("strict_restrict_settings").collectLatest { value ->
                    restrictSettingsFullyEnabled = value?.toBoolean() ?: false
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

        // Some OEM skins (especially on Go Edition) don't fully honor every attribute
        // declared in accessibility_service_config.xml, so we re-assert the important
        // ones in code as a safety net. FLAG_REPORT_VIEW_IDS in particular is required
        // for AccessibilityNodeInfo.viewIdResourceName to ever be populated - without it,
        // every id-based lookup in this file (URL bar, Shorts/Reels/Spotlight detection)
        // silently returns nothing, which is what was breaking website blocking.
        serviceInfo = serviceInfo?.apply {
            flags = flags or 
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or 
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
    }

    /**
     * Pins this service's process at foreground priority via a low-importance,
     * silent notification. AccessibilityService is itself a Service, so it can call
     * startForeground() on itself. This is the single biggest lever against Android
     * Go's aggressive low-memory killer: background/cached processes are killed far
     * more readily than ones holding a foreground priority, and Go devices reclaim
     * RAM much more aggressively than standard Android due to limited memory.
     */
    private fun startForegroundProtection() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = getSystemService(NotificationManager::class.java)
                if (notificationManager?.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Focus Buddy Protection",
                        NotificationManager.IMPORTANCE_MIN
                    ).apply {
                        description = "Keeps app and website blocking running in the background"
                        setShowBadge(false)
                    }
                    notificationManager?.createNotificationChannel(channel)
                }
            }

            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Focus Buddy is protecting your focus")
                .setContentText("App and website blocking is active")
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
            // Best-effort optimization only - never let this take the service down.
            Log.e("FocusService", "Could not start foreground protection", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && 
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        if (overlayView != null && packageName != currentBlockedPackage) {
            removeOverlay()
        }

        // TYPE_WINDOW_CONTENT_CHANGED fires on nearly every keystroke/scroll/animation
        // frame. Re-walking the node tree for each one is a major CPU/GC cost on
        // low-RAM Go devices, so we throttle it. WINDOW_STATE_CHANGED (app/tab
        // switches) is never throttled, so app/website blocking still reacts instantly.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (packageName != "com.android.settings") {
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastContentChangedProcessTime < CONTENT_CHANGED_THROTTLE_MS) return
                lastContentChangedProcessTime = nowMs
            }
        }

        // Update live diagnostics variables
        currentPackage.value = packageName

        // If a quota-tracked app is no longer in the foreground, stop the ticker and
        // persist however much time was actually spent in it this session.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            quotaTrackingPackage != null && quotaTrackingPackage != packageName
        ) {
            stopQuotaTrackingAndPersist()
        }

        val eventTypeStr = when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            else -> "EVENT_$eventType"
        }
        lastEvent.value = "$eventTypeStr - Class: ${event.className}, Text: ${event.text}"

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val classNameStr = event.className?.toString() ?: ""
            if (classNameStr.contains(".") || classNameStr.endsWith("Activity")) {
                currentActivity.value = classNameStr
            }
        }

        // Fetch the window's node tree exactly ONCE per event and reuse it for every
        // check below (diagnostics, website blocking, Shorts/Reels/Spotlight detection).
        // The previous implementation queried rootInActiveWindow a second time for the
        // website-blocking check, doubling the IPC + tree-walk cost of every event.
        val rootNode = rootInActiveWindow
        val usingFallbackSource = rootNode == null
        val nodeToUse = rootNode ?: event.source

        try {
            val screenTexts = mutableListOf<String>()
            if (nodeToUse != null) {
                collectScreenText(nodeToUse, screenTexts, 0)
            }
            val textString = screenTexts.joinToString(", ")
            visibleText.value = if (textString.length > 1000) textString.take(1000) + "..." else textString

            // The indented full-tree dump is a debug convenience only used for YouTube
            // logging - build it lazily, only for that package, instead of on every
            // single event for every app (which was the largest per-event allocation).
            if (packageName == "com.google.android.youtube") {
                val nodeTreeDump = StringBuilder()
                if (nodeToUse != null) {
                    buildNodeTreeDump(nodeToUse, nodeTreeDump, 0)
                }
                Log.d("FocusService", "YouTube Event: $eventTypeStr")
                Log.d("FocusService", "YouTube Screen Text: $textString")
                Log.d("FocusService", "YouTube Node Tree:\n$nodeTreeDump")
            }

            // Don't block our own app or common system tasks
            val ourPackage = applicationContext.packageName
            if (packageName == ourPackage) return

            val appName = applicationContext.getString(R.string.app_name)

            val now = System.currentTimeMillis()

            // Strict-Mode Guard
            val isStrictModeActive = isSessionActive && now < sessionEndTime && isSessionStrict
            if (isStrictModeActive) {
                // Blocking Rules: block uninstallation
                if (packageName == "com.android.packageinstaller" || packageName == "com.google.android.packageinstaller") {
                    Log.d("FocusService", "Strict Mode: Blocking app uninstaller: $packageName")
                    triggerBlockActivity(packageName, isLongTerm = false, reason = "App uninstallation is blocked in Strict Mode.", endDate = sessionEndTime)
                    return
                }
                // Selective Settings Rules
                if (packageName == "com.android.settings") {
                    if (restrictSettingsFullyEnabled) {
                        // Wizard's "Phone Settings" restriction is on for this session -
                        // block Settings entirely, not just specific bypass actions.
                        Log.d("FocusService", "Strict Mode: Phone Settings restriction is on - blocking Settings entirely")
                        triggerBlockActivity(packageName, isLongTerm = false, reason = "Phone Settings is blocked for this Strict Mode session.", endDate = sessionEndTime)
                        return
                    }
                    val bypassKeywords = listOf(
                        "Reset", "Factory reset", "Erase all data",
                        "Clear storage", "Clear data", "Clear cache", "Storage & cache",
                        "Force stop", "Uninstall",
                        "Apps & notifications"
                    )
                    val containsBypass = screenTexts.any { text ->
                        bypassKeywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
                    }
                    if (containsBypass) {
                        Log.d("FocusService", "Strict Mode: Blocking settings bypass action in $packageName")
                        triggerBlockActivity(packageName, isLongTerm = false, reason = "Settings bypass action is blocked in Strict Mode.", endDate = sessionEndTime)
                        return
                    }
                }
            }

            // Always-on Uninstall Friction Guard for Focus Buddy (independent of Strict Mode
            // session). Redirects into the app's real 600-word uninstall flow instead of a
            // dead-end block screen, the moment Settings shows Focus Buddy's own App Info
            // screen with an "Uninstall" action visible, or the system package installer's
            // uninstall confirmation appears directly.
            if (!isStrictModeActive) {
                val isUninstallerScreen = packageName == "com.android.packageinstaller" || packageName == "com.google.android.packageinstaller"
                val isOwnAppInfoWithUninstall = packageName == "com.android.settings" &&
                    screenTexts.any { it.contains(appName, ignoreCase = true) } &&
                    screenTexts.any { it.contains("Uninstall", ignoreCase = true) }

                if (isUninstallerScreen || isOwnAppInfoWithUninstall) {
                    Log.d("FocusService", "Detected external uninstall attempt - redirecting to in-app flow")
                    if (Settings.canDrawOverlays(this)) {
                        showOverlay(packageName)
                    } else {
                        triggerOverlayPermissionRequest()
                    }
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

                // 2. Check for active Long-Term App Blocks
                val activeLongTermAppBlock = longTermBlocks.firstOrNull {
                    it.type == "APP" && it.target == packageName && now >= it.startDate && now <= it.endDate && it.isActive
                }
                if (activeLongTermAppBlock != null) {
                    val limit = activeLongTermAppBlock.dailyLimitSeconds
                    if (limit == null) {
                        // Full block - unchanged behavior.
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
                        // Daily time-quota mode: reset the counter if the day has rolled
                        // over, then either block (quota already used up today) or allow
                        // the app to open and start tracking foreground time against it.
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
            }

            // 3. Check for active Long-Term Website Blocks (Inspect browser URL/node contents)
            val browserPackages = setOf(
                "com.android.chrome",
                "org.mozilla.firefox",
                "com.opera.browser",
                "com.sec.android.app.sbrowser",
                "com.microsoft.emmx",
                "com.duckduckgo.mobile.android",
                "com.brave.browser"
            )
            if (browserPackages.contains(packageName)) {
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

            // Shorts detection logic
            // A single loose signal isn't enough on its own: the Shorts *shelf* (a
            // horizontal row of Shorts previews shown inline on the home/subscriptions
            // feed, visible without ever opening a Short) can trip a bare class-name or
            // resource-id match, and "Dislike" appears on every video, Shorts or not.
            // So we require at least two independent signals to agree before treating
            // it as the user actually being inside full-screen Shorts/Reels playback.
            var isShortsDetected = false
            if (packageName == "com.google.android.youtube") {
                val hasShortsText = screenTexts.any { it.contains("Shorts", ignoreCase = true) }
                val hasShortsClass = event.className?.toString()?.lowercase()?.contains("shorts") == true ||
                                     event.className?.toString()?.lowercase()?.contains("reel") == true
                val hasShortsId = checkNodeForShortsSpecifics(nodeToUse)
                val hasShortsDescription = screenTexts.any { it.contains("shorts player", ignoreCase = true) || it.contains("reel player", ignoreCase = true) }
                val hasRemixAction = screenTexts.any { it.equals("Remix", ignoreCase = true) }

                val signalCount = listOf(hasShortsClass, hasShortsId, hasShortsDescription, hasRemixAction).count { it }

                // hasShortsDescription alone is already a strong, specific signal
                // ("shorts player" / "reel player" text doesn't show up outside actual
                // playback), so it's enough by itself. Everything else needs at least
                // one corroborating signal, and bare "Shorts" text (e.g. the nav tab
                // label, always visible) never counts as a signal on its own.
                if (hasShortsDescription || signalCount >= 2 || (hasShortsText && signalCount >= 1)) {
                    isShortsDetected = true
                }
            }

            if (packageName == "com.google.android.youtube") {
                val previousStatus = shortsDetectionStatus.value
                if (isShortsDetected) {
                    shortsDetectionStatus.value = "Shorts Detected!"
                    if (previousStatus != "Shorts Detected!") {
                        // Show Toast on Main UI thread and only on state transition to prevent excessive spamming/suppression
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            try {
                                android.widget.Toast.makeText(applicationContext, "Shorts Detected", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("FocusService", "Failed to show Toast", e)
                            }
                        }
                    }
                } else {
                    shortsDetectionStatus.value = "Shorts Not Detected"
                }
            }

            // 4. Content-Level Blocking (YouTube Shorts, Instagram Reels, Snapchat Spotlight)
            // Content-Level Blocking must only be active during Strict Mode.
            if (isStrictModeActive) {
                val prefs = getSharedPreferences("focuss_buddy_settings", MODE_PRIVATE)

                if (packageName == "com.google.android.youtube" && prefs.getBoolean("youtube_block_shorts", false)) {
                    if (isShortsDetected) {
                        Log.d("FocusService", "Content-Level Block: YouTube Shorts detected")
                        triggerContentBlockActivity(packageName, "YouTube Shorts")
                        return
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
            }
        } finally {
            // Always release the node(s) we obtained at the top of this event, on every
            // return path. The previous implementation only recycled the happy-path
            // rootInActiveWindow and never released event.source when it was used as a
            // fallback, leaking AccessibilityNodeInfo instances on every such event.
            if (usingFallbackSource) {
                nodeToUse?.recycle()
            } else {
                rootNode?.recycle()
            }
        }
    }

    /**
     * Current epoch day (days since 1970-01-01, in the device's local timezone) -
     * used to detect the midnight rollover so usedSecondsToday resets automatically.
     */
    private fun currentEpochDay(): Long {
        return System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
    }

    /**
     * Begins tracking foreground time for a quota-mode long-term-blocked app. Starts a
     * ticker that periodically persists elapsed time and checks whether the daily
     * limit has now been crossed mid-session (not just at the next app launch).
     */
    private fun startQuotaTracking(block: LongTermBlock) {
        quotaTrackingBlockId = block.id
        quotaTrackingPackage = block.target
        quotaTrackingStartMs = System.currentTimeMillis()

        quotaTickerJob?.cancel()
        quotaTickerJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(2_000L)
                val currentBlockId = quotaTrackingBlockId ?: break
                val currentStartMs = quotaTrackingStartMs
                val exceeded = persistQuotaProgress(currentBlockId, currentStartMs)
                if (exceeded) {
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

    /**
     * Adds elapsed foreground time (since [startMs]) to the persisted usedSecondsToday
     * for [blockId], handling the midnight reset if the day has rolled over. Returns
     * true if the daily limit is now exceeded.
     *
     * blockId/startMs are passed in explicitly (rather than read from the shared
     * quotaTracking* fields at call time) so that a caller can snapshot them before
     * those fields get reset/nulled elsewhere - see stopQuotaTrackingAndPersist().
     * Reading the shared fields directly here previously raced with
     * stopQuotaTrackingAndPersist() nulling them before this suspend function got a
     * chance to run, silently dropping the final segment of usage on every app switch.
     */
    private suspend fun persistQuotaProgress(blockId: Int, startMs: Long): Boolean {
        val elapsedMillis = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)

        val repository = (application as FocusApplication).repository
        val block = repository.getLongTermBlockById(blockId) ?: return false
        val limit = block.dailyLimitSeconds ?: return false
        val today = currentEpochDay()

        // Accumulate in milliseconds so brief sub-second segments (rapid Reels
        // scrolling, quick app peeks) don't get truncated away - only the final
        // derived usedSecondsToday (for display/threshold checks elsewhere) is
        // floored to whole seconds, from an otherwise lossless running total.
        val baseMillis = if (block.lastUsageResetEpochDay != today) 0L else block.usedMillisToday
        val newMillis = baseMillis + elapsedMillis
        val newSeconds = newMillis / 1000L
        repository.updateLongTermBlockUsage(blockId, newSeconds, newMillis, today)

        // Only reset the shared tracking window if we're still actively tracking this
        // same block - guards against clobbering a newer tracking window started
        // concurrently (e.g. the user left and immediately reopened the same app).
        if (quotaTrackingBlockId == blockId) {
            quotaTrackingStartMs = System.currentTimeMillis()
        }

        return newMillis >= limit * 1000L
    }

    /** Stops tracking (app switched away or session ending) and persists final elapsed time. */
    private fun stopQuotaTrackingAndPersist() {
        quotaTickerJob?.cancel()
        quotaTickerJob = null

        // Snapshot before clearing the shared fields below, so the async persist call
        // still has valid values to work with regardless of scheduling order.
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

        if (Settings.canDrawOverlays(this)) {
            showOverlay(packageName)
        } else {
            triggerOverlayPermissionRequest()
        }

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
        val firstColon = url.indexOf(':')
        
        var endIndex = url.length
        if (firstSlash in 0 until endIndex) endIndex = firstSlash
        if (firstQuestion in 0 until endIndex) endIndex = firstQuestion
        if (firstColon in 0 until endIndex) endIndex = firstColon

        return url.substring(0, endIndex).trim()
    }

    /**
     * Breadth-first search for the browser's URL/address bar node, identified by its
     * view id (requires FLAG_REPORT_VIEW_IDS to be set - see onServiceConnected()).
     *
     * Unlike a naive recursive search, this recycles every visited node that doesn't
     * match once it has been checked (and drains the remaining queue on an early
     * match), instead of leaking every intermediate AccessibilityNodeInfo obtained via
     * getChild(). On a full page's node tree that can be hundreds of nodes per event,
     * so this matters for GC pressure on low-RAM Go devices. The root node passed in is
     * owned by the caller and is left untouched.
     */
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
                while (queue.isNotEmpty()) queue.removeFirst().recycle()
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
            // If the URL bar is currently focused, the user is actively typing or editing.
            // Do not block in this state (prevents premature triggers during search suggestions/autocomplete).
            if (urlBarNode.isFocused || urlBarNode.isAccessibilityFocused) {
                return null
            }

            val urlText = urlBarNode.text?.toString() ?: return null
            val host = extractHost(urlText)
            if (host.isEmpty()) return null

            val cleanHost = host.removePrefix("www.")
            for (web in blockedWebsites) {
                val cleanWeb = web.lowercase().trim().removePrefix("http://").removePrefix("https://").removePrefix("www.")
                if (cleanWeb.isNotEmpty() && (cleanHost == cleanWeb || cleanHost.endsWith("." + cleanWeb))) {
                    return web
                }
            }
            return null
        } finally {
            urlBarNode.recycle()
        }
    }

    override fun onInterrupt() {
        Log.d("FocusService", "Service Interrupted")
    }

    override fun onDestroy() {
        // Best-effort final flush of any in-progress quota tracking before the
        // coroutine scope dies below. This can't help against a silent low-memory
        // kill (Android gives no callback at all for that - see the shortened
        // ticker interval above, which is the real mitigation for that case), but
        // it does catch normal teardowns: the user disabling the accessibility
        // service, force-stopping the app, etc.
        val blockId = quotaTrackingBlockId
        val startMs = quotaTrackingStartMs
        if (blockId != null) {
            try {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(1500L) {
                        persistQuotaProgress(blockId, startMs)
                    }
                }
            } catch (e: Exception) {
                Log.e("FocusService", "Error flushing quota progress on destroy", e)
            }
        }

        super.onDestroy()
        if (instance == this) {
            instance = null
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
            if (!id.contains("tab") && !id.contains("button") && !id.contains("icon")) {
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

    private fun triggerContentBlockActivity(packageName: String, contentType: String) {
        if (Settings.canDrawOverlays(this)) {
            showOverlay(packageName)
        } else {
            triggerOverlayPermissionRequest()
        }

        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra("BLOCKED_PACKAGE", packageName)
            putExtra("IS_CONTENT_LEVEL", true)
            putExtra("CONTENT_TYPE", contentType)
        }
        startActivity(intent)
    }

    /**
     * Lightweight tree walk that only gathers visible text/content-descriptions.
     * This runs on every processed event for every app, so it deliberately avoids
     * building the indented string-dump representation that the old combined
     * function produced unconditionally (see buildNodeTreeDump for that, which is
     * now only invoked for the one package that actually logs it).
     */
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

    /** Verbose indented tree dump used only for YouTube debug logging. */
    private fun buildNodeTreeDump(node: AccessibilityNodeInfo?, builder: StringBuilder, depth: Int) {
        if (node == null || depth > 50) return
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        val indent = "  ".repeat(depth.coerceAtMost(10))
        builder.append("$indent[$className] id=$viewId text='$text' desc='$desc'\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            buildNodeTreeDump(child, builder, depth + 1)
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
        if (id.contains("clips") || id.contains("reels") || id.contains("reel_viewer") || id.contains("clips_video_container")) {
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
        if (id.contains("spotlight")) {
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

    private fun showOverlay(packageName: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                if (overlayView == null) {
                    val textView = TextView(this).apply {
                        text = "App Blocked!"
                        textSize = 24f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        setBackgroundColor(Color.parseColor("#121212")) // Premium dark background
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
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
                    windowManager.addView(textView, params)
                    overlayView = textView
                    currentBlockedPackage = packageName
                    Log.d("FocusService", "Overlay added successfully for $packageName")
                } else {
                    currentBlockedPackage = packageName
                }
            } catch (e: Exception) {
                Log.e("FocusService", "Error adding overlay view", e)
            }
        }
    }

    private fun removeOverlay() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                overlayView?.let {
                    windowManager.removeView(it)
                    overlayView = null
                    currentBlockedPackage = null
                    Log.d("FocusService", "Removed overlay view")
                }
            } catch (e: Exception) {
                Log.e("FocusService", "Failed to remove overlay view", e)
            }
        }
    }

    fun dismissInstantOverlay() {
        removeOverlay()
    }

    private fun triggerOverlayPermissionRequest() {
        val now = System.currentTimeMillis()
        if (now - lastPermissionRequestTime > 10000L) { // 10 seconds cooldown to avoid spamming intents
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
