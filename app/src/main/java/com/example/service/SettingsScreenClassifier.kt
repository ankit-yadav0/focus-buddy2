package com.example.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Classifies what kind of Settings screen is currently on-screen, so Strict Mode can
 * bounce back from an actual bypass-capable detail page WITHOUT also bouncing from a
 * list screen that merely happens to mention our app's name as one row among many
 * (e.g. the Accessibility services list, or the Apps & notifications app list).
 *
 * The naive version of this check was `screenTexts.any { it.contains(appName) }` - a
 * flat "does this word appear anywhere on screen" test with no structural awareness.
 * That fires the instant a LIST screen containing our app's name renders, long before
 * the user has actually tapped into our app's row - so bounce-back kicked in one level
 * too early (at the list) instead of at the real target (the detail/toggle screen for
 * our app specifically).
 *
 * This classifier instead looks at the screen's *shape*: how many similarly-structured
 * clickable siblings exist under one parent (a list has many; a detail page doesn't),
 * how many checkable controls (Switch/CheckBox/ToggleButton) are present (a detail page
 * for one app typically has exactly one; an app list either has none of its own or many,
 * one per row), and whether any detail-only keyword is present (things like "Uninstall"
 * or "Force stop" only ever appear on a specific app's own detail page, never on a list
 * enumerating many apps).
 */
object SettingsScreenClassifier {

    enum class ScreenType {
        /** Nothing bypass-relevant on screen - leave it alone entirely. */
        SAFE,
        /** A list enumerating multiple apps/items (our app may be one row in it) - leave
         * it open; the user hasn't reached anything actionable yet. */
        LIST,
        /** The actual detail/config/toggle screen for OUR app specifically - bounce. */
        PROTECTED_APP_DETAIL,
        /** A device-wide bypass action (factory reset, clear all data, etc.) that's
         * dangerous regardless of which app screen it was reached through. */
        PROTECTED_DEVICE_WIDE
    }

    // Only ever appear on a genuine per-app detail/config page, never on a screen that
    // lists several apps side by side.
    private val DETAIL_ONLY_KEYWORDS = listOf(
        "Uninstall", "Force stop", "Storage & cache", "App info", "Open by default",
        "Deactivate this device admin app", "Activate this device admin app",
        "Battery", "Notifications", "Permissions", "Advanced"
    )

    // Dangerous regardless of which app's screen they appear on - checked before we
    // even look for our app's name, since these don't need it.
    private val DEFAULT_DEVICE_WIDE_KEYWORDS = listOf(
        "Reset", "Factory reset", "Erase all data",
        "Clear storage", "Clear data", "Clear cache"
    )

    private data class ScreenSignals(
        val hasAppIdentity: Boolean,
        val checkableCount: Int,
        val maxSiblingGroupSize: Int,
        val hasDetailKeyword: Boolean,
        val hasDeviceWideKeyword: Boolean
    )

    /**
     * @param extraDeviceWideKeywords additional device-wide keywords to also treat as
     * PROTECTED_DEVICE_WIDE (e.g. the Strict Mode wizard's opt-in "Phone Settings"
     * extras - Modify system settings, Usage access, Battery optimization).
     */
    fun classify(
        root: AccessibilityNodeInfo?,
        appName: String,
        extraDeviceWideKeywords: List<String> = emptyList()
    ): ScreenType {
        if (root == null) return ScreenType.SAFE
        val signals = collectSignals(root, appName, DEFAULT_DEVICE_WIDE_KEYWORDS + extraDeviceWideKeywords)

        if (signals.hasDeviceWideKeyword) return ScreenType.PROTECTED_DEVICE_WIDE
        if (!signals.hasAppIdentity) return ScreenType.SAFE

        // IMPORTANT - order matters here, and this order must never be reversed:
        // detail-keyword and single-toggle signals are checked BEFORE the list-shape
        // fallback below. A real detail page for our app (e.g. its Permissions screen)
        // can itself contain several rows and would otherwise get misread as a "list"
        // and vetoed. List-shape is a fallback for when NEITHER decisive signal fired,
        // never a veto over a signal that did.
        if (signals.hasDetailKeyword || signals.checkableCount == 1) {
            return ScreenType.PROTECTED_APP_DETAIL
        }

        // Only consulted as a last resort: many similarly-shaped clickable siblings
        // under one parent, or several checkable controls, means this is a list of
        // apps/items rather than one app's own detail page.
        if (signals.maxSiblingGroupSize >= 4 || signals.checkableCount >= 3) {
            return ScreenType.LIST
        }

        // Ambiguous: our app's name showed up but neither signal is decisive (e.g. a
        // layout this heuristic doesn't recognize). Default to SAFE rather than
        // bouncing - an unrecognized shape should fail open, not trigger a false bounce.
        return ScreenType.SAFE
    }

    private fun collectSignals(
        root: AccessibilityNodeInfo,
        appName: String,
        deviceWideKeywords: List<String>
    ): ScreenSignals {
        var hasAppIdentity = false
        var checkableCount = 0
        var maxSiblingGroupSize = 0
        var hasDetailKeyword = false
        var hasDeviceWideKeyword = false

        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 50) return

            listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).forEach { t ->
                if (t.contains(appName, ignoreCase = true)) hasAppIdentity = true
                if (DETAIL_ONLY_KEYWORDS.any { t.contains(it, ignoreCase = true) }) hasDetailKeyword = true
                if (deviceWideKeywords.any { t.contains(it, ignoreCase = true) }) hasDeviceWideKeyword = true
            }
            if (node.isCheckable) checkableCount++

            val childCount = node.childCount
            if (childCount > 1) {
                // Group this node's direct clickable children by class name - several
                // children sharing a class under one parent is the structural signature
                // of a repeated list row (icon + label + maybe a switch, repeated).
                val groups = HashMap<String, Int>()
                for (i in 0 until childCount) {
                    val child = node.getChild(i) ?: continue
                    if (child.isClickable) {
                        val className = child.className?.toString() ?: "?"
                        groups[className] = (groups[className] ?: 0) + 1
                    }
                    visit(child, depth + 1)
                    child.recycle()
                }
                val localMax = groups.values.maxOrNull() ?: 0
                if (localMax > maxSiblingGroupSize) maxSiblingGroupSize = localMax
            } else {
                for (i in 0 until childCount) {
                    val child = node.getChild(i) ?: continue
                    visit(child, depth + 1)
                    child.recycle()
                }
            }
        }

        visit(root, 0)
        return ScreenSignals(hasAppIdentity, checkableCount, maxSiblingGroupSize, hasDetailKeyword, hasDeviceWideKeyword)
    }
}
