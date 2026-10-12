package com.sugarbridge.app.update

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sugarbridge.app.libre.HttpResponse
import com.sugarbridge.app.libre.HttpTransport
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Offering a new version: the background check posts the notification once per version and only with both
 * switches on, the window's answer stops it, the daily check follows the switches, and GitHub's failures are
 * GitHub's. GitHub is a stand-in that has version 99.0.0.
 */
@RunWith(AndroidJUnit4::class)
class UpdateOfferTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var answer = HttpResponse(200, release("99.0.0"))

    private fun release(version: String) = """
        {"tag_name": "v$version", "html_url": "https://github.com/$GITHUB_REPO/releases/tag/v$version", "body": "notes",
         "assets": [{"name": "$APK_NAME", "browser_download_url": "https://github.com/$GITHUB_REPO/releases/download/v$version/$APK_NAME"}]}
    """.trimIndent()

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("update", Context.MODE_PRIVATE).edit().clear().commit()   // no 6-hour pause, nothing offered
        Updater.transportForTests = HttpTransport { _, _, _, _ -> answer }
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0))
        manager.cancel(UpdateOffer.NOTIFICATION_ID)
    }

    @After
    fun tearDown() {
        Updater.transportForTests = null
        context.getSharedPreferences("update", Context.MODE_PRIVATE).edit().clear().commit()
        manager.cancel(UpdateOffer.NOTIFICATION_ID)
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0))
        scheduleUpdateChecks(context)
    }

    private fun offered(): Notification? = manager.activeNotifications.firstOrNull { it.id == UpdateOffer.NOTIFICATION_ID }?.notification

    /** Android posts and cancels notifications a moment later: wait up to 3 seconds for [what]. */
    private fun eventually(what: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 3_000
        while (!condition()) {
            if (SystemClock.uptimeMillis() > deadline) fail(what)
            Thread.sleep(100)
        }
    }

    /** Nothing is posted: checked after the moment Android would need to post it. */
    private fun staysAway(what: String) {
        Thread.sleep(1_000)
        assertTrue(what, offered() == null)
    }

    @Test
    fun aNewVersionIsOfferedInANotificationOnce() {
        UpdateOffer.checkInBackground(context)
        eventually("no notification") { offered() != null }
        val notification = offered()
        assertTrue(notification!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("99.0.0"))
        assertEquals(1, notification.actions.size)   // "Download and install"

        manager.cancel(UpdateOffer.NOTIFICATION_ID)   // swiped away: not posted again for this version
        eventually("not cancelled") { offered() == null }
        context.getSharedPreferences("update", Context.MODE_PRIVATE).edit().remove("checked_at").commit()
        UpdateOffer.checkInBackground(context)
        staysAway("offered twice")

        answer = HttpResponse(200, release("99.1.0"))   // the next version is offered again
        context.getSharedPreferences("update", Context.MODE_PRIVATE).edit().remove("checked_at").commit()
        UpdateOffer.checkInBackground(context)
        eventually("the next version not offered") { offered()?.extras?.getCharSequence(Notification.EXTRA_TITLE).toString().contains("99.1.0") }
    }

    @Test
    fun theSwitchesTurnTheOfferOff() {
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0, updatePrompt = false))
        UpdateOffer.checkInBackground(context)
        staysAway("offered with the offer off")
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0, autoUpdateCheck = false))
        UpdateOffer.checkInBackground(context)
        staysAway("offered with the check off")
        assertFalse(UpdateOffer.shouldOffer(context, AppSettings(autoUpdateCheck = false), Updater.check(context, force = true)!!))
    }

    @Test
    fun answeringTheWindowEndsTheOffer() {
        UpdateOffer.checkInBackground(context)
        eventually("no notification") { offered() != null }
        val release = Updater.known(context)!!
        assertTrue(UpdateOffer.shouldOffer(context, AppSettings(), release))
        UpdateOffer.markOffered(context, release.version)
        eventually("the notification stays after the answer") { offered() == null }
        assertFalse(UpdateOffer.shouldOffer(context, AppSettings(), release))
    }

    @Test
    fun theDailyCheckFollowsTheSwitches() {
        fun state() = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UpdateCheckWorker.WORK_NAME).get().lastOrNull()?.state
        scheduleUpdateChecks(context, AppSettings())
        assertEquals(WorkInfo.State.ENQUEUED, state())
        scheduleUpdateChecks(context, AppSettings(updatePrompt = false))
        assertEquals(WorkInfo.State.CANCELLED, state())
        scheduleUpdateChecks(context, AppSettings())
        assertEquals(WorkInfo.State.ENQUEUED, state())
        scheduleUpdateChecks(context, AppSettings(autoUpdateCheck = false))
        assertEquals(WorkInfo.State.CANCELLED, state())
    }

    @Test
    fun gitHubsFailuresNameGitHub() {
        answer = HttpResponse(503, "")
        try {
            Updater.check(context, force = true)
            fail("a failure must not pass for no update")
        } catch (e: GitHubError) {
            assertEquals(503, e.code)
            assertTrue(e.message!!, e.message!!.contains("GitHub"))
        }
    }
}
