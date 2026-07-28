package com.seasonforge.widget.utils

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val isNewer: Boolean
)

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "com.seasonforge.widget.PREFS"
    private const val KEY_LAST_CHECK_TIME = "key_last_update_check_time"
    private const val KEY_LAST_DOWNLOAD_ID = "key_last_download_id"
    private const val CHECK_INTERVAL_MS = 24 * 3600 * 1000L // 24 hours

    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/SeasonForge/SeasonForgeMobile/releases/latest"
    private const val APK_FILE_NAME = "SeasonForge-update.apk"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Checks for updates from GitHub Releases API.
     * Runs asynchronously on a background thread.
     */
    fun checkForUpdate(context: Context, forceCheck: Boolean = false, callback: (ReleaseInfo?) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()

        if (!forceCheck && (now - lastCheck) < CHECK_INTERVAL_MS) {
            Log.d(TAG, "Skipping update check: last check was less than 24h ago")
            callback(null)
            return
        }

        Thread {
            try {
                val request = Request.Builder()
                    .url(GITHUB_RELEASES_API)
                    .header("User-Agent", "SeasonForgeMobileApp")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub API returned status code ${response.code}")
                    postResult(callback, null)
                    return@Thread
                }

                val bodyString = response.body?.string()
                if (bodyString.isNull_orEmpty()) {
                    postResult(callback, null)
                    return@Thread
                }

                val jsonObject = JsonParser.parseString(bodyString).asJsonObject
                val tagName = jsonObject.get("tag_name")?.let { if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else "" } ?: ""
                val releaseNotes = jsonObject.get("body")?.let { if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else "" } ?: ""

                var apkUrl = ""
                val assets = if (jsonObject.has("assets") && jsonObject.get("assets").isJsonArray) jsonObject.getAsJsonArray("assets") else null
                if (assets != null) {
                    for (element in assets) {
                        if (!element.isJsonObject) continue
                        val assetObj = element.asJsonObject
                        val name = assetObj.get("name")?.let { if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else "" } ?: ""
                        val downloadUrl = assetObj.get("browser_download_url")?.let { if (it.isJsonPrimitive && it.asJsonPrimitive.isString) it.asString else "" } ?: ""
                        if (name.endsWith(".apk", ignoreCase = true) || downloadUrl.endsWith(".apk", ignoreCase = true)) {
                            if (isValidDomain(downloadUrl)) {
                                apkUrl = downloadUrl
                                break
                            }
                        }
                    }
                }

                if (tagName.isNotEmpty() && apkUrl.isNotEmpty()) {
                    val currentVersion = getAppVersionName(context)
                    val isNewer = SemVerUtils.isNewer(currentVersion, tagName)

                    prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

                    val releaseInfo = ReleaseInfo(
                        tagName = tagName,
                        releaseNotes = releaseNotes,
                        apkUrl = apkUrl,
                        isNewer = isNewer
                    )
                    postResult(callback, releaseInfo)
                } else {
                    postResult(callback, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                postResult(callback, null)
            }
        }.start()
    }

    /**
     * Downloads the APK file using DownloadManager and initiates installation when complete.
     */
    fun downloadAndInstallApk(activity: Activity, releaseInfo: ReleaseInfo) {
        val context = activity.applicationContext
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            Toast.makeText(context, "DownloadManager unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. Remove previous download task from DownloadManager if any
        val oldDownloadId = prefs.getLong(KEY_LAST_DOWNLOAD_ID, -1L)
        if (oldDownloadId != -1L) {
            try {
                downloadManager.remove(oldDownloadId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove old download task $oldDownloadId", e)
            }
        }

        // 2. Explicitly delete any old target file BEFORE starting DownloadManager to prevent "-1" suffixing
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(targetDir, APK_FILE_NAME)
        if (targetFile.exists()) {
            val deleted = targetFile.delete()
            Log.d(TAG, "Old update file deleted before download: $deleted")
        }

        // 3. Configure DownloadManager request
        try {
            val uri = Uri.parse(releaseInfo.apkUrl)
            val request = DownloadManager.Request(uri).apply {
                setTitle("SeasonForge v${releaseInfo.tagName}")
                setDescription("Downloading app update...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadId = downloadManager.enqueue(request)
            prefs.edit().putLong(KEY_LAST_DOWNLOAD_ID, downloadId).apply()

            Toast.makeText(context, "Downloading update v${releaseInfo.tagName}...", Toast.LENGTH_SHORT).show()

            // 4. Register receiver for download completion
            val activityRef = java.lang.ref.WeakReference(activity)
            val onCompleteReceiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id == downloadId) {
                        try {
                            context.unregisterReceiver(this)
                        } catch (_: Exception) {}

                        val currentActivity = activityRef.get()
                        if (targetFile.exists() && targetFile.length() > 0) {
                            if (currentActivity != null && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
                                promptInstallApk(currentActivity, targetFile)
                            }
                        } else {
                            if (currentActivity != null && !currentActivity.isFinishing && !currentActivity.isDestroyed) {
                                Toast.makeText(currentActivity, "Download failed or file corrupted", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    onCompleteReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.registerReceiver(
                    onCompleteReceiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initiating APK download", e)
            Toast.makeText(activity, "Failed to start download: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Triggers the system package installer intent for the downloaded APK file.
     */
    fun promptInstallApk(activity: Activity, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, "APK file not found", Toast.LENGTH_SHORT).show()
            return
        }

        // On Android 8.0+ (API 26), verify if app can request package installations
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(activity, "Please allow SeasonForge to install unknown apps", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
                return
            }
        }

        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activity.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting package installer", e)
            Toast.makeText(activity, "Unable to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isValidDomain(urlStr: String): Boolean {
        return try {
            val host = Uri.parse(urlStr).host ?: ""
            host.equals("github.com", ignoreCase = true) ||
                    host.endsWith(".github.com", ignoreCase = true) ||
                    host.endsWith(".githubusercontent.com", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun postResult(callback: (ReleaseInfo?) -> Unit, result: ReleaseInfo?) {
        Handler(Looper.getMainLooper()).post {
            callback(result)
        }
    }

    fun getAppVersionName(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun String?.isNull_orEmpty(): Boolean = this == null || this.trim().isEmpty()
}
