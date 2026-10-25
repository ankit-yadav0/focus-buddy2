package com.example.service

/**
 * Pure matching rules for the Strict Mode power-menu guard (no Android dependencies, so they can be
 * unit-tested on the JVM). Deliberately narrow: generic words such as "Turn off", "Restart" or
 * "बंद करें" also appear in notification actions and ordinary dialogs, and matching them would
 * make the notification shade close itself.
 */
object PowerMenuMatcher {
    /** Substrings of the (lower-cased) window class name of a power / shutdown dialog. */
    private val CLASS_HINTS = listOf("globalactions", "powermenu", "poweroff", "shutdown", "reboot")

    /** Exact, case-insensitive node text / content description of the power menu's own button. */
    val LABELS: List<String> = listOf(
        "power off", "slide to power off", "shut down", "shutdown",
        "पावर ऑफ", "पावर बंद करें"
    )

    /** Only windows owned by these packages can be the system power menu. */
    fun isPackageCandidate(pkg: String): Boolean {
        if (pkg == "com.android.systemui" || pkg == "android") return true
        val l = pkg.lowercase()
        return l.contains("shutdown") || l.contains("globalaction") || l.contains("poweroff")
    }

    fun isPowerMenuClass(className: String?): Boolean {
        val c = className?.lowercase() ?: return false
        return c.isNotEmpty() && CLASS_HINTS.any { c.contains(it) }
    }

    fun isPowerMenuLabel(text: String?): Boolean {
        val t = text?.trim()?.lowercase() ?: return false
        return t in LABELS
    }
}
