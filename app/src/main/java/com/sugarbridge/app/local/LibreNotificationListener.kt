package com.sugarbridge.app.local

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.sync.SyncStore
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android hands a notification listener every notification once notification access is given. All but those of
 * Abbott's apps are left at once and nothing of them is kept; see [LibreNotifications] for the rest.
 */
class LibreNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!LibreNotifications.isAbbott(sbn.packageName)) return
        LibreNotifications.handle(this, sbn.notification, sbn.postTime, scope)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun component(context: Context) = ComponentName(context, LibreNotificationListener::class.java)

        fun hasAccess(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))
    }
}

/**
 * The glucose from the notifications of Abbott's apps (LibreLink in its regional versions, the Libre 3 app), if
 * they show it, while "From LibreLink's notification" is on. A notification without a value is noted in Settings
 * and the event log, so it shows what LibreLink puts there on this phone.
 */
object LibreNotifications {

    fun isAbbott(packageName: String) = packageName.startsWith("com.freestylelibre")

    fun handle(context: Context, notification: Notification, postTime: Long, scope: CoroutineScope) {
        if (!SettingsStore(context).load().localNotification) return
        val texts = texts(context, notification)
        val found = LocalGlucose.inTexts(texts)
        val now = Instant.now()
        if (found == null) {
            val first = texts.take(2).joinToString(" · ").take(80)   // the title and the first line
            if (SyncStore(context).saveLocalMiss(first, now)) DebugLog.add(context, "local", "LibreLink: no glucose in “$first”")
            return
        }
        // the notification's own time when it is near the posting, otherwise the moment it came
        val shown = Instant.ofEpochMilli(notification.`when`)
        val posted = Instant.ofEpochMilli(postTime)
        val time = if (Duration.between(shown, posted).abs() <= Duration.ofMinutes(5)) shown else posted
        val reading = LocalGlucose.reading(found.first, time.toEpochMilli(), found.second, now) ?: return
        scope.launch { LocalReadings.receive(context.applicationContext, reading, "LibreLink") }
    }

    /** The visible texts: the standard fields and, for a notification of its own layout, the texts in it. */
    fun texts(context: Context, n: Notification): List<String> {
        val extras = n.extras
        val standard = listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_SUMMARY_TEXT,
        ).mapNotNull { extras.getCharSequence(it)?.toString() } +
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString() }
        @Suppress("DEPRECATION")
        val custom = listOfNotNull(n.contentView, n.bigContentView).flatMap { textsIn(context, it) }
        return (standard + custom).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private fun textsIn(context: Context, views: RemoteViews): List<String> = try {
        val root = views.apply(context, FrameLayout(context))
        buildList { collect(root, this) }
    } catch (e: Exception) {
        emptyList()   // a layout that can't be drawn here: its texts stay unknown
    }

    private fun collect(view: View, into: MutableList<String>) {
        if (view is TextView) view.text?.toString()?.let(into::add)
        if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i), into)
    }
}
