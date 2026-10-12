package com.sugarbridge.app.sync

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.edit

/**
 * Whether this phone keeps the number and the arrow as two status bar icons. Android may bundle several
 * notifications of one app into a group shown as a single icon (Tenshi-chan's), and phone makers tune when.
 * Minute mode's own notification is a colourful foreground one, which Android leaves out of such bundles, so
 * the arrow's is the only one that could be bundled and normally isn't. If a phone bundles them all the same,
 * that is noted here and the arrow goes over the number in one icon instead. A system update tries again, and
 * so does choosing "Beside it" in Settings.
 */
object IconBundling {
    private const val PREFS = "status_icons"
    private const val KEY = "bundled_on"   // the system build that bundled them

    fun bundled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) == Build.FINGERPRINT

    fun markBundled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, Build.FINGERPRINT) }

    fun reset(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(KEY) }

    /** Android has put minute mode's notifications into a bundle of its own making. */
    fun bundledNow(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .any { (it.id == LiveSyncService.NOTIFICATION_ID || it.id == LiveSyncService.ARROW_ID) && it.overrideGroupKey != null }
}
