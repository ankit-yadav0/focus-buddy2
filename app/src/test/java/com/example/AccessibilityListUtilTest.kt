package com.example

import com.example.service.AccessibilityListUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Banking Pause re-enables the service by editing Settings.Secure's enabled-services list. */
class AccessibilityListUtilTest {
    private val flat = "com.aistudio.focussbuddy.kxmpzq/com.example.service.FocusAccessibilityService"

    @Test fun addsToEmptyAndNullLists() {
        assertEquals(flat, AccessibilityListUtil.add(null, flat))
        assertEquals(flat, AccessibilityListUtil.add("", flat))
    }

    @Test fun keepsOtherServices() {
        val other = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        assertEquals("$other:$flat", AccessibilityListUtil.add(other, flat))
    }

    @Test fun neverAddsTwice() {
        val once = AccessibilityListUtil.add("a.b/a.b.C", flat)
        assertEquals(once, AccessibilityListUtil.add(once, flat))
    }

    @Test fun recognisesShortFormAndCase() {
        assertTrue(AccessibilityListUtil.contains("x.y/.Svc", "x.y/x.y.Svc"))
        assertTrue(AccessibilityListUtil.contains("X.Y/X.Y.SVC", "x.y/x.y.svc"))
        assertFalse(AccessibilityListUtil.contains("x.y/.Other", "x.y/x.y.Svc"))
        assertFalse(AccessibilityListUtil.contains(null, flat))
        assertFalse(AccessibilityListUtil.contains("garbage", flat))
    }

    @Test fun toleratesStrayColonsAndSpaces() {
        assertTrue(AccessibilityListUtil.contains(":: a.b/a.b.C :", "a.b/a.b.C"))
        assertEquals("a.b/a.b.C:$flat", AccessibilityListUtil.add(":a.b/a.b.C:", flat))
    }
}
