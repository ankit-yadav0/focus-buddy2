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

        // Safety-net cap on how long the instant-block overlay can stay up if
        // BlockActivity never calls dismissInstantOverlay() (e.g. it fails to launch).
        private const val OVERLAY_SAFETY_TIMEOUT_MS = 4_000L
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

    // App Lock (PIN-gated apps): packages the user chose to lock, and which one (if
    // any) has already had its PIN entered for the current foreground visit. The
    // grant is cleared the moment the foreground package changes to anything else,
    // so re-opening a locked app always asks for the PIN again.
    private var lockedPackages = setOf<String>()
    private var unlockedPackage: String? = null

    // Daily time-quota tracking: while a quota-mode long-term-blocked app is in the
    // foreground, we track how long it's been open and periodically add that to its
    // persisted usedSecondsToday, so quota enforcement works both across app
    // launches (checked at window-state-change) and mid-session (via the ticker).
    private var quotaTrackingBlockId: Int? = null
    private var quotaTrackingPackage: String? = null
    private var quotaTrackingStartMs: Long = 0L
    private var quotaTickerJob: kotlinx.coroutines.Job? = null

    /**
     * Guards the read-modify-write in persistQuotaProgress() below. Without this, the
     * ticker's periodic persist and the stop-triggered persist (fired when an app-switch
     * event races the ticker mid-write) can both read the same row's usedMillisToday
     * before either has written back, then write independently - one overwriting the
     * other (lost update) or both adding overlapping elapsed windows (double-count).
     * Job cancellation alone doesn't prevent this: once the underlying Room write for a
     * given call has actually started on the IO thread, cancelling that coroutine's Job
     * does not abort the in-flight SQL statement, so the write still lands even though
     * the caller goes on to throw CancellationException. Serializing the whole
     * read-then-write section on this mutex means a second caller can only start its own
     * read after the first caller's write (if any) has fully committed, so it always
     * works from a fresh, consistent value.
     */
    private val quotaPersistMutex = Mutex()
    private var quotaHeartbeatJob: kotlinx.coroutines.Job? = null

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

            // Independent watchdog that expires a finished session on a fixed clock,
            // regardless of whether any app-switch (WINDOW_STATE_CHANGED) event happens
            // to fire around the same time. Without this, a session (in particular a
            // Strict Mode session, which the user cannot end early any other way) could
            // stay marked active - and its app blocking / settings lock kept in force -
            // for an unbounded extra stretch if the phone just sits on one screen past
            // the timer's actual end time. This runs inside the foreground service
            // itself, not the ViewModel, so it keeps working even if MainActivity has
            // been closed or swiped away.
            serviceScope.launch {
                while (true) {
                    try {
                        if (isSessionActive && System.currentTimeMillis() >= sessionEndTime) {
                            repository.stopActiveSession("Expired")
                        }
                    } catch (e: Exception) {
                        Log.e("FocusService", "Error in session-expiry watchdog", e)
                    }
                    delay(5_000L)
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

        startQuotaHeartbeat()
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

    /**
     * True for windows that pop over the current app without the user actually
     * switching away from it - soft keyboard, runtime permission dialogs, the
     * notification shade / quick settings, share sheets. Used to keep App Lock's
     * unlock grant alive through normal in-app activity (typing, calls, etc.)
     * instead of revoking it on every transient system window.
     */
    private fun isTransientSystemPackage(packageName: String): Boolean {
        if (packageName == "com.android.systemui") return true
        if (packageName == "android") return true
        if (packageName == "com.android.permissioncontroller" ||
            packageName == "com.google.android.permissioncontroller"
        ) return true
        // In-call / telecom UI: OEMs each ship this under a different package
        // (com.android.dialer, com.android.incallui, com.coloros.dialer,
        // com.realme.dialer, com.android.server.telecom, and more). A voice/video
        // call started from a locked app (WhatsApp, Messenger, etc.) can briefly
        // foreground whichever one this device uses, without the user having
        // actually left the locked app - so match generically instead of trying
        // to enumerate every OEM's exact package name.
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && 
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Only tear the overlay down once OUR OWN app (BlockActivity, or MainActivity
        // for the uninstall-friction redirect) is actually the foreground package -
        // not on the very next event with any differing package. The block flow
        // dispatches GLOBAL_ACTION_HOME first, which briefly foregrounds the launcher
        // before BlockActivity draws; removing the overlay on that launcher event (as
        // the old check did) tore it down before BlockActivity could cover the
        // transition, causing a visible home-screen flash. BlockActivity.onResume()
        // already calls dismissInstantOverlay() explicitly for the normal case; this
        // check now only acts as a safety net for when our app truly regains focus.
        if (overlayView != null && packageName == applicationContext.packageName) {
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
        // persist however much time was actually spent in it this session. Same
        // transient-window guard as the App Lock check below - a keyboard popup or
        // permission dialog inside the tracked app must not prematurely stop the
        // ticker and split/undercount the session's usage.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            quotaTrackingPackage != null && quotaTrackingPackage != packageName &&
            !isTransientSystemPackage(packageName)
        ) {
            stopQuotaTrackingAndPersist()
        }

        // A previously PIN-unlocked app is only "unlocked" for as long as it stays in
        // the foreground. The moment focus moves to any other REAL app, drop the grant
        // so re-entering it later asks for the PIN again.
        //
        // IMPORTANT: don't drop the grant just because SOME other package briefly
        // reported a window-state-changed event. Transient system windows constantly
        // pop over a locked app without the user ever actually leaving it - the soft
        // keyboard when typing in a chat, a runtime permission dialog (mic/camera for
        // a call), the notification shade, share sheets, the in-call UI overlay. Each
        // of those has its own packageName, and treating them as "the user switched
        // apps" was wiping the unlock on every keystroke/tab-switch/call inside the
        // locked app itself, forcing the PIN again a moment later even though the user
        // never left. Only a transition to a genuine other app should revoke it.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            unlockedPackage != null && unlockedPackage != packageName &&
            !isTransientSystemPackage(packageName)
        ) {
            unlockedPackage = null
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
                // Blocking Rules: block uninstallation of FOCUS BUDDY ITSELF only.
                // packageinstaller foregrounds for every install AND uninstall of ANY
                // app - installing a brand new app, or installing an updated Focus
                // Buddy APK signed with the same release key, both bring up this same
                // package. Blocking on packageName alone (as before) blocked ALL of
                // those too, not just uninstalling this app - so during an active
                // Strict session the user couldn't install anything at all, including
                // their own updated build. Only step in when the screen is actually
                // an uninstall confirmation naming this app.
                if (packageName == "com.android.packageinstaller" || packageName == "com.google.android.packageinstaller") {
                    val isUninstallingUs = screenTexts.any { it.contains(appName, ignoreCase = true) } &&
                        screenTexts.any { it.contains("uninstall", ignoreCase = true) }
                    if (isUninstallingUs) {
                        Log.d("FocusService", "Strict Mode: Blocking app uninstaller: $packageName")
                        triggerBlockActivity(packageName, isLongTerm = false, reason = "App uninstallation is blocked in Strict Mode.", endDate = sessionEndTime)
                        return
                    }
                    // Anything else here (installing/updating some other app, or even
                    // reinstalling/updating Focus Buddy itself) - let it proceed normally.
                }
                // Own-app protection: ALWAYS active during a Strict session, regardless
                // of the "Phone Settings" toggle below. If the user could freely reach
                // Focus Buddy's own App Info / Accessibility-service / Device-admin
                // toggle screens and disable them from there, Strict Mode would have no
                // teeth at all - so this part is unconditional whenever Strict Mode is
                // active, the same way it must always have been intended to work.
                if (packageName == "com.android.settings") {
                    // A screen that shows our own app's name is NOT enough by itself -
                    // "Focuss Buddy" can also show up as just one row in a plain LIST
                    // (Apps & notifications' "recently used apps" mini-list, a usage/
                    // battery list, a permission-manager app list) since its
                    // Accessibility Service keeps it near the top of most such lists.
                    // Bouncing on the name alone fired every time the user merely
                    // scrolled past a list that happened to mention this app, without
                    // ever tapping into it - not what we want. Require a genuine
                    // detail/toggle-page marker alongside the name - text that only
                    // appears once you're actually ON that app's own App Info,
                    // accessibility-service, or device-admin screen, never on a list
                    // row that just names it in passing.
                    val ownAppDetailMarkers = listOf(
                        "Uninstall", "Force stop", "Storage & cache", "App info",
                        "Open by default", "Mobile data & Wi", "Use service",
                        "Deactivate", "device admin", "This app can"
                    )
                    if (screenTexts.any { it.contains(appName, ignoreCase = true) } &&
                        screenTexts.any { text -> ownAppDetailMarkers.any { text.contains(it, ignoreCase = true) } }
                    ) {
                        Log.d("FocusService", "Strict Mode: Bouncing back from our own app's Settings screen")
                        bounceBackFromSettingsBypass(packageName)
                        return
                    }
                }
                // Broader Settings Rules - the "Phone Settings" wizard toggle that used to
                // gate this was removed from the UI, leaving restrictSettingsFullyEnabled
                // permanently false and this whole block unreachable dead code. Per
                // Ankit's decision, this is now unconditional (always on) during Strict
                // Mode instead of tied to a setting nothing can ever turn on - these GLOBAL
                // (not app-specific) screens (Reset, Factory reset, Device admin apps list,
                // Special app access) are blocked the same way the own-app protection above
                // always is. This never blocks Settings wholesale - WiFi/Bluetooth/mobile
                // data/display/sound etc. always stay reachable - it only silently backs
                // the user out (GLOBAL_ACTION_BACK, no overlay, no full "App Blocked"
                // screen) the moment one of these specific screens shows up.
                if (packageName == "com.android.settings") {
                    // IMPORTANT: only keywords here that are truly GLOBAL screen titles -
                    // ones that never appear on an arbitrary other app's own App Info /
                    // permission page. Anything that shows up on EVERY app's info page
                    // (Force stop, Uninstall, Clear cache/data, Storage & cache, Battery
                    // optimization, Usage access, Display over other apps, Modify system
                    // settings, Deactivate this device admin app) must NOT be listed here,
                    // or opening any other app's info screen bounces the user out too.
                    // Those per-app actions on OUR OWN app are already caught above by the
                    // "screenTexts contains appName" check - no keyword needed for that case.
                    val bypassKeywords = listOf(
                        "Reset", "Factory reset", "Erase all data",
                        "Device admin apps", "Special app access"
                    )
                    val containsBypass = screenTexts.any { text ->
                        bypassKeywords.any { keyword -> text.contains(keyword, ignoreCase = true) }
                    }
                    if (containsBypass) {
                        Log.d("FocusService", "Strict Mode: Bouncing back from settings bypass screen in $packageName")
                        bounceBackFromSettingsBypass(packageName)
                        return
                    }
                    // Anything else (WiFi, Bluetooth, Display, etc.) - no action, the screen
                    // just stays open and usable.
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

                // 3. Check for App Lock (PIN-gated apps) - only relevant for an app that
                // wasn't already handled by a hard block above, and only if it hasn't
                // already had its PIN entered for this foreground visit.
                if (lockedPackages.contains(packageName) && unlockedPackage != packageName) {
                    Log.d("FocusService", "Prompting for PIN - locked app opened: $packageName")
                    triggerAppLockPrompt(packageName)
                    return
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
                    // Clear tracking state up front rather than waiting for a future
                    // WINDOW_STATE_CHANGED event to a different package to do it via
                    // stopQuotaTrackingAndPersist(). If GLOBAL_ACTION_HOME/BlockActivity
                    // ever fails to actually move this app out of the foreground, that
                    // event never comes - leaving quotaTrackingPackage stuck on this
                    // app, which made the quota heartbeat's "continue" skip re-checking
                    // it and silently give up re-triggering the block.
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

    /**
     * Everything above (startQuotaTracking / stopQuotaTrackingAndPersist) is driven by
     * TYPE_WINDOW_STATE_CHANGED events - it only knows to (re)start tracking when the
     * foreground package visibly changes. That assumption breaks for apps like games,
     * which render through a single game-engine surface and generate few or no
     * accessibility events for the entire time they're being played. If the service
     * process also gets killed and restarted mid-game (very likely on a 2GB Go-edition
     * device with a heavy game in foreground competing for RAM), there is no future
     * event to ever re-trigger tracking - it silently never resumes for the rest of
     * that play session.
     *
     * This heartbeat polls the actual foreground package directly every few seconds,
     * independent of any event firing, and resumes tracking (or blocks immediately if
     * already over quota) whenever it finds a quota-enabled app sitting untracked in
     * the foreground. Started once for the service's lifetime from onServiceConnected().
     */
    private fun startQuotaHeartbeat() {
        quotaHeartbeatJob?.cancel()
        quotaHeartbeatJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3_000L)
                try {
                    val fgPackage = rootInActiveWindow?.packageName?.toString() ?: continue
                    if (quotaTrackingPackage == fgPackage) continue

                    val now = System.currentTimeMillis()
                    val activeBlock = longTermBlocks.firstOrNull {
                        it.type == "APP" && it.target == fgPackage && it.isActive &&
                            now >= it.startDate && now <= it.endDate && it.dailyLimitSeconds != null
                    } ?: continue

                    val today = currentEpochDay()
                    val usedMillis = if (activeBlock.lastUsageResetEpochDay != today) 0L else activeBlock.usedMillisToday
                    val limitMillis = (activeBlock.dailyLimitSeconds ?: continue) * 1000L

                    if (usedMillis >= limitMillis) {
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
                    Log.e("FocusService", "Error in quota heartbeat", e)
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
    private suspend fun persistQuotaProgress(blockId: Int, startMs: Long): Boolean = quotaPersistMutex.withLock {
        val elapsedMillis = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)

        val repository = (application as FocusApplication).repository
        val block = repository.getLongTermBlockById(blockId) ?: return@withLock false
        val limit = block.dailyLimitSeconds ?: return@withLock false
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

        newMillis >= limit * 1000L
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

    /**
     * Sends the device Home before the block screen appears, so the blocked app is
     * pushed out of the foreground first instead of sitting behind the overlay/
     * BlockActivity. Third-party accessibility services can't force-stop another
     * app's process (that needs a system-level permission we don't have), but
     * GLOBAL_ACTION_HOME reliably backgrounds it immediately and is what every
     * major app-blocker relies on for this "close it first" behavior. If dispatch
     * ever fails (e.g. an unusual OEM skin), we still fall through to showing the
     * overlay/BlockActivity as before instead of silently doing nothing.
     */
    private fun closeBlockedAppThenBlock(packageName: String) {
        if (Settings.canDrawOverlays(this)) {
            showOverlay(packageName)
        } else {
            triggerOverlayPermissionRequest()
        }
        // Note: we deliberately do NOT dispatch GLOBAL_ACTION_HOME here anymore.
        // BlockActivity is launched right after this with FLAG_ACTIVITY_NEW_TASK |
        // FLAG_ACTIVITY_CLEAR_TASK, which already replaces the blocked app in its
        // task. Firing GLOBAL_ACTION_HOME as well was a race: the system sometimes
        // processed the Home action after BlockActivity had already come to the
        // foreground, which sent BlockActivity itself back to the launcher a moment
        // after it appeared - the block screen would flash and immediately vanish.
    }

    /**
     * Silent, lightweight response used for every Strict-Mode Settings bypass detection
     * (our own app's accessibility/app-info/device-admin toggle, or a generic bypass
     * screen like Accessibility's list, Uninstall, Force stop, etc.). No overlay, no
     * full-screen BlockActivity, no GLOBAL_ACTION_HOME - Settings stays open and visibly
     * usable the whole session; this just dispatches GLOBAL_ACTION_BACK to pop the
     * offending screen off Settings' own back stack; the user lands back on the previous
     * screen and stays inside Settings.
     *
     * Dispatches BACK twice, not once: a single back only pops the offending
     * toggle screen and lands on its immediate parent (e.g. the Accessibility
     * services list) - one tap away from walking straight back into it. A
     * second back, fired a beat later once the first has actually been
     * processed, clears that parent screen too and drops the user out to the
     * Settings root, so a repeat attempt has to re-navigate the whole path
     * again instead of just tapping back in.
     */
    // Guards bounceBackFromSettingsBypass against re-firing while a bounce it just
    // triggered is still in flight. Settings' content-changed events are
    // deliberately left un-throttled (see the throttle comment above) so blocking
    // reacts instantly - but that also means a bypass screen that stays visible for
    // even 2-3 rapid content-changed events (a list re-render, a ripple/focus
    // animation, anything) would call this repeatedly before the first double-back
    // has actually navigated away, stacking up extra GLOBAL_ACTION_BACK calls and
    // shoving the user further back than intended - sometimes out of Settings
    // entirely. One bounce (its own two backs, ~60ms apart) is enough; ignore any
    // further trigger for a short cooldown after starting one.
    private var lastBounceBackTime = 0L
    private val BOUNCE_BACK_COOLDOWN_MS = 800L

    private fun bounceBackFromSettingsBypass(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastBounceBackTime < BOUNCE_BACK_COOLDOWN_MS) return
        lastBounceBackTime = now

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

        // Second back, slightly delayed so it lands after the first one has
        // actually navigated - firing both in the same instant can race the
        // first transition and get silently dropped by the system.
        serviceScope.launch {
            delay(60)
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

    /**
     * Shows the PIN-entry screen over a locked app. Unlike triggerBlockActivity(),
     * this deliberately does NOT use FLAG_ACTIVITY_CLEAR_TASK: the locked app's own
     * task is left completely intact underneath, so once the correct PIN is entered
     * AppLockUnlockActivity just finishes and the locked app reappears exactly as the
     * user left it, instead of being relaunched from scratch.
     */
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

    /** Called by AppLockUnlockActivity once the correct PIN has been entered. */
    fun grantAppUnlock(packageName: String) {
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
        // No synchronous flush here anymore - a previous version used runBlocking to
        // force a final quota-progress write, but that risked briefly blocking the
        // main thread during teardown for marginal benefit. The quota heartbeat now
        // self-heals within ~3 seconds of any restart regardless of cause, and the
        // ticker's 2-second interval already caps normal-teardown loss on its own.
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
        closeBlockedAppThenBlock(packageName)

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
        // Runs synchronously (no Handler.post) - we're already on the main thread
        // here (onAccessibilityEvent always dispatches on it), and posting only
        // pushed the overlay's actual appearance later in the message queue,
        // behind whatever else was pending - which is what made it show up late.
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

                // Safety net: normally BlockActivity.onResume() calls
                // dismissInstantOverlay() within a fraction of a second. If that
                // never happens (e.g. BlockActivity failed to launch), force-remove
                // the overlay after a short delay instead of leaving it stuck over
                // whatever the user is doing.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (overlayView == textView) {
                        Log.d("FocusService", "Overlay safety-net timeout fired for $packageName")
                        removeOverlay()
                    }
                }, OVERLAY_SAFETY_TIMEOUT_MS)
            } else {
                currentBlockedPackage = packageName
            }
        } catch (e: Exception) {
            Log.e("FocusService", "Error adding overlay view", e)
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
