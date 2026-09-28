package com.example.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

/**
 * Checks GitHub Releases for a newer build, notifies the user, and downloads +
 * installs it via the system package installer.
 *
 * Data safety: as long as every release APK is signed with the SAME key (the
 * persistent "release" keystore the CI workflow already uses - see
 * .github/workflows/main2.yml) and keeps the same applicationId, Android
 * treats this as a normal in-place app UPDATE - all Room DB data, settings,
 * and files are preserved automatically by the OS. Never distribute a debug
 * APK through this flow: main2.yml generates a fresh debug keystore on every
 * CI run, so consecutive debug builds don't share a signature and installing
 * one "over" another forces an uninstall (wiping all data) instead of an
 * update.
 */
object UpdateManager {

    // Set this to your GitHub repo as "owner/repo", e.g. "ankit123/focus-buddy2".
    // Update checks are a no-op until this is filled in.
    private const val GITHUB_REPO = "ankit-yadav0/focus-buddy2"

    private const val PREFS_NAME = "focuss_buddy_settings"
    private const val KEY_LAST_CHECK = "update_last_check_time"
    private const val KEY_LAST_STATUS = "update_last_status"
    private const val KEY_PENDING_DOWNLOAD_ID = "update_pending_download_id"
    private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L // at most one GitHub check per 15 min

    private const val NOTIFICATION_CHANNEL_ID = "focus_buddy_updates"
    private const val NOTIFICATION_ID = 4301

    private val client = OkHttpClient()

    /**
     * Looks up the newest published GitHub Release (drafts skipped, pre-releases
     * allowed) and returns it if it is newer than the installed build. Returns null on
     * any failure, if already up to date, or during the cooldown (force=true bypasses
     * it). The reason for a null is stored and readable via [lastStatus].
     * Never throws - safe to call from a background coroutine.
     */
    suspend fun checkForUpdate(context: Context, force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fun status(msg: String): UpdateInfo? {
            Log.d("UpdateManager", msg)
            prefs.edit().putString(KEY_LAST_STATUS, msg).apply()
            return null
        }
        try {
            if (GITHUB_REPO.startsWith("YOUR_")) return@withContext status("repo not configured")

            val now = System.currentTimeMillis()
            if (!force) {
                val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
                if (now - lastCheck < CHECK_INTERVAL_MS) return@withContext null
            }

            val request = Request.Builder()
                .url("https://api.github.com/repos/$GITHUB_REPO/releases?per_page=5")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FocussBuddy-Updater")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext status("GitHub HTTP ${response.code} (404 = repo private/wrong name, 403 = rate limit)")
                }
                // Only a real answer from GitHub uses up the cooldown; a failed
                // request (offline, rate-limited) must not block the next try.
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

                val body = response.body?.string() ?: return@withContext status("empty response")
                val releases = org.json.JSONArray(body)
                if (releases.length() == 0) return@withContext status("no releases published")

                var sawNewerWithoutApk = false
                for (r in 0 until releases.length()) {
                    val json = releases.getJSONObject(r)
                    if (json.optBoolean("draft")) continue
                    val tagName = json.optString("tag_name")
                    if (!isNewerThanInstalled(tagName)) continue

                    val assets = json.optJSONArray("assets")
                    var apkUrl: String? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                    }
                    if (apkUrl == null) { sawNewerWithoutApk = true; continue }

                    prefs.edit().putString(KEY_LAST_STATUS, "update found: $tagName").apply()
                    return@withContext UpdateInfo(
                        versionCode = tagName.removePrefix("v").removePrefix("V").toIntOrNull() ?: 0,
                        versionName = json.optString("name").ifBlank { tagName },
                        downloadUrl = apkUrl,
                        releaseNotes = json.optString("body")
                    )
                }
                if (sawNewerWithoutApk) status("newer release found but it has no .apk file attached")
                else status("up to date (installed ${BuildConfig.VERSION_NAME} / code ${BuildConfig.VERSION_CODE})")
            }
        } catch (e: Exception) {
            status("check failed: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    /** Last result of an update check, for debugging ("why no prompt?"). */
    fun lastStatus(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_STATUS, "never checked") ?: "never checked"

    /**
     * Tag "v57" (or "57") -> compared with the installed versionCode.
     * Tag "v1.2.3" -> compared, part by part, with the installed versionName.
     */
    private fun isNewerThanInstalled(tag: String): Boolean {
        val t = tag.trim().removePrefix("v").removePrefix("V")
        t.toIntOrNull()?.let { return it > BuildConfig.VERSION_CODE }
        val remote = t.split(".", "-").map { it.toIntOrNull() ?: return false }
        val local = BuildConfig.VERSION_NAME.removePrefix("v").split(".", "-").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    /** Checks for an update and posts the notification if one is found. */
    suspend fun checkAndNotify(context: Context, force: Boolean = false) {
        val info = checkForUpdate(context, force) ?: return
        showUpdateNotification(context, info)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies you when a new Focuss Buddy update is available"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun showUpdateNotification(context: Context, info: UpdateInfo) {
        ensureChannel(context)

        // Tapping the notification opens MainActivity and immediately starts
        // the download - no extra screen in between, matching "click pe hi
        // download start ho jaye".
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("start_update_download", true)
            putExtra("update_download_url", info.downloadUrl)
            putExtra("update_version_name", info.versionName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Update available: ${info.versionName}")
            .setContentText("Tap to download and install. Your data stays exactly as it is.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Tap to download and install. Your data stays exactly as it is."
            ))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Starts downloading the APK via the system DownloadManager - it survives
     * the app being backgrounded and shows its own progress notification.
     * UpdateDownloadReceiver picks up completion and launches the installer.
     */
    fun startDownload(context: Context, downloadUrl: String, versionName: String) {
        val safeVersion = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "focus-buddy-update-$safeVersion.apk"
        val destFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Focuss Buddy update")
            .setDescription("Downloading version $versionName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destFile))
            .setAllowedOverMetered(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PENDING_DOWNLOAD_ID, downloadId)
            .apply()
    }

    fun isPendingDownload(context: Context, downloadId: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_PENDING_DOWNLOAD_ID, -1L) == downloadId
    }

    /** Called by UpdateDownloadReceiver once the download finishes successfully. */
    fun promptInstall(context: Context, apkFile: File) {
        if (!apkFile.exists()) return
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            // Best-effort: if "install unknown apps" isn't granted for this
            // source yet, the system shows that prompt itself on the first
            // real attempt; the APK also stays in Downloads for manual install.
        }
    }
}
