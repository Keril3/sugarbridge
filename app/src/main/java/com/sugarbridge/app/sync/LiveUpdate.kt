package com.sugarbridge.app.sync

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Live Updates (Android 16 and newer). Android shows an ongoing notification it promotes as a capsule next to
 * the clock: its small icon and a short text in the status bar's own font. SugarBridge puts the trend arrow in
 * the icon and the number in the text, so both are as large as the clock's digits; in the square icon of an
 * ordinary notification they can't be. Not every phone draws the capsule, so Settings can turn it off, and the
 * square icon is used whenever the capsule isn't possible.
 */
object LiveUpdate {
    /** Android has Live Updates at all. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

    /** Android lets SugarBridge post them: they are supported and not turned off for the app. */
    fun allowed(context: Context): Boolean =
        supported && context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()

    /** Minute mode's notification is a Live Update right now. */
    fun shown(context: Context): Boolean = supported &&
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .any { it.id == LiveSyncService.NOTIFICATION_ID && it.notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0 }

    /** The page where Live Updates are allowed for SugarBridge, or null before Android 16. */
    fun settings(context: Context): Intent? =
        if (supported) Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) else null
}
