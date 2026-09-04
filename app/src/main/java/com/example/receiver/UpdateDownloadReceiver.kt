package com.example.receiver

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import com.example.update.UpdateManager

/**
 * Listens for DownloadManager finishing the update APK download (started by
 * UpdateManager.startDownload) and launches the system installer on it.
 */
class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L || !UpdateManager.isPendingDownload(context, downloadId)) {
            // Not our download (some other app/download on the device) - ignore.
            return
        }

        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val apkFile = downloadsDir.listFiles { f ->
            f.name.startsWith("focus-buddy-update-") && f.name.endsWith(".apk")
        }?.maxByOrNull { it.lastModified() } ?: return

        UpdateManager.promptInstall(context, apkFile)
    }
}
