package com.example.service

/**
 * Classifies the currently visible Android Settings screen as one of:
 *  - a LIST/index screen (Accessibility's "Installed services", the Device admin apps
 *    list, the system App list, Settings search results) where Focuss Buddy may appear
 *    as one row among several, but nothing can actually be disabled/uninstalled/cleared
 *    from that screen directly, or
 *  - the actual PROTECTED_APP_DETAIL screen for Focuss Buddy specifically (the
 *    accessibility-service toggle page, App Info, Storage, Notifications, Permissions,
 *    Battery/background-restriction, the Device Admin detail page) - or a follow-on
 *    confirmation dialog for one of those - where a bypass can actually happen, or
 *  - PROTECTED_DEVICE_WIDE for the class of screens that threaten enforcement without
 *    ever naming any app (factory reset, "Reset app preferences"), or
 *  - SAFE, meaning there is no reason to believe this screen can defeat enforcement.
 *
 * Deliberately window/class-agnostic: Settings' underlying Activity/Fragment class
 * names vary by OEM and Android version (stock AOSP, Samsung OneUI, Realme/ColorOS,
 * etc.), so nothing here depends on a specific class name. Classification is driven
 * only by the visible text and the number of checkable (switch/checkbox) controls on
 * screen - signals that stay stable across skins - and deliberately does NOT bounce
 * merely because "Focuss Buddy" appears as text somewhere on screen; that alone is
 * exactly the over-broad signal this classifier replaces.
 */
object SettingsScreenClassifier {

    enum class ScreenKind {
        SAFE,
        LIST,
        PROTECTED_APP_DETAIL,
        PROTECTED_DEVICE_WIDE,
    }

    data class Result(val kind: ScreenKind, val reason: String)

    // Both spellings genuinely appear in this app's own UI surfaces: R.string.app_name
    // is "Focuss Buddy", but the Device Admin receiver's manifest label is the plain
    // "Focus Buddy Device Admin" (no double 's'). Matching only one spelling previously
    // made the device-admin deactivation screen invisible to identity matching - a real
    // cause of "bounce sometimes doesn't fire".
    private val IDENTITY_STRINGS = listOf("Focuss Buddy", "Focus Buddy")

    // Text that only ever appears on a detail/config page for ONE specific app or
    // service - never on a screen listing many apps/services side by side.
    private val APP_DETAIL_KEYWORDS = listOf(
        "Use Focuss Buddy", "Use Focus Buddy",
        "Allow Focuss Buddy", "Allow Focus Buddy",
        "Deactivate this device admin app", "Activate this device admin app",
        "Uninstall", "Force stop", "Clear storage", "Clear data", "Clear cache",
        "Storage & cache", "App info", "App details", "Open by default",
        "Unrestricted", "Optimized (recommended)", "Restricted",
        "Background restriction", "Battery usage",
        "Notification categories", "Show notifications", "Allow notifications",
        "App permissions", "Permissions"
    )

    // Threatens the whole device regardless of app identity, so no app-identity match
    // is required before treating this as protected. "Reset app preferences" undoes
    // things like battery-optimization exemptions and re-enables disabled apps/services
    // device-wide - it doesn't need to name Focuss Buddy to defeat enforcement.
    private val DEVICE_WIDE_KEYWORDS = listOf(
        "Erase all data", "Factory data reset", "Erase all data (factory reset)",
        "Reset app preferences"
    )

    /**
     * @param screenTexts   Visible text/content-descriptions on the current screen, in
     *                      traversal order (as already collected for other checks).
     * @param checkableNodeCount Number of checkable (Switch/CheckBox-style) controls
     *                      currently in the node tree.
     */
    fun classify(screenTexts: List<String>, checkableNodeCount: Int): Result {
        if (screenTexts.any { text -> DEVICE_WIDE_KEYWORDS.any { text.contains(it, ignoreCase = true) } }) {
            return Result(ScreenKind.PROTECTED_DEVICE_WIDE, "device-wide destructive screen (factory reset / reset app preferences)")
        }

        val mentionsUs = screenTexts.any { text -> IDENTITY_STRINGS.any { text.contains(it, ignoreCase = true) } }
        if (!mentionsUs) {
            return Result(ScreenKind.SAFE, "no app-identity text and no device-wide destructive screen")
        }

        val detailHits = screenTexts.count { text -> APP_DETAIL_KEYWORDS.any { text.contains(it, ignoreCase = true) } }

        // A detail-only keyword is decisive on its own: Android never renders "Uninstall",
        // "Force stop", "Use Focuss Buddy", "Deactivate this device admin app", etc. as row
        // text on a screen that lists many apps/services side by side - only on that one
        // app's own detail/config page. A single checkable control alongside our identity
        // is the same kind of decisive signal for a one-app toggle page (the accessibility
        // service page, notification access, etc.) that has no other keyword to match.
        // Neither of these needs, or should be overruled by, the list-shape check below -
        // that check exists only for the leftover case where identity text is present but
        // nothing else tells us anything (previously, giving it veto power over a real
        // keyword match was itself a bug: a detail page's own description text - bullet
        // points like "View screen content", "Perform actions" - reads as several short
        // capitalized "sibling rows" and was wrongly vetoing the real Accessibility
        // service toggle page, letting the service be disabled with Strict Mode on).
        if (detailHits > 0) {
            return Result(ScreenKind.PROTECTED_APP_DETAIL, "app-identity + $detailHits detail-only keyword(s)")
        }
        if (checkableNodeCount == 1) {
            return Result(ScreenKind.PROTECTED_APP_DETAIL, "app-identity + a single toggle control")
        }

        // Neither signal fired - fall back to list-shape purely to decide between LIST and
        // SAFE for the ambiguous leftover case (e.g. our name in a Settings search
        // suggestion). A LIST screen shows several OTHER short, capitalized, name-like
        // rows alongside ours (other apps/services); we can't enumerate every app/service
        // name on the device, but we can recognise that shape.
        val siblingRowCount = screenTexts.count { raw ->
            val t = raw.trim()
            t.length in 2..40 &&
                t.first().isUpperCase() &&
                IDENTITY_STRINGS.none { t.contains(it, ignoreCase = true) } &&
                APP_DETAIL_KEYWORDS.none { t.contains(it, ignoreCase = true) }
        }
        return if (siblingRowCount >= 3) {
            Result(ScreenKind.LIST, "app-identity present but screen looks list-shaped ($siblingRowCount sibling rows), no detail signal")
        } else {
            Result(ScreenKind.SAFE, "app-identity present but inconclusive - failing safe")
        }
    }
}
