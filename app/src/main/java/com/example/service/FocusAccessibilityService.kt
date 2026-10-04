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

        // Minimum spacing between processed TYPE_WINDOW_CONTENT_CHANGED events. These events
        // fire very rapidly (every keystroke, scroll, animation frame) and re-walking the node
        // tree on every single one is a major source of CPU/GC pressure on low-RAM Go devices,
        // which in turn makes the process a more likely low-memory-killer target. Window state
        // changes (app/tab switches) are never throttled so blocking still reacts instantly.
        private const val CONTENT_CHANGED_THROTTLE_MS = 300L

        // Safety-net cap on how long the instant-block overlay can stay up if
        // BlockActivity never calls dismissInstantOverlay() (e.g. it fails to launch).
        private const val OVERLAY_SAFETY_TIMEOUT_MS = 4_000L

        // Colour of the text-less backdrop shown while BlockActivity / the PIN screen
        // loads (same as BlockActivity's top gradient colour). It exists only to hide
        // the blocked app's content for those few frames. Set to false to disable.
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
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var isSessionActive = false
    private var sessionEndTime: Long = 0
    private var isSessionStrict = false
    private var blockedPackages = setOf<String>()
    private var restrictSettingsFullyEnabled = false
    private var restrictUninstallEnabled = false
    private var longTermBlocks = listOf<LongTermBlock>()
    private var activeWebsitesList = listOf<WebsiteBlock>()
    private var lastContentChangedProcessTime = 0L

    // App Lock (PIN-gated apps): packages the user chose to lock, and which one (if
    // any) has already had its PIN entered for the current foreground visit. The
    // grant is cleared the moment the foreground package changes to anything else,
    // so re-opening a locked app always asks for the PIN again.
    private var lockedPackages = setOf<String>()
    private var unlockedPackage: String? = null
    // When the user went to some OTHER app while a locked app was unlocked (0 = they are still in it).
    private var unlockedLeftAtMs = 0L

    // Daily time-quota tracking: while a quota-mode long-term-blocked app is in the
    // foreground, we track how long it's been open and periodically add that to its
    // persisted usedSecondsToday, so quota enforcement works both across app
    // launches (checked at window-state-change) and mid-session (via the ticker).
    private var quotaTrackingBlockId: Int? = null
    private var quotaTrackingPackage: String? = null
    private var quotaTrackingStartMs: Long = 0L
    private var quotaTickerJob: kotlinx.coroutines.Job? = null
    private var quotaHeartbeatJob: kotlinx.coroutines.Job? = null

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

    // Per quota block: the moment up to which foreground time has already been counted. Only read /
    // written while holding quotaPersistMutex, it guarantees a window shared by two overlapping persist
    // calls (ticker vs. app switch) is added to the usage exactly once.
    private val quotaAccountedUntil = HashMap<Int, Long>()

    // Drops App Lock's unlock grant (and stops quota tracking) when the screen turns off.
    private var screenOffReceiverRegistered = false
    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                unlockedPackage = null
                unlockedLeftAtMs = 0L
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

    // Debounce state for the Strict-Mode Settings instant gate. On a 2GB Go
    // device the very first node-tree read after the gate fires can catch the
    // destination screen (e.g. the accessibility-service toggle) mid-render,
    // before its title/description text has painted - that used to read as
    // "no bypass keyword found" and release the overlay while the real toggle
    // was still a frame or two from being fully there, which is exactly the
    // window a fast, repeated tap could land in. We now require two
    // consecutive harmless reads AND a minimum elapsed time since the gate
    // fired before trusting a "harmless" verdict enough to release it.
    private var settingsHarmlessStreak = 0
    private var settingsGateShownAtMs = 0L
    private val SETTINGS_RELEASE_MIN_ELAPSED_MS = 200L
    private val SETTINGS_RELEASE_MIN_STREAK = 2

    // GLOBAL_ACTION_BACK from bounceBackFromSettingsBypass() itself generates new
    // accessibility events as the screen transitions - without this debounce those
    // events could re-enter the same classification logic before the back navigation
    // has actually settled, and re-trigger another bounce (a loop). 1.2s is comfortably
    // longer than any real back-navigation transition on this device class.
    private var lastSettingsBounceAtMs = 0L
    private val SETTINGS_BOUNCE_DEBOUNCE_MS = 450L
    private var lastDeviceWideBlockAtMs = 0L
    private val DEVICE_WIDE_BLOCK_DEBOUNCE_MS = 1500L
    // Event-independent watcher: after every Settings screen change we poll the
    // screen every SETTINGS_WATCH_INTERVAL_MS for SETTINGS_WATCH_WINDOW_MS, so a
    // protected page is caught the moment it is readable instead of whenever the
    // next accessibility event happens to arrive.
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

        if (!screenOffReceiverRegistered) {
            try {
                // ACTION_SCREEN_OFF is a protected system broadcast, so no exported/not-exported flag is needed.
                registerReceiver(screenOffReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF))
                screenOffReceiverRegistered = true
            } catch (e: Exception) {
                Log.e("FocusService", "Could not register screen-off receiver", e)
            }
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
        if (EXTRA_TRANSIENT_PACKAGES.contains(packageName)) return true
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

    private fun isHomeLauncherPackage(packageName: String): Boolean {
        return try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(i, 0).any { it.activityInfo.packageName == packageName }
        } catch (e: Exception) { false }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && 
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        val now = TrustedClock.now()
        val isStrictModeActive = isSessionActive && now < sessionEndTime && isSessionStrict

        // Strict-Mode Settings gate: an invisible, text-less touch guard goes up the
        // moment a Settings window opens, so the accessibility/device-admin toggle can't
        // be tapped during the node-tree read below. Released once the screen is
        // classified harmless (see the LIST/SAFE branch further down).
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
            val minGap = if (packageName == "com.android.settings") SETTINGS_CONTENT_THROTTLE_MS else CONTENT_CHANGED_THROTTLE_MS
            if (now - lastContentChangedProcessTime < minGap) return
            lastContentChangedProcessTime = now
        }

        // If a quota-tracked app is no longer in the foreground, stop the ticker and
        // persist however much time was actually spent in it this session.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            quotaTrackingPackage != null && quotaTrackingPackage != packageName
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
            packageName != applicationContext.packageName &&
            !isTransientSystemPackage(packageName) &&
            (isHomeLauncherPackage(packageName) || lockedPackages.contains(packageName) || blockedPackages.contains(packageName))
        ) {
            unlockedPackage = null
        }

        // Grace window. Leaving an unlocked app through some OTHER app (gesture-switching via Recents,
        // never touching the launcher) used to keep the unlock alive forever. Now the unlock only
        // survives a short detour: come back within UNLOCK_GRACE_MS and it is still open, stay away
        // longer and the PIN is asked again. (Screen-off revokes it too - see screenOffReceiver.)
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

        // Fetch the window's node tree exactly ONCE per event and reuse it for every check below.
        // Walking a tree is expensive on a 2GB device, so it is only done where the result is used:
        //  - browsers: only the URL bar is needed (found by view id) - the page itself is never read
        //  - Settings / package installer: Strict Mode Settings gate + Uninstall Friction Guard
        //  - YouTube / Instagram / Snapchat: content-level blocking, which exists only in Strict Mode
        //    and only when that app's toggle is on
        val isBrowser = BROWSER_PACKAGES.contains(packageName)
        val readsScreenText = SYSTEM_GUARD_PACKAGES.contains(packageName) ||
            (isStrictModeActive && isContentBlockEnabled(packageName))
        val needsTree = isBrowser || readsScreenText
        val rootNode = if (needsTree) rootInActiveWindow else null
        val usingFallbackSource = needsTree && rootNode == null
        val nodeToUse = if (needsTree) (rootNode ?: event.source) else null

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
                // "App Uninstallation" restriction (Strict Mode wizard, step 2): while ON, the system
                // uninstall confirmation for ANY app is blocked. Text-scoped on purpose - the installer
                // package itself is not blocked, so installing an update still works.
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

                // NOTE: installing/uninstalling via com.android.packageinstaller used to be
                // blocked outright here, which also blocked installing a brand new APK (e.g.
                // this app's own update) any time Strict Mode was on. Uninstall protection for
                // OUR app now lives entirely in the always-on Uninstall Friction Guard below,
                // which checks screen text instead of blocking the installer package wholesale.

                // Selective Settings Rules - blocks/bounces screens that could be used to
                // defeat enforcement (uninstalling, disabling the accessibility service or
                // device admin, revoking the overlay permission), never a full Settings
                // block, so WiFi/Bluetooth/mobile data/display/sound etc. always stay
                // reachable. Uses SettingsScreenClassifier to tell an actual per-app detail
                // screen apart from a LIST screen that merely mentions our app's name as
                // one row among many (e.g. the Accessibility services list) - see that
                // file for why a flat "does the text appear anywhere" check isn't enough.
                if (packageName == "com.android.settings") {
                    if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) startSettingsWatch()
                    if (handleSettingsScreen(nodeToUse, packageName, appName, now)) return
                }
            }

            // Always-on Uninstall Friction Guard for Focuss Buddy (independent of Strict Mode
            // session). Redirects into the app's real 600-word uninstall flow instead of a
            // dead-end block screen, the moment Settings shows Focuss Buddy's own App Info
            // screen with an "Uninstall" action visible, or the system package installer's
            // uninstall confirmation appears directly.
            run {
                // Must be OUR app's uninstall dialog: the app name AND "uninstall" both on screen.
                // (An OR here also hijacked every other app's uninstall dialog and this app's own
                // update-install screen, which shows the app name too.)
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

                // Check for App Lock (PIN-gated apps) - only relevant for an app that
                // wasn't already handled by a hard block above, and only if it hasn't
                // already had its PIN entered for this foreground visit.
                if (lockedPackages.contains(packageName) && unlockedPackage != packageName) {
                    Log.d("FocusService", "Prompting for PIN - locked app opened: $packageName")
                    triggerAppLockPrompt(packageName)
                    return
                }
            }

            // 3. Check for active Long-Term Website Blocks (Inspect browser URL/node contents)
            val browserPackages = BROWSER_PACKAGES
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
        } catch (e: Exception) {
            // Never let a malformed node tree or a transient IPC failure take the whole service down.
            Log.e("FocusService", "Error handling accessibility event", e)
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

    /** True when the Strict Mode content-level toggle for this app (Shorts / Reels / Spotlight) is on. */
    private fun isContentBlockEnabled(packageName: String): Boolean {
        val prefs = getSharedPreferences("focuss_buddy_settings", MODE_PRIVATE)
        return when (packageName) {
            "com.google.android.youtube" -> prefs.getBoolean("youtube_block_shorts", false)
            "com.instagram.android" -> prefs.getBoolean("instagram_block_reels", false)
            "com.snapchat.android" -> prefs.getBoolean("snapchat_block_spotlight", false)
            else -> false
        }
    }

    /**
     * Current epoch day (days since 1970-01-01 in the device's LOCAL timezone) - used to detect
     * the midnight rollover so usedSecondsToday resets automatically.
     */
    private fun currentEpochDay(): Long = TimeUtils.localEpochDay(TrustedClock.now())

    /**
     * Begins tracking foreground time for a quota-mode long-term-blocked app. Starts a
     * ticker that periodically persists elapsed time and checks whether the daily
     * limit has now been crossed mid-session (not just at the next app launch).
     */
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
     * independent of any event firing. It resumes quota tracking (or blocks immediately if
     * already over quota) for a quota-enabled app sitting untracked in the foreground, and it
     * also blocks an app that is ALREADY open when a focus session / long-term block starts
     * (app blocks are otherwise only evaluated when an app is opened). It additionally closes
     * a focus session whose time has run out. Started once from onServiceConnected().
     */
    private fun startQuotaHeartbeat() {
        quotaHeartbeatJob?.cancel()
        quotaHeartbeatJob = serviceScope.launch {
            while (true) {
                delay(3_000L)
                try {
                    val now = TrustedClock.now()

                    // A focus session whose time is up is closed here too, so it ends on time even if
                    // no accessibility event happens to arrive afterwards.
                    if (isSessionActive && now >= sessionEndTime) {
                        (application as FocusApplication).repository.stopActiveSession("Expired")
                    }

                    val sessionBlocking = isSessionActive && now < sessionEndTime && blockedPackages.isNotEmpty()
                    val hasAppBlocks = longTermBlocks.any { it.type == "APP" }
                    if (!sessionBlocking && !hasAppBlocks) continue

                    val root = rootInActiveWindow ?: continue
                    val fgPackage = root.packageName?.toString()
                    @Suppress("DEPRECATION")
                    root.recycle()
                    if (fgPackage == null || fgPackage == applicationContext.packageName) continue
                    // A block was just fired and its screen is still loading - don't stack another one.
                    if (now - lastBlockTriggerAtMs < 2_500L) continue

                    // 1. A focus session started (or the block list changed) while the blocked app was
                    //    already open - app blocks are otherwise only evaluated when an app is opened.
                    if (sessionBlocking && blockedPackages.contains(fgPackage)) {
                        Log.d("FocusService", "Heartbeat: blocked app already in foreground: $fgPackage")
                        triggerBlockActivity(fgPackage, isLongTerm = false, reason = "", endDate = sessionEndTime)
                        continue
                    }

                    // 2. Long-term app blocks: a block that became active while its app was open, or a
                    //    quota app that is sitting untracked in the foreground.
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
        val windowEnd = TrustedClock.now()
        return quotaPersistMutex.withLock {
            // Count every moment exactly once: a concurrent persist (ticker vs. app switch) may
            // already have accounted for the start of this window. startMs is snapshotted by the
            // caller BEFORE it waits for this lock, so the overlap can only be resolved here, inside
            // it, via the per-block "accounted until" mark.
            val accountedUntil = quotaAccountedUntil[blockId] ?: 0L
            val elapsedMillis = (windowEnd - maxOf(startMs, accountedUntil)).coerceAtLeast(0L)
            quotaAccountedUntil[blockId] = maxOf(windowEnd, accountedUntil)

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
                quotaTrackingStartMs = windowEnd
            }

            newMillis >= limit * 1000L
        }
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
        // No forced HOME. A text-less backdrop hides the app until BlockActivity is up.
        if (Settings.canDrawOverlays(this)) {
            showOverlay(packageName)
        } else {
            triggerOverlayPermissionRequest()
        }
    }

    /**
     * Classifies the current Settings screen and acts on it. Returns true when a
     * bounce/block was fired (caller should stop processing this event).
     */
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
                if (root.packageName?.toString() != "com.android.settings") break
                handleSettingsScreen(root, "com.android.settings", appName, TrustedClock.now())
            }
        }
    }

    /**
     * Silent, lightweight response used ONLY when the user has navigated straight to
     * Focuss Buddy's own accessibility/app-info/device-admin toggle screen (i.e. is
     * literally looking at the toggle that would disable enforcement). Unlike
     * triggerBlockActivity(), this does NOT launch the full-screen BlockActivity and
     * does NOT dispatch GLOBAL_ACTION_HOME - it just dispatches GLOBAL_ACTION_BACK to
     * pop that one screen off Settings' back stack, so the user lands back on the
     * previous screen (e.g. the Accessibility services list) and stays inside
     * Settings, instead of being kicked out with a full "App Blocked" screen.
     *
     * The overlay drawn by the instant Settings gate is removed right away rather
     * than left up: GLOBAL_ACTION_BACK is a single fast system call (no Activity
     * launch, no WindowManager churn), so by the time this runs the offending screen
     * is already being torn down - there's no meaningful window left for the overlay
     * to protect. Generic bypass actions (Reset, Uninstall, Force stop, etc. - see
     * bypassKeywords below) intentionally keep going through triggerBlockActivity()
     * instead: those are more consequential and still get the full friction screen.
     *
     * Dispatches BACK twice, not once: a single back only pops the offending
     * toggle screen and lands on its immediate parent (e.g. the Accessibility
     * services list) - one tap away from walking straight back into it. A
     * second back, fired a beat later once the first has actually been
     * processed, clears that parent screen too and drops the user out to the
     * Settings root, so a repeat attempt has to re-navigate the whole path
     * again instead of just tapping back in.
     */
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

        // Second back, slightly delayed so it lands after the first one has
        // actually navigated - firing both in the same instant can race the
        // first transition and get silently dropped by the system.
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
        lastBlockTriggerAtMs = TrustedClock.now()
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
     * Lightweight tree walk that only gathers visible text/content-descriptions. Only called
     * for the few windows whose text is actually used (see readsScreenText in
     * onAccessibilityEvent).
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

    /**
     * Text-less cover window. opaque=true: solid backdrop (hides the blocked app while
     * BlockActivity / the PIN screen loads). opaque=false: fully transparent but still
     * touch-consuming (Settings gate). Removed by BlockActivity/AppLockUnlockActivity
     * onResume, by the Settings classifier, or by the safety timeout.
     */
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
        // Detach the field right away; only the WindowManager call is posted. Previously the field was
        // cleared inside the posted runnable, so a new showOverlay() arriving in between saw a
        // non-null overlayView, skipped adding a fresh cover, and the pending removal then deleted it.
        val view = overlayView ?: return
        overlayView = null
        currentBlockedPackage = null
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                windowManager.removeView(view)
                Log.d("FocusService", "Removed overlay view")
            } catch (e: Exception) {
                Log.e("FocusService", "Failed to remove overlay view", e)
            }
        }
    }

    fun dismissInstantOverlay() {
        removeOverlay()
    }

    private fun triggerOverlayPermissionRequest() {
        val now = TrustedClock.now()
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
