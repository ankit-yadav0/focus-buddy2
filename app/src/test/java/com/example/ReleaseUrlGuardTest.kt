package com.example

import com.example.update.ReleaseUrlGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: MainActivity is exported, so the update URL in its intent extras must be vetted. */
class ReleaseUrlGuardTest {
    private val repo = "ankit-yadav0/focus-buddy2"
    private val good = "https://github.com/ankit-yadav0/focus-buddy2/releases/download/v57/app-release.apk"

    @Test fun acceptsOfficialReleaseAsset() = assertTrue(ReleaseUrlGuard.isTrusted(good, repo))

    @Test fun repoMatchIsCaseInsensitive() =
        assertTrue(ReleaseUrlGuard.isTrusted(good.replace("ankit-yadav0", "Ankit-Yadav0"), repo))

    @Test fun rejectsPlainHttp() = assertFalse(ReleaseUrlGuard.isTrusted(good.replace("https", "http"), repo))

    @Test fun rejectsOtherHosts() {
        assertFalse(ReleaseUrlGuard.isTrusted("https://evil.example/ankit-yadav0/focus-buddy2/releases/download/v1/a.apk", repo))
        assertFalse(ReleaseUrlGuard.isTrusted("https://github.com.evil.example/ankit-yadav0/focus-buddy2/releases/download/v1/a.apk", repo))
        assertFalse(ReleaseUrlGuard.isTrusted("https://github.com@evil.example/ankit-yadav0/focus-buddy2/releases/download/v1/a.apk", repo))
    }

    @Test fun rejectsOtherRepos() =
        assertFalse(ReleaseUrlGuard.isTrusted("https://github.com/someone/else/releases/download/v1/a.apk", repo))

    @Test fun rejectsTraversalAndEncodedTricks() {
        assertFalse(ReleaseUrlGuard.isTrusted("https://github.com/ankit-yadav0/focus-buddy2/releases/download/../../../evil/x/raw/a.apk", repo))
        assertFalse(ReleaseUrlGuard.isTrusted("https://github.com/ankit-yadav0/focus-buddy2/releases/download/%2e%2e/%2e%2e/a.apk", repo))
        assertFalse(ReleaseUrlGuard.isTrusted("https://github.com/ankit-yadav0/focus-buddy2/releases/download/v1%2f..%2fa.apk", repo))
    }

    @Test fun rejectsPortQueryFragmentAndGarbage() {
        assertFalse(ReleaseUrlGuard.isTrusted(good.replace("github.com", "github.com:8443"), repo))
        assertFalse(ReleaseUrlGuard.isTrusted("$good?x=1", repo))
        assertFalse(ReleaseUrlGuard.isTrusted("$good#frag", repo))
        assertFalse(ReleaseUrlGuard.isTrusted("", repo))
        assertFalse(ReleaseUrlGuard.isTrusted("not a url", repo))
        assertFalse(ReleaseUrlGuard.isTrusted("file:///sdcard/a.apk", repo))
    }
}
