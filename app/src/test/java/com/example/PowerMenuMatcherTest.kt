package com.example

import com.example.service.PowerMenuMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Power-menu guard rules: must catch Power Off & Restart menus, must NOT fire on shade/notification text. */
class PowerMenuMatcherTest {
    @Test fun detectsPowerMenuWindowClasses() {
        assertTrue(PowerMenuMatcher.isPowerMenuClass("com.android.systemui.globalactions.GlobalActionsDialogLite"))
        assertTrue(PowerMenuMatcher.isPowerMenuClass("com.android.internal.app.ShutdownActivity"))
        assertTrue(PowerMenuMatcher.isPowerMenuClass("com.oplus.globalactions.PowerMenuActivity"))
        assertTrue(PowerMenuMatcher.isPowerMenuClass("com.android.internal.app.RebootConfirmDialog"))
    }

    @Test fun ignoresOrdinaryWindowClasses() {
        assertFalse(PowerMenuMatcher.isPowerMenuClass(null))
        assertFalse(PowerMenuMatcher.isPowerMenuClass(""))
        assertFalse(PowerMenuMatcher.isPowerMenuClass("android.widget.FrameLayout"))
        assertFalse(PowerMenuMatcher.isPowerMenuClass("com.android.systemui.statusbar.phone.StatusBarWindowView"))
    }

    @Test fun detectsPowerMenuViewIds() {
        assertTrue(PowerMenuMatcher.isPowerMenuViewId("com.android.systemui:id/global_actions_view"))
        assertTrue(PowerMenuMatcher.isPowerMenuViewId("com.android.systemui:id/power_menu_container"))
        assertFalse(PowerMenuMatcher.isPowerMenuViewId("com.android.systemui:id/notification_stack_scroller"))
    }

    @Test fun matchesOnlyTheExactPowerOffLabel() {
        assertTrue(PowerMenuMatcher.isPowerMenuLabel("Power off"))
        assertTrue(PowerMenuMatcher.isPowerMenuLabel("  POWER OFF "))
        assertTrue(PowerMenuMatcher.isPowerMenuLabel("Slide to power off"))
    }

    @Test fun genericWordsDoNotMatchSingleLabelCheck() {
        // These appear in notification actions / normal dialogs; matching them alone as a single label without context is unsafe.
        assertFalse(PowerMenuMatcher.isPowerMenuLabel("Turn off"))
        assertFalse(PowerMenuMatcher.isPowerMenuLabel("Restart"))
        assertFalse(PowerMenuMatcher.isPowerMenuLabel("बंद करें"))
        assertFalse(PowerMenuMatcher.isPowerMenuLabel("Power off hotspot"))
        assertFalse(PowerMenuMatcher.isPowerMenuLabel(null))
    }

    @Test fun detectsRestartAndPowerOffScreens() {
        assertTrue(PowerMenuMatcher.matchesPowerMenuScreen(listOf("Emergency", "Lockdown", "Power off", "Restart")))
        assertTrue(PowerMenuMatcher.matchesPowerMenuScreen(listOf("Emergency call", "Restart")))
        assertTrue(PowerMenuMatcher.matchesPowerMenuScreen(listOf("Slide to restart", "Slide to power off")))
        assertTrue(PowerMenuMatcher.matchesPowerMenuScreen(listOf("Restart", "Tap again to restart your phone")))
        assertTrue(PowerMenuMatcher.matchesPowerMenuScreen(emptyList(), listOf("com.android.systemui:id/global_actions_grid")))
    }

    @Test fun onlySystemWindowsAreCandidates() {
        assertTrue(PowerMenuMatcher.isPackageCandidate("com.android.systemui"))
        assertTrue(PowerMenuMatcher.isPackageCandidate("android"))
        assertTrue(PowerMenuMatcher.isPackageCandidate("com.oplus.globalactions"))
        assertFalse(PowerMenuMatcher.isPackageCandidate("com.whatsapp"))
        assertFalse(PowerMenuMatcher.isPackageCandidate("com.android.settings"))
    }
}
