package com.example

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2 hashing for the App Lock PIN, plus a persistent failed-attempt
 * lockout. Stored format: "pbkdf2$<iterations>$<saltHex>$<hashHex>". Legacy PINs
 * (bare 64-char SHA-256 hex) still verify and are re-hashed on the next success.
 */
object PinSecurity {
    private const val ITERATIONS = 20_000
    private const val PREFS = "focuss_buddy_settings"
    private const val FREE_ATTEMPTS = 5

    /** Lockout counters live under "<scope>_fail_count" / "<scope>_lock_until". "pin" = App Lock. */
    const val SCOPE_APP_LOCK = "pin"
    const val SCOPE_STRICT_PASSWORD = "strict_pw"
    private fun failsKey(scope: String) = "${scope}_fail_count"
    private fun lockKey(scope: String) = "${scope}_lock_until"

    fun hash(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "pbkdf2$$ITERATIONS$${toHex(salt)}$${toHex(pbkdf2(pin, salt, ITERATIONS))}"
    }

    fun verify(pin: String, stored: String?): Boolean {
        if (stored.isNullOrEmpty()) return false
        return if (stored.startsWith("pbkdf2$")) {
            val parts = stored.split("$")
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = fromHex(parts[2]) ?: return false
            MessageDigest.isEqual(toHex(pbkdf2(pin, salt, iterations)).toByteArray(), parts[3].toByteArray())
        } else {
            MessageDigest.isEqual(legacySha256(pin).toByteArray(), stored.toByteArray())
        }
    }

    fun isLegacy(stored: String?): Boolean = !stored.isNullOrEmpty() && !stored.startsWith("pbkdf2$")

    /** Milliseconds left on the current lockout, or 0 if attempts are allowed. */
    fun remainingLockMs(context: Context, scope: String = SCOPE_APP_LOCK): Long {
        val until = prefs(context).getLong(lockKey(scope), 0L)
        return (until - TrustedClock.now()).coerceAtLeast(0L)
    }

    fun recordFailure(context: Context, scope: String = SCOPE_APP_LOCK) {
        val p = prefs(context)
        val fails = p.getInt(failsKey(scope), 0) + 1
        val editor = p.edit().putInt(failsKey(scope), fails)
        if (fails >= FREE_ATTEMPTS) {
            val step = (fails - FREE_ATTEMPTS).coerceAtMost(5)
            val lockMs = (30_000L shl step).coerceAtMost(15 * 60_000L)
            editor.putLong(lockKey(scope), TrustedClock.now() + lockMs)
        }
        editor.apply()
    }

    fun recordSuccess(context: Context, scope: String = SCOPE_APP_LOCK) {
        prefs(context).edit().putInt(failsKey(scope), 0).putLong(lockKey(scope), 0L).apply()
    }

    fun lockMessage(ms: Long): String = "Too many attempts. Try again in ${(ms + 999) / 1000}s"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 160)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    private fun legacySha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun toHex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun fromHex(s: String): ByteArray? = try {
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    } catch (e: Exception) { null }
}
