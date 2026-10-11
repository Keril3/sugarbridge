package com.sugarbridge.app.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sugarbridge.app.MainActivity
import com.sugarbridge.app.R
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.WRITE_GLUCOSE
import com.sugarbridge.app.health.deleteOwnGlucose
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.health.ownMinuteKeys
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.resourcesIn
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.useAppLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking

/**
 * The whole chain on a device: a fake LibreLinkUp → sync → Health Connect,
 * and minute mode with glucose in the notification, in two languages.
 */
@RunWith(AndroidJUnit4::class)
class SyncEndToEndTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val from = Instant.now().minus(Duration.ofHours(14))
    private val to = Instant.now().plus(Duration.ofHours(1))
    private lateinit var cloud: FakeCloud

    private fun clearSyncState() {
        context.getSharedPreferences(SyncStore.NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Before
    fun setUp() {
        assumeTrue("Health Connect is not available", healthConnectState(context) == HealthConnectState.AVAILABLE)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, WRITE_GLUCOSE)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        cloud = FakeCloud(start = Instant.now().minusSeconds(180))   // the latest reading is 3.5 minutes old
        Libre.signOut(context)
        Libre.transportForTests = cloud
        Libre.signIn(context, "e2e@example.com", "secret")
        clearSyncState()
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0))
        runBlocking { deleteOwnGlucose(context, from, to) }
    }

    @After
    fun tearDown() {
        LiveSyncService.stop(context)
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0))
        runBlocking { deleteOwnGlucose(context, from, to) }
        Libre.signOut(context)
        Libre.transportForTests = null
        clearSyncState()
        useAppLanguage(context, null)
    }

    @Test
    fun everyReadingIsWrittenOnce() = runBlocking {
        val first = syncOnce(context)
        assertEquals(49, first.written)   // 48 history points + the current reading
        assertEquals(0, syncOnce(context).written)

        cloud.tick(60, 120.0)   // a new reading a minute later
        val third = syncOnce(context)
        assertEquals(1, third.written)
        assertEquals(120.0, third.latest!!.mgDl, 0.0)
        assertEquals(50, ownMinuteKeys(context, from, to).size)

        val snapshot = SyncStore(context).snapshot()
        assertEquals(1, snapshot.lastWritten)
        assertEquals(50, snapshot.recent.size)

        // the sensor clock runs ahead: Health Connect would reject a reading "from the future" with the whole batch
        cloud.tick(600, 130.0)
        val fourth = syncOnce(context)
        assertEquals(0, fourth.written)
        assertEquals(130.0, fourth.latest!!.mgDl, 0.0)   // it is still shown on screen
    }

    @Test
    fun liveModeShowsGlucoseThenGoesQuietInEnglish() = liveModeShowsGlucoseThenGoesQuiet("en")

    @Test
    fun liveModeShowsGlucoseThenGoesQuietInRussian() = liveModeShowsGlucoseThenGoesQuiet("ru")

    private fun liveModeShowsGlucoseThenGoesQuiet(language: String) {
        useAppLanguage(context, language)
        val res = resourcesIn(context, language)
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0, liveEnabled = true, liveShowValue = true, liveKeepAwake = false))
        // with the app on screen Android allows starting the service; MainActivity starts it by itself.
        // No ActivityScenario: it waits for the main thread to go idle, and animations on the emulator never let it
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        val unit = res.getString(R.string.unit_mmol)
        val withValue = waitForNotification { it.contains(LiveSyncService.CHANNEL_LIVE) && it.contains(unit) }
        assertTrue(withValue, withValue.contains(formatGlucose(115.0, GlucoseUnit.MMOL, res.configuration.locales[0])))
        assertTrue(withValue, withValue.contains(res.getString(R.string.level_in_range)))
        Thread.sleep(1500)
        saveScreenshot(language, "statusbar")   // glucose as a number in the status bar
        screenshotOfShade(language, "notification")

        SettingsStore(context).update { it.copy(liveShowValue = false) }
        LiveSyncService.reload(context)
        val quiet = waitForNotification { it.contains(LiveSyncService.CHANNEL_QUIET) }
        assertTrue(quiet, quiet.contains(res.getString(R.string.notif_watching)))

        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
    }

    /**
     * The app syncs by itself too: when opened, by "Sync now" and in the background. The notification must
     * show that newer value at once, not only on the service's own next round, which may be minutes away.
     */
    @Test
    fun notificationFollowsSyncsMadeOutsideTheService() {
        useAppLanguage(context, "en")
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0, liveEnabled = true, liveMinutes = 10, liveShowValue = true, liveKeepAwake = false))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val before = waitForNotification { it.contains(LiveSyncService.CHANNEL_LIVE) && it.contains(formatGlucose(115.0, GlucoseUnit.MMOL)) }
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()

        cloud.tick(60, 160.0)
        runBlocking { syncAndRecord(context) }   // what the app, the background job or "Sync now" do
        val after = waitForNotification(timeoutMs = 10_000) { it.contains(formatGlucose(160.0, GlucoseUnit.MMOL)) }
        assertTrue("$before → $after", after.contains(LiveSyncService.CHANNEL_LIVE))
    }

    /** The trend arrow and the number in the status bar each have a switch in Settings. */
    @Test
    fun arrowAndStatusBarNumberCanBeTurnedOff() {
        useAppLanguage(context, "en")
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0, liveEnabled = true, liveMinutes = 10, liveShowValue = true, liveKeepAwake = false))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val withArrow = waitForNotification { it.contains(LiveSyncService.CHANNEL_LIVE) && it.contains(formatGlucose(115.0, GlucoseUnit.MMOL)) }
        assertTrue(withArrow, withArrow.contains("↗"))   // LibreLinkUp says 4: rising
        assertEquals(Icon.TYPE_BITMAP, smallIcon().type)   // the number drawn as the icon
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
        Thread.sleep(1500)
        saveScreenshot("en", "statusbar_arrow")

        SettingsStore(context).update { it.copy(liveArrow = false) }
        LiveSyncService.reload(context)
        val noArrow = waitForNotification { it.contains(LiveSyncService.CHANNEL_LIVE) && !it.contains("↗") }
        assertTrue(noArrow, noArrow.contains(formatGlucose(115.0, GlucoseUnit.MMOL)))

        SettingsStore(context).update { it.copy(liveStatusValue = false) }
        LiveSyncService.reload(context)
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (smallIcon().type != Icon.TYPE_RESOURCE && SystemClock.uptimeMillis() < deadline) Thread.sleep(250)
        assertEquals(Icon.TYPE_RESOURCE, smallIcon().type)   // Tenshi-chan's icon again, the value still in the shade
        assertTrue(waitForNotification { it.contains(LiveSyncService.CHANNEL_LIVE) }.contains(formatGlucose(115.0, GlucoseUnit.MMOL)))
    }

    /** The developer log gets every sync with its trigger, and the screen going off and on while minute mode runs. */
    @Test
    fun theLogRecordsSyncsAndTheScreen() {
        DebugLog.clear(context)
        useAppLanguage(context, "en")
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0, liveEnabled = true, liveMinutes = 10, liveShowValue = true, liveKeepAwake = false))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForNotification { it.contains(LiveSyncService.CHANNEL_LIVE) && it.contains(formatGlucose(115.0, GlucoseUnit.MMOL)) }
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
        Thread.sleep(2000)
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        val deadline = SystemClock.uptimeMillis() + 10_000
        fun texts() = DebugLog.read(context).map { "${it.kind} ${it.text}" }
        while (texts().none { it == "phone screen on" } && SystemClock.uptimeMillis() < deadline) Thread.sleep(250)
        val log = texts()
        val all = log.joinToString(" | ")
        assertTrue(all, log.any { it.startsWith("service minute mode on") })
        assertTrue(all, log.any { it.startsWith("sync minute, first:") && it.contains("wrote") })
        assertTrue(all, "phone screen off" in log && "phone screen on" in log)
    }

    private fun smallIcon(): Icon =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .first { it.id == LiveSyncService.NOTIFICATION_ID }.notification.smallIcon

    /** A swipe on Android 14+ removes the notification and sends the service "dismissed"; pinned brings it right back. */
    @Test
    fun pinnedNotificationComesBackAfterASwipe() {
        useAppLanguage(context, "en")
        SettingsStore(context).save(
            AppSettings(backgroundMinutes = 0, liveEnabled = true, liveMinutes = 10, liveShowValue = true, liveKeepAwake = false, livePinned = true)
        )
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForNotification { it.contains(LiveSyncService.CHANNEL_LIVE) && it.contains(formatGlucose(115.0, GlucoseUnit.MMOL)) }
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()

        swipe()
        cloud.tick(60, 150.0)
        runBlocking { syncAndRecord(context) }
        waitForNotification(timeoutMs = 10_000) { it.contains(formatGlucose(150.0, GlucoseUnit.MMOL)) }
    }

    @Test
    fun unpinnedNotificationStaysHiddenAfterASwipe() {
        useAppLanguage(context, "en")
        SettingsStore(context).save(
            AppSettings(backgroundMinutes = 0, liveEnabled = true, liveMinutes = 10, liveShowValue = true, liveKeepAwake = false, livePinned = false)
        )
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForNotification { it.contains(LiveSyncService.CHANNEL_LIVE) && it.contains(formatGlucose(115.0, GlucoseUnit.MMOL)) }
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()

        swipe()
        cloud.tick(60, 150.0)
        runBlocking { syncAndRecord(context) }
        Thread.sleep(5000)
        val manager = context.getSystemService(NotificationManager::class.java)
        val texts = manager.activeNotifications.map { "${it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)}" }
        assertTrue(texts.toString(), texts.none { it.contains(formatGlucose(150.0, GlucoseUnit.MMOL)) })
    }

    /** What the system sends to the service when the user swipes the notification away. */
    private fun swipe() {
        context.startService(Intent(context, LiveSyncService::class.java).setAction(LiveSyncService.ACTION_DISMISSED))
        Thread.sleep(1000)
    }

    /** Waits for the service notification and returns "channel | title | text". */
    private fun waitForNotification(timeoutMs: Long = 30_000, matches: (String) -> Boolean): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var last: String? = null
        while (SystemClock.uptimeMillis() < deadline) {
            last = manager.activeNotifications.map {
                val extras = it.notification.extras
                "${it.notification.channelId} | ${extras.getCharSequence(Notification.EXTRA_TITLE)} | ${extras.getCharSequence(Notification.EXTRA_TEXT)}"
            }.firstOrNull(matches) ?: last
            if (last != null && matches(last)) return last
            Thread.sleep(250)
        }
        throw AssertionError("no matching notification; the last one: $last")
    }

    private fun screenshotOfShade(language: String, name: String) {
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
        Thread.sleep(2000)
        saveScreenshot(language, name)
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
        Thread.sleep(500)
    }

    private fun saveScreenshot(language: String, name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(context.filesDir, "shots/$language").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
