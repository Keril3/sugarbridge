package com.sugarbridge.app.local

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.glucose.MGDL_PER_MMOL
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.WRITE_GLUCOSE
import com.sugarbridge.app.health.deleteOwnGlucose
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.health.ownMinuteKeys
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.sync.LiveSyncService
import com.sugarbridge.app.sync.SyncStore
import com.sugarbridge.app.sync.minuteKey
import com.sugarbridge.app.withUpdateOffers
import com.sugarbridge.app.withoutUpdateOffers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Glucose without the internet: Juggluco's and xDrip+'s broadcasts and LibreLink's notification reach Health
 * Connect and the screens, only while their switch is on, and a reading that can't be right is left out.
 */
@RunWith(AndroidJUnit4::class)
class LocalSourcesTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val from = Instant.now().minus(Duration.ofHours(1))
    private val to = Instant.now().plus(Duration.ofHours(1))

    @Before
    fun setUp() {
        assumeTrue("Health Connect is not available", healthConnectState(context) == HealthConnectState.AVAILABLE)
        withoutUpdateOffers(context)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, WRITE_GLUCOSE)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences(SyncStore.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0))
        LocalReadings.setBroadcasts(context, false)
        DebugLog.clear(context)
        runBlocking { deleteOwnGlucose(context, from, to) }
    }

    @After
    fun tearDown() {
        LiveSyncService.stop(context)
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0))
        LocalReadings.setBroadcasts(context, false)
        runBlocking { deleteOwnGlucose(context, from, to) }
        context.getSharedPreferences(SyncStore.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        withUpdateOffers(context)
    }

    private fun broadcastsOn() {
        SettingsStore(context).update { it.copy(localBroadcasts = true) }
        LocalReadings.setBroadcasts(context, true)
    }

    private fun juggluco(mgDl: Int, at: Instant, rate: Float = 0f) = context.sendBroadcast(
        Intent(LocalGlucose.JUGGLUCO_ACTION).setPackage(context.packageName)
            .putExtra(LocalGlucose.JUGGLUCO_MGDL, mgDl)
            .putExtra(LocalGlucose.JUGGLUCO_TIME, at.toEpochMilli())
            .putExtra(LocalGlucose.JUGGLUCO_RATE, rate)
            .putExtra("glucodata.Minute.SerialNumber", "3MH00TEST")
    )

    private fun waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    private fun written(at: Instant) = runBlocking { minuteKey(at) in ownMinuteKeys(context, from, to) }

    @Test
    fun aJugglucoReadingGoesToHealthConnectAndTheScreens() {
        broadcastsOn()
        val at = Instant.now().minusSeconds(30)
        juggluco(123, at, rate = 1.5f)
        assertTrue("the reading didn't arrive", waitFor { SyncStore(context).snapshot().localReading != null })
        val snapshot = SyncStore(context).snapshot()
        assertEquals(123.0, snapshot.localReading!!.mgDl, 0.0)
        assertEquals(4, snapshot.localReading!!.trend)   // rising
        assertEquals("Juggluco", snapshot.localSource)
        assertEquals(123.0, snapshot.recent.last().mgDl, 0.0)
        assertTrue(written(at))
        assertTrue(DebugLog.read(context).any { it.kind == "local" && it.text.startsWith("Juggluco: 123.0 mg/dL") })
    }

    @Test
    fun anXdripReadingToo() {
        broadcastsOn()
        val at = Instant.now().minusSeconds(40)
        context.sendBroadcast(
            Intent(LocalGlucose.XDRIP_ACTION).setPackage(context.packageName)
                .putExtra(LocalGlucose.XDRIP_MGDL, 98.0)
                .putExtra(LocalGlucose.XDRIP_TIME, at.toEpochMilli())
                .putExtra(LocalGlucose.XDRIP_SLOPE_NAME, "FortyFiveDown")
        )
        assertTrue(waitFor { SyncStore(context).snapshot().localReading != null })
        assertEquals(2, SyncStore(context).snapshot().localReading!!.trend)
        assertEquals("xDrip+", SyncStore(context).snapshot().localSource)
        assertTrue(written(at))
    }

    @Test
    fun withTheSwitchOffNothingComesIn() {
        juggluco(123, Instant.now().minusSeconds(30))   // the receiver is off: Android doesn't even deliver it
        Thread.sleep(2000)
        assertNull(SyncStore(context).snapshot().localReading)
        // on in Android but off in the settings (as after restoring a backup): still nothing
        LocalReadings.setBroadcasts(context, true)
        juggluco(123, Instant.now().minusSeconds(30))
        Thread.sleep(2000)
        assertNull(SyncStore(context).snapshot().localReading)
    }

    @Test
    fun aReadingThatCantBeRightIsLeftOut() {
        broadcastsOn()
        juggluco(5, Instant.now().minusSeconds(30))
        assertTrue(waitFor { DebugLog.read(context).any { it.text.contains("can't be right") } })
        assertNull(SyncStore(context).snapshot().localReading)
    }

    private fun libreNotification(text: String) = Notification.Builder(context, LiveSyncService.CHANNEL_QUIET)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("FreeStyle LibreLink")
        .setContentText(text)
        .setWhen(System.currentTimeMillis() - 30_000)
        .build()

    @Test
    fun theGlucoseInLibreLinksNotificationIsTaken() {
        SettingsStore(context).update { it.copy(localNotification = true) }
        LibreNotifications.handle(context, libreNotification("6.4 mmol/L ↗"), System.currentTimeMillis(), CoroutineScope(Dispatchers.IO))
        assertTrue(waitFor { SyncStore(context).snapshot().localReading != null })
        val reading = SyncStore(context).snapshot().localReading!!
        assertEquals(6.4 * MGDL_PER_MMOL, reading.mgDl, 0.1)
        assertEquals(4, reading.trend)
        assertEquals("LibreLink", SyncStore(context).snapshot().localSource)
    }

    @Test
    fun aNotificationWithoutGlucoseIsNotedOnce() {
        SettingsStore(context).update { it.copy(localNotification = true) }
        repeat(3) { LibreNotifications.handle(context, libreNotification("Sensor is working"), System.currentTimeMillis(), CoroutineScope(Dispatchers.IO)) }
        val snapshot = SyncStore(context).snapshot()
        assertNull(snapshot.localReading)
        assertEquals("FreeStyle LibreLink · Sensor is working", snapshot.localMissText)
        assertEquals(1, DebugLog.read(context).count { it.kind == "local" })   // not every minute
    }

    @Test
    fun withoutItsSwitchLibreLinksNotificationIsIgnored() {
        LibreNotifications.handle(context, libreNotification("6.4 mmol/L"), System.currentTimeMillis(), CoroutineScope(Dispatchers.IO))
        Thread.sleep(1500)
        assertNull(SyncStore(context).snapshot().localReading)
        assertNull(SyncStore(context).snapshot().localMissText)
    }

    @Test
    fun onlyAbbottsAppsAreRead() {
        assertTrue(LibreNotifications.isAbbott("com.freestylelibre.app.ru"))
        assertTrue(LibreNotifications.isAbbott("com.freestylelibre3.app"))
        assertFalse(LibreNotifications.isAbbott("org.telegram.messenger"))
        assertFalse(LibreNotifications.isAbbott(context.packageName))
    }

    @Test
    fun notificationAccessIsSeen() {
        val component = LibreNotificationListener.component(context).flattenToString()
        try {
            instrumentation.uiAutomation.executeShellCommand("cmd notification allow_listener $component").close()
            assertTrue(waitFor { LibreNotificationListener.hasAccess(context) })
        } finally {
            instrumentation.uiAutomation.executeShellCommand("cmd notification disallow_listener $component").close()
        }
        assertTrue(waitFor { !LibreNotificationListener.hasAccess(context) })
    }
}
