package com.example.service

/**
 * Pure helpers for the colon-separated Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES value.
 * An entry may be written "pkg/full.ClassName" or "pkg/.ShortName"; both are normalised before
 * comparing, so the same service is never added twice.
 */
object AccessibilityListUtil {
    /** "pkg/.Cls" -> "pkg/pkg.cls"; "pkg/a.b.Cls" -> "pkg/a.b.cls"; no "/" -> null. */
    fun normalize(entry: String): String? {
        val e = entry.trim()
        val slash = e.indexOf('/')
        if (slash <= 0 || slash == e.length - 1) return null
        val pkg = e.substring(0, slash)
        var cls = e.substring(slash + 1)
        if (cls.startsWith(".")) cls = pkg + cls
        return "$pkg/$cls".lowercase()
    }

    fun entries(current: String?): List<String> =
        current.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }

    fun contains(current: String?, flat: String): Boolean {
        val target = normalize(flat) ?: return false
        return entries(current).any { normalize(it) == target }
    }

    /** Returns [current] with [flat] appended (unchanged if it is already listed). */
    fun add(current: String?, flat: String): String {
        if (contains(current, flat)) return current.orEmpty()
        return (entries(current) + flat).joinToString(":")
    }
}
