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
    private const val KEY_PENDING_DOWNLOAD_ID = "update_pending_download_id"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // don't hit GitHub more than once every 6h

    private const val NOTIFICATION_CHANNEL_ID = "focus_buddy_updates"
    private const val NOTIFICATION_ID = 4301

    private val client = OkHttpClient()

    /**
     * Looks up the latest GitHub Release and returns it if it's newer than the
     * currently-installed build. Returns null on any failure, if not configured,
     * if already up to date, or if called again before the cooldown elapses
     * (pass force=true to bypass the cooldown, e.g. for a manual "Check now").
     * Never throws - safe to call from a background coroutine on app start.
     */
    suspend fun checkForUpdate(context: Context, force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            if (GITHUB_REPO.startsWith("YOUR_")) return@withContext null

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (!force) {
                val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
                if (now - lastCheck < CHECK_INTERVAL_MS) return@withContext null
            }
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

            val request = Request.Builder()
                .url("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                // The release tag is expected to be "v<versionCode>" (the CI
                // workflow tags releases this way using the run number), so
                // comparison is a simple integer check - no messy string
                // version-name parsing needed.
                val tagName = json.optString("tag_name")
                val remoteVersionCode = tagName.removePrefix("v").toIntOrNull() ?: return@withContext null
                if (remoteVersionCode <= BuildConfig.VERSION_CODE) return@withContext null

                val assets = json.optJSONArray("assets") ?: return@withContext null
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                val url = apkUrl ?: return@withContext null

                UpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = json.optString("name").ifBlank { tagName },
                    downloadUrl = url,
                    releaseNotes = json.optString("body")
                )
            }
        } catch (e: Exception) {
            null
        }
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
                    description = "Notifies you when a new Focus Buddy update is available"
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
            .setTitle("Focus Buddy update")
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
