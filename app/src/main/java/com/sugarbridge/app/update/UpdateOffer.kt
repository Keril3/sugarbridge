package com.sugarbridge.app.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sugarbridge.app.MainActivity
import com.sugarbridge.app.R
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offering a new version (Settings → About SugarBridge). With "Check for updates by itself" the app asks GitHub
 * when it opens, at most every 6 hours, and with "Offer to install" a window offers the new version once. While
 * the app is closed a daily check in the background offers it once in a notification. Each version is offered
 * once in each way; the banner on the home screen and the About window keep showing it.
 */
object UpdateOffer {
    private const val PREFS = "update"
    private const val OFFERED = "offered_version"     // the window was answered
    private const val NOTIFIED = "notified_version"   // the notification was posted
    const val CHANNEL = "updates"
    const val NOTIFICATION_ID = 3
    const val ACTION_OFFER = "com.sugarbridge.app.OFFER_UPDATE"
    const val ACTION_INSTALL = "com.sugarbridge.app.INSTALL_UPDATE"

    /** Whether to offer [release] in a window: both switches on and not answered for this version yet. */
    fun shouldOffer(context: Context, settings: AppSettings, release: Release): Boolean =
        settings.autoUpdateCheck && settings.updatePrompt && prefs(context).getString(OFFERED, null) != release.version

    /** The window for [version] was answered: "Later" or "Download and install". */
    fun markOffered(context: Context, version: String) {
        prefs(context).edit { putString(OFFERED, version) }
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)   // the window said it all
    }

    /** The notification about [release], once per version, if Android lets the app post notifications. */
    fun notifyOnce(context: Context, release: Release) {
        val prefs = prefs(context)
        if (prefs.getString(NOTIFIED, null) == release.version || prefs.getString(OFFERED, null) == release.version) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        // high importance: Android shows it popping up over the screen, once
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_updates_description)
                setShowBadge(false)
            }
        )
        fun open(action: String, code: Int) = PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tenshi)
            .setContentTitle(context.getString(R.string.update_available, release.version))
            .setContentText(context.getString(R.string.notif_update_text))
            .setContentIntent(open(ACTION_OFFER, 10))
            .addAction(Notification.Action.Builder(null, context.getString(R.string.update_install), open(ACTION_INSTALL, 11)).build())
            .setColor(0xFFF06FA4.toInt())
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        prefs.edit { putString(NOTIFIED, release.version) }
    }

    /**
     * The background check: with both switches on, asks GitHub (at most every 6 hours) and offers a new version
     * in a notification, once. Blocking: call it off the main thread.
     */
    fun checkInBackground(context: Context) {
        val settings = SettingsStore(context).load()
        if (!settings.autoUpdateCheck || !settings.updatePrompt) return
        Updater.check(context)?.let { notifyOnce(context, it) }
    }

    /** Forgets what was offered: for tests. */
    internal fun reset(context: Context) = prefs(context).edit { remove(OFFERED).remove(NOTIFIED) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The daily check in the background, which offers a new version in a notification. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            UpdateOffer.checkInBackground(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {   // no network or GitHub failed: WorkManager tries again later
            Log.w(TAG, "update check: ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "update check failed", e)
            Result.failure()
        }
    }

    companion object {
        const val TAG = "SugarBridge"
        const val WORK_NAME = "update-check"
    }
}

/** Schedules the daily background check from the settings. Safe to call any number of times. */
fun scheduleUpdateChecks(context: Context, settings: AppSettings = SettingsStore(context).load()) {
    val workManager = WorkManager.getInstance(context)
    if (!settings.autoUpdateCheck || !settings.updatePrompt) {
        workManager.cancelUniqueWork(UpdateCheckWorker.WORK_NAME)
        return
    }
    val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    workManager.enqueueUniquePeriodicWork(UpdateCheckWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
