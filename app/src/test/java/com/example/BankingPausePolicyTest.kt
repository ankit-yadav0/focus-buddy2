package com.example

import com.example.service.BankingPausePolicy
import com.example.service.BankingPausePolicy.Reason
import com.example.service.BankingPausePolicy.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankingPausePolicyTest {
    private val navi = "com.naviapp"
    private val banking = setOf(navi, "com.phonepe.app")
    private val t0 = 1_000_000L

    private fun poll(pkg: String, at: Long, prev: State = State(), interactive: Boolean = true, usage: Boolean = true) =
        BankingPausePolicy.decide(pkg, banking, at, t0, interactive, usage, prev)

    // ---- eligibility: banking pause must never become a bypass --------------------------------
    @Test fun blockedAppsCanNeverPause() {
        assertFalse(BankingPausePolicy.isEligible("com.example.game", setOf("com.example.game"), emptySet()))
        assertFalse(BankingPausePolicy.isEligible("com.example.game", emptySet(), setOf("com.example.game")))
        assertTrue(BankingPausePolicy.isEligible(navi, setOf("com.other"), emptySet()))
    }

    @Test fun socialBrowserAndSystemAppsCanNeverPause() {
        for (p in listOf("com.instagram.android", "com.google.android.youtube", "com.snapchat.android",
            "com.android.chrome", "com.brave.browser", "com.android.settings", "com.android.systemui", "")) {
            assertFalse(p, BankingPausePolicy.isEligible(p, emptySet(), emptySet()))
        }
    }

    @Test fun packageListRoundTrips() {
        val s = setOf("b.pkg", "a.pkg", "c.pkg")
        assertEquals(s, BankingPausePolicy.parsePackages(BankingPausePolicy.serializePackages(s)))
        assertEquals(emptySet<String>(), BankingPausePolicy.parsePackages(null))
        assertEquals(setOf("a", "b"), BankingPausePolicy.parsePackages(" a, ,b ,,"))
    }

    // ---- when does protection come back? ------------------------------------------------------
    @Test fun staysPausedWhileBankingAppIsForeground() {
        assertNull(poll(navi, t0 + 30_000).resume)
        assertNull(poll("com.phonepe.app", t0 + 60_000).resume)
    }

    @Test fun resumesQuicklyAfterLeavingToANormalApp() {
        val first = poll("com.android.launcher", t0 + 5_000)
        assertNull(first.resume)                       // seen once: not yet confirmed
        val second = poll("com.android.launcher", t0 + 5_000 + BankingPausePolicy.LEAVE_CONFIRM_MS, first.next)
        assertEquals(Reason.LEFT_BANKING_APP, second.resume)
    }

    @Test fun settingsIsNotGivenTheTransientGrace() {
        val first = poll("com.android.settings", t0 + 5_000)
        val second = poll("com.android.settings", t0 + 5_000 + BankingPausePolicy.LEAVE_CONFIRM_MS, first.next)
        assertEquals(Reason.LEFT_BANKING_APP, second.resume)
    }

    @Test fun systemDialogsGetAGraceAndABlipIsForgiven() {
        val first = poll("com.android.systemui", t0 + 5_000)
        val mid = poll("com.android.systemui", t0 + 7_000, first.next)
        assertNull(mid.resume)                          // still inside the grace
        val back = poll(navi, t0 + 8_000, mid.next)     // came back to the banking app
        assertNull(back.resume)
        assertEquals(0L, back.next.nonBankingSinceMs)   // timer reset
    }

    @Test fun systemWindowThatLastsTooLongCounts() {
        val first = poll("com.google.android.gms", t0 + 5_000)
        val late = poll("com.google.android.gms", t0 + 5_000 + BankingPausePolicy.TRANSIENT_GRACE_MS, first.next)
        assertEquals(Reason.LEFT_BANKING_APP, late.resume)
    }

    // ---- safety nets: protection must always come back ----------------------------------------
    @Test fun hardCapAlwaysResumes() {
        assertEquals(Reason.TIMEOUT, poll(navi, t0 + BankingPausePolicy.MAX_PAUSE_MS).resume)
        assertNull(poll(navi, t0 + BankingPausePolicy.MAX_PAUSE_MS - 1).resume)
    }

    @Test fun staleMarkerAfterRebootResumes() {
        // monotonic clock restarted: now < started
        assertEquals(Reason.TIMEOUT, BankingPausePolicy.decide(navi, banking, 5_000L, t0, true, true, State()).resume)
    }

    @Test fun lostUsageAccessResumesImmediately() {
        assertEquals(Reason.NO_USAGE_ACCESS, poll(navi, t0 + 10_000, usage = false).resume)
    }

    @Test fun longScreenOffResumes() {
        val off1 = poll(navi, t0 + 10_000, interactive = false)
        assertNull(off1.resume)
        val off2 = poll(navi, t0 + 10_000 + BankingPausePolicy.SCREEN_OFF_RESUME_MS, off1.next, interactive = false)
        assertEquals(Reason.SCREEN_OFF, off2.resume)
    }

    @Test fun shortScreenOffDoesNotResume() {
        val off = poll(navi, t0 + 10_000, interactive = false)
        val on = poll(navi, t0 + 30_000, off.next, interactive = true)
        assertNull(on.resume)
        assertEquals(0L, on.next.screenOffSinceMs)
    }
}
