package com.example

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.abs

/**
 * Tamper-resistant "now" for everything that enforces a time limit: Strict Mode sessions, block
 * windows, daily quotas, PIN lockouts and the override cooldown.
 *
 * System.currentTimeMillis() is the wall clock, and the user can change it in Date & time settings -
 * moving it forward would instantly "expire" a Strict Mode session or a block.
 * SystemClock.elapsedRealtime() is monotonic (it keeps counting through deep sleep and only resets on
 * reboot), so the user cannot move it.
 *
 * We keep an anchor (trusted time, elapsedRealtime, boot count) and derive "now" from the monotonic
 * clock. The wall clock is only followed when it agrees with the monotonic clock (ordinary drift / an
 * NTP nudge), when "Automatic date & time" is on (a big jump is then a genuine network correction), or
 * right after a reboot (minus any manual offset we already detected).
 */
object TrustedClock {
    private const val PREFS = "trusted_clock"
    private const val KEY_TRUSTED = "anchor_trusted"
    private const val KEY_ELAPSED = "anchor_elapsed"
    private const val KEY_BOOT = "anchor_boot"
    private const val KEY_OFFSET = "wall_offset"

    /** Wall/monotonic disagreement up to this is treated as ordinary drift or an NTP nudge. */
    private const val DRIFT_TOLERANCE_MS = 120_000L

    private var loaded = false
    private var anchorTrusted = 0L
    private var anchorElapsed = 0L
    private var anchorBoot = -1

    /** Wall clock minus trusted time. Non-zero only after a manual clock change was detected. */
    private var wallOffset = 0L
    private var bootCountCache = Int.MIN_VALUE

    @Synchronized
    fun now(context: Context = FocusApplication.instance): Long {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val boot = bootCount(context)
        if (!loaded) load(context, wall, elapsed, boot)

        if (boot != anchorBoot || elapsed < anchorElapsed) {
            // Rebooted since the anchor: elapsedRealtime restarted from zero, so the wall clock is the
            // only reference left (minus any manual offset we already know about).
            reanchor(context, wall - wallOffset, elapsed, boot)
            return anchorTrusted
        }

        val trusted = anchorTrusted + (elapsed - anchorElapsed)
        val drift = (wall - wallOffset) - trusted
        if (abs(drift) <= DRIFT_TOLERANCE_MS) return trusted

        if (isAutoTimeOn(context)) {
            // Network time is on, so the user cannot have set this by hand: genuine correction, adopt it.
            wallOffset = 0L
            reanchor(context, wall, elapsed, boot)
            return anchorTrusted
        }

        // Manual clock change: ignore it. Remember the offset so the wall clock can still be mapped
        // back to trusted time after a reboot.
        wallOffset += drift
        save(context)
        return trusted
    }

    private fun load(context: Context, wall: Long, elapsed: Long, boot: Int) {
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_TRUSTED)) {
            anchorTrusted = prefs.getLong(KEY_TRUSTED, wall)
            anchorElapsed = prefs.getLong(KEY_ELAPSED, elapsed)
            anchorBoot = prefs.getInt(KEY_BOOT, boot)
            wallOffset = prefs.getLong(KEY_OFFSET, 0L)
        } else {
            anchorTrusted = wall
            anchorElapsed = elapsed
            anchorBoot = boot
            wallOffset = 0L
            save(context)
        }
    }

    private fun reanchor(context: Context, trusted: Long, elapsed: Long, boot: Int) {
        anchorTrusted = trusted
        anchorElapsed = elapsed
        anchorBoot = boot
        save(context)
    }

    private fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_TRUSTED, anchorTrusted)
            .putLong(KEY_ELAPSED, anchorElapsed)
            .putInt(KEY_BOOT, anchorBoot)
            .putLong(KEY_OFFSET, wallOffset)
            .apply()
    }

    private fun bootCount(context: Context): Int {
        if (bootCountCache == Int.MIN_VALUE) {
            bootCountCache = try {
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
            } catch (e: Exception) {
                -1
            }
        }
        return bootCountCache
    }

    private fun isAutoTimeOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
    } catch (e: Exception) {
        false
    }
}
