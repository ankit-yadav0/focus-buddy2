package com.example.service

/**
 * Pure matching rules for the Strict Mode power-menu guard (no Android dependencies, so they can be
 * unit-tested on the JVM).
 *
 * Prevents opening the Power Off / Restart / Reboot menu across Stock Android, Pixel, Samsung OneUI,
 * Realme UI / ColorOS / OxygenOS, Xiaomi MIUI / HyperOS, Vivo / FuntouchOS, and Motorola while
 * avoiding false-positive closures of the normal notification shade.
 */
object PowerMenuMatcher {
    /** Substrings of the (lower-cased) window class name of a power / shutdown / restart dialog. */
    private val CLASS_HINTS = listOf(
        "globalactions",
        "powermenu",
        "poweroff",
        "powerdialog",
        "shutdown",
        "reboot",
        "restart"
    )

    /** Substrings of the (lower-cased) view ID resource name belonging to the system Power / Restart menu. */
    private val VIEW_ID_HINTS = listOf(
        "global_actions",
        "power_menu",
        "power_off",
        "shutdown",
        "reboot_view",
        "coui_global_actions",
        "oplus_global_actions",
        "miui_global_actions",
        "sec_global_actions"
    )

    /** Exact, case-insensitive node text / content description of a primary power-off button or slider. */
    val LABELS: List<String> = listOf(
        "power off",
        "slide to power off",
        "swipe to power off",
        "drag to power off",
        "tap to power off",
        "touch to power off",
        "slide down to power off",
        "shut down",
        "shutdown",
        "power off phone",
        "पावर ऑफ",
        "पावर ऑफ़",
        "पावर बंद करें",
        "फ़ोन बंद करें"
    )

    /** Exact, case-insensitive node text / content description of a Restart / Reboot button or slider. */
    val RESTART_LABELS: List<String> = listOf(
        "restart",
        "reboot",
        "slide to restart",
        "swipe to restart",
        "drag to restart",
        "tap to restart",
        "touch to restart",
        "slide up to restart",
        "restart phone",
        "reboot phone",
        "रीस्टार्ट",
        "रीस्टार्ट करें",
        "पुनः आरंभ करें",
        "रीबूट करें"
    )

    /** Companion labels that appear alongside Restart/Power off inside the Hardware Power Menu. */
    val COMPANION_LABELS: List<String> = listOf(
        "emergency",
        "emergency call",
        "emergency sos",
        "sos",
        "lockdown",
        "medical info",
        "आपातकालीन",
        "आपातकालीन कॉल"
    )

    /** Search queries used with findAccessibilityNodeInfosByText to probe for the power/restart menu. */
    val SEARCH_QUERIES: List<String> = listOf(
        "Power off",
        "Shut down",
        "Shutdown",
        "Restart",
        "Reboot",
        "पावर ऑफ",
        "पावर बंद करें",
        "रीस्टार्ट",
        "पुनः आरंभ करें"
    )

    /** Windows owned by these packages can host the system power / restart dialog. */
    fun isPackageCandidate(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        if (pkg == "com.android.systemui" || pkg == "android") return true
        val l = pkg.lowercase()
        return l.contains("systemui") ||
            l.contains("shutdown") ||
            l.contains("globalaction") ||
            l.contains("poweroff") ||
            l.contains("powermenu") ||
            l.contains("reboot")
    }

    fun isPowerMenuClass(className: String?): Boolean {
        val c = className?.lowercase() ?: return false
        return c.isNotEmpty() && CLASS_HINTS.any { c.contains(it) }
    }

    fun isPowerMenuViewId(viewIdResourceName: String?): Boolean {
        val id = viewIdResourceName?.lowercase() ?: return false
        return id.isNotEmpty() && VIEW_ID_HINTS.any { id.contains(it) }
    }

    /** True for standalone unmistakable power-off labels (e.g. "Power off", "Slide to power off"). */
    fun isPowerMenuLabel(text: String?): Boolean {
        val t = text?.trim()?.lowercase() ?: return false
        return t in LABELS
    }

    /** True for exact restart / reboot labels (e.g. "Restart", "Reboot", "Slide to restart"). */
    fun isRestartLabel(text: String?): Boolean {
        val t = text?.trim()?.lowercase() ?: return false
        return t in RESTART_LABELS
    }

    /** True for companion labels on the power dialog (e.g. "Emergency", "Emergency call", "Lockdown"). */
    fun isCompanionLabel(text: String?): Boolean {
        val t = text?.trim()?.lowercase() ?: return false
        return t in COMPANION_LABELS
    }

    /**
     * Evaluates all visible texts/descriptions and view IDs from a candidate window to decide
     * whether the Hardware Power Off / Restart menu is on screen.
     *
     * Rules:
     * 1. Any explicit power-menu view ID (`global_actions`, `power_menu`, etc.) -> true.
     * 2. Any unmistakable power-off label (`Power off`, `Slide to power off`, `Shut down`, etc.) -> true.
     * 3. Any explicit slider restart phrase (`Slide to restart`, `Swipe to restart`, etc.) -> true.
     * 4. An exact `Restart` / `Reboot` button paired with a companion power-menu action (`Emergency`,
     *    `Emergency call`, `Emergency SOS`, `Lockdown`, etc.) or inside a small power dialog (<= 10 visible
     *    text nodes, which excludes the notification shade) -> true.
     */
    fun matchesPowerMenuScreen(
        visibleTexts: Collection<String>,
        visibleViewIds: Collection<String> = emptyList()
    ): Boolean {
        if (visibleViewIds.any { isPowerMenuViewId(it) }) return true

        var hasPrimaryPowerOff = false
        var hasRestart = false
        var hasExplicitRestartPhrase = false
        var hasCompanion = false
        var nonEmptyCount = 0

        for (raw in visibleTexts) {
            val clean = raw.trim().lowercase()
            if (clean.isEmpty()) continue
            nonEmptyCount++
            if (clean in LABELS) {
                hasPrimaryPowerOff = true
            }
            if (clean in RESTART_LABELS) {
                hasRestart = true
                if (clean != "restart" && clean != "reboot" && clean != "रीस्टार्ट") {
                    hasExplicitRestartPhrase = true
                }
            }
            if (clean in COMPANION_LABELS) {
                hasCompanion = true
            }
        }

        if (hasPrimaryPowerOff || hasExplicitRestartPhrase) return true
        if (hasRestart && (hasCompanion || nonEmptyCount in 1..8)) return true
        return false
    }
}
