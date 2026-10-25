package com.example.update

import java.net.URI

/**
 * Decides whether a download URL is a release asset of OUR GitHub repo. MainActivity is exported, so
 * the update URL arriving in its intent extras is attacker-controllable; only
 * https://github.com/<owner>/<repo>/releases/download/... may ever reach DownloadManager.
 */
object ReleaseUrlGuard {
    fun isTrusted(url: String, repo: String): Boolean {
        val uri = try { URI(url.trim()) } catch (e: Exception) { return false }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        if (!"github.com".equals(uri.host, ignoreCase = true)) return false
        if (uri.port != -1 || uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return false
        val raw = uri.rawPath ?: return false
        val lower = raw.lowercase()
        // No dot-segment traversal and no percent-encoded dots/slashes/backslashes.
        if (lower.contains("..") || lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")) return false
        return lower.startsWith("/${repo.lowercase()}/releases/download/")
    }
}
