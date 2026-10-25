package com.example

import com.example.service.PowerMenuMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Power-menu guard rules: must catch the menu, must NOT fire on shade/notification text. */
class PowerMenuMatcherTest {
    @Test fun detectsPowerMenuWindowClasses() {
        assertTrue(PowerMenuMatcher.isPowerMenuClass("com.android.systemui.globalactions.GlobalActionsDialogLite"))
        assertTrue(PowerMenuMatcher.isPowerMenuClass("com.android.internal.app.ShutdownActivity"))
    }

    @Test fun ignoresOrdinaryWindowClasses() {
        assertFalse(PowerMenuMatcher.isPowerMenuClass(null))
        assertFalse(PowerMenuMatcher.isPowerMenuClass(""))
        assertFalse(PowerMenuMatcher.isPowerMenuClass("android.widget.FrameLayout"))
        assertFalse(PowerMenuMatcher.isPowerMenuClass("com.android.systemui.statusbar.phone.StatusBarWindowView"))
    }

    @Test fun matchesOnlyTheExactPowerOffLabel() {
        assertTrue(PowerMenuMatcher.isPowerMenuLabel("Power off"))
        assertTrue(PowerMenuMatcher.isPowerMenuLabel("  POWER OFF "))
        assertTrue(PowerMenuMatcher.isPowerMenuLabel("Slide to power off"))
    }

    @Test fun genericWordsDoNotMatch() {
        // These appear in notification actions / normal dialogs; matching them closes the shade.
        assertFalse(PowerMenuMatcher.isPowerMenuLabel("Turn off"))
        assertFalse(PowerMenuMatcher.isPowerMenuLabel("Restart"))
        assertFalse(PowerMenuMatcher.isPowerMenuLabel("बंद करें"))
        assertFalse(PowerMenuMatcher.isPowerMenuLabel("Power off hotspot"))
        assertFalse(PowerMenuMatcher.isPowerMenuLabel(null))
    }

    @Test fun onlySystemWindowsAreCandidates() {
        assertTrue(PowerMenuMatcher.isPackageCandidate("com.android.systemui"))
        assertTrue(PowerMenuMatcher.isPackageCandidate("android"))
        assertFalse(PowerMenuMatcher.isPackageCandidate("com.whatsapp"))
        assertFalse(PowerMenuMatcher.isPackageCandidate("com.android.settings"))
    }
}
