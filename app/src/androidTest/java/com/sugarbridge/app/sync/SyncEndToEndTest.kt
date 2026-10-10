package com.sugarbridge.app.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sugarbridge.app.MainActivity
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.WRITE_GLUCOSE
import com.sugarbridge.app.health.deleteOwnGlucose
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.health.ownMinuteKeys
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
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
 * Вся цепочка на устройстве: поддельный LibreLinkUp → синхронизация → Health Connect,
 * и минутный режим с сахаром в уведомлении.
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
        assumeTrue("Health Connect недоступен", healthConnectState(context) == HealthConnectState.AVAILABLE)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, WRITE_GLUCOSE)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        cloud = FakeCloud(start = Instant.now().minusSeconds(180))   // последний замер — 3,5 минуты назад
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
    }

    @Test
    fun everyReadingIsWrittenOnce() = runBlocking {
        val first = syncOnce(context)
        assertEquals(49, first.written)   // 48 точек истории + текущий замер
        assertEquals(0, syncOnce(context).written)

        cloud.tick(60, 120.0)   // через минуту — новый замер
        val third = syncOnce(context)
        assertEquals(1, third.written)
        assertEquals(120.0, third.latest!!.mgDl, 0.0)
        assertEquals(50, ownMinuteKeys(context, from, to).size)

        val snapshot = SyncStore(context).snapshot()
        assertEquals(1, snapshot.lastWritten)
        assertEquals(50, snapshot.recent.size)

        // часы датчика спешат: замер «из будущего» Health Connect отверг бы вместе со всей пачкой
        cloud.tick(600, 130.0)
        val fourth = syncOnce(context)
        assertEquals(0, fourth.written)
        assertEquals(130.0, fourth.latest!!.mgDl, 0.0)   // на экране он всё равно виден
    }

    @Test
    fun liveModeShowsGlucoseThenGoesQuiet() {
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0, liveEnabled = true, liveShowValue = true, liveKeepAwake = false))
        // приложение на экране — Android разрешает запустить сервис; MainActivity запустит его сама
        // без ActivityScenario: он ждёт простоя главного потока, а анимации на эмуляторе не дают его дождаться
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        val withValue = waitForNotification { it.contains(LiveSyncService.CHANNEL_LIVE) && it.contains("ммоль/л") }
        assertTrue(withValue, withValue.contains(formatGlucose(115.0, GlucoseUnit.MMOL)))
        Thread.sleep(1500)
        saveScreenshot("statusbar")   // сахар числом в строке состояния
        screenshotOfShade("notification")

        SettingsStore(context).update { it.copy(liveShowValue = false) }
        LiveSyncService.reload(context)
        val quiet = waitForNotification { it.contains(LiveSyncService.CHANNEL_QUIET) }
        assertTrue(quiet, quiet.contains("присматривает"))

        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
    }

    /** Ждёт уведомление сервиса и отдаёт «канал | заголовок | текст». */
    private fun waitForNotification(matches: (String) -> Boolean): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = SystemClock.uptimeMillis() + 30_000
        var last: String? = null
        while (SystemClock.uptimeMillis() < deadline) {
            last = manager.activeNotifications.map {
                val extras = it.notification.extras
                "${it.notification.channelId} | ${extras.getCharSequence(Notification.EXTRA_TITLE)} | ${extras.getCharSequence(Notification.EXTRA_TEXT)}"
            }.firstOrNull(matches) ?: last
            if (last != null && matches(last)) return last
            Thread.sleep(250)
        }
        throw AssertionError("нет нужного уведомления; последнее: $last")
    }

    private fun screenshotOfShade(name: String) {
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").close()
        Thread.sleep(2000)
        saveScreenshot(name)
        instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").close()
        Thread.sleep(500)
    }

    private fun saveScreenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
