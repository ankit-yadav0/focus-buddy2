package com.example.service

/**
 * Pure rules for "Banking Pause": while a UPI / banking app is open, Focuss Buddy switches its own
 * accessibility service off (banking apps refuse to run next to a user-installed accessibility
 * service) and switches it back on when the app is left. No Android dependencies, so the rules are
 * unit-tested on the JVM.
 */
object BankingPausePolicy {
    /** Hard cap on one pause. A forgotten / stuck pause can never leave protection off longer. */
    const val MAX_PAUSE_MS = 10 * 60_000L
    /** Screen off this long while paused -> nobody is using the phone, resume. */
    const val SCREEN_OFF_RESUME_MS = 60_000L
    /** Leaving to a system window (permission dialog, SIM picker...) must last this long to count. */
    const val TRANSIENT_GRACE_MS = 4_000L
    /** Leaving to a normal app must be seen for this long (about two polls) to count. */
    const val LEAVE_CONFIRM_MS = 600L
    /** The recovery alarm fires this long after the cap, in case the pause service itself died. */
    const val DEADMAN_MARGIN_MS = 15_000L

    /** Best-effort package names of common UPI / banking apps. Only used to float them to the top of the picker. */
    val SUGGESTED_PACKAGES: Set<String> = setOf(
        "com.naviapp", "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm",
        "in.org.npci.upiapp", "com.dreamplug.androidapp", "com.mobikwik_new", "com.sbi.lotusintouch",
        "com.snapwork.hdfc", "com.enstage.wibmo.hdfc", "com.csam.icici.bank.imobile", "com.axis.mobile",
        "com.msf.kbank.mobile"
    )

    /** Browsers (website blocking reads them) - never pausable. */
    val BROWSERS: Set<String> = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
        "com.sec.android.app.sbrowser", "com.microsoft.emmx",
        "com.duckduckgo.mobile.android", "com.brave.browser",
        "com.heytap.browser", "com.coloros.browser", "com.android.browser",
        "com.vivaldi.browser", "com.kiwibrowser.browser", "com.UCMobile.intl"
    )

    /** Apps whose blocking is the whole point (content-level blocking) plus system UI. Never pausable. */
    private val NEVER_PAUSE: Set<String> = setOf(
        "com.google.android.youtube", "com.instagram.android", "com.snapchat.android",
        "com.android.settings", "com.android.systemui", "android",
        "com.android.packageinstaller", "com.google.android.packageinstaller"
    )

    /** System windows that appear over a banking app without the user really leaving it. */
    private val TRANSIENT: Set<String> = setOf(
        "com.android.systemui", "android",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.google.android.gms", "com.android.phone", "com.android.server.telecom"
    )

    fun isTransient(pkg: String): Boolean = pkg in TRANSIENT

    /**
     * May [pkg] pause protection? Not if it is one Focuss Buddy is blocking right now (otherwise
     * "banking app" would be a loophole), not a browser, not a content-blocked or system app.
     */
    fun isEligible(pkg: String, blockedPackages: Set<String>, longTermTargets: Set<String>): Boolean {
        if (pkg.isBlank()) return false
        if (pkg in NEVER_PAUSE || pkg in BROWSERS) return false
        if (pkg in blockedPackages || pkg in longTermTargets) return false
        return true
    }

    fun parsePackages(csv: String?): Set<String> =
        csv.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun serializePackages(packages: Set<String>): String = packages.sorted().joinToString(",")

    enum class Reason { LEFT_BANKING_APP, TIMEOUT, SCREEN_OFF, NO_USAGE_ACCESS }

    data class State(val nonBankingSinceMs: Long = 0L, val screenOffSinceMs: Long = 0L)

    data class Decision(val resume: Reason?, val next: State)

    /**
     * One poll of the pause loop. [nowMs]/[startedMs] are SystemClock.elapsedRealtime() values
     * (monotonic: changing the wall clock cannot stretch a pause). [resume] == null means "stay paused".
     */
    fun decide(
        currentPkg: String,
        banking: Set<String>,
        nowMs: Long,
        startedMs: Long,
        interactive: Boolean,
        usageAccess: Boolean,
        prev: State
    ): Decision {
        // nowMs < startedMs means a reboot reset the monotonic clock: the pause is stale.
        if (nowMs < startedMs || nowMs - startedMs >= MAX_PAUSE_MS) return Decision(Reason.TIMEOUT, prev)
        // Without usage access we can no longer see when the banking app is left.
        if (!usageAccess) return Decision(Reason.NO_USAGE_ACCESS, prev)

        val offSince = if (interactive) 0L else (if (prev.screenOffSinceMs == 0L) nowMs else prev.screenOffSinceMs)
        if (!interactive && nowMs - offSince >= SCREEN_OFF_RESUME_MS) {
            return Decision(Reason.SCREEN_OFF, State(prev.nonBankingSinceMs, offSince))
        }

        if (currentPkg in banking) return Decision(null, State(0L, offSince))

        val since = if (prev.nonBankingSinceMs == 0L) nowMs else prev.nonBankingSinceMs
        val need = if (isTransient(currentPkg)) TRANSIENT_GRACE_MS else LEAVE_CONFIRM_MS
        val next = State(since, offSince)
        return if (nowMs - since >= need) Decision(Reason.LEFT_BANKING_APP, next) else Decision(null, next)
    }
}
