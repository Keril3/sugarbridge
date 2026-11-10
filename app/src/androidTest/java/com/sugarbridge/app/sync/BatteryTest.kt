package com.sugarbridge.app.sync

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.WRITE_GLUCOSE
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.withUpdateOffers
import com.sugarbridge.app.withoutUpdateOffers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.Instant

/**
 * Minute mode and the processor: without battery restrictions it sleeps between polls and an alarm wakes it, also
 * in Doze; with them Android allows no such alarm, and "Keep the phone awake" keeps it awake as it always did.
 */
@RunWith(AndroidJUnit4::class)
class BatteryTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.packageName

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { output ->
            FileInputStream(output.fileDescriptor).use { String(it.readBytes()) }
        }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    @Before
    fun setUp() {
        assumeTrue("Health Connect is not available", healthConnectState(context) == HealthConnectState.AVAILABLE)
        withoutUpdateOffers(context)
        instrumentation.uiAutomation.grantRuntimePermission(app, WRITE_GLUCOSE)
        instrumentation.uiAutomation.grantRuntimePermission(app, Manifest.permission.POST_NOTIFICATIONS)
        Libre.signOut(context)
        Libre.transportForTests = FakeCloud(Instant.now().minusSeconds(40))
        Libre.signIn(context, "battery@example.com", "secret")
        context.getSharedPreferences(SyncStore.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0, liveEnabled = true, liveMinutes = 1, liveKeepAwake = true))
    }

    @After
    fun tearDown() {
        LiveSyncService.stop(context)
        shell("dumpsys deviceidle unforce")
        shell("dumpsys battery reset")
        shell("dumpsys deviceidle whitelist -$app")
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0))
        Libre.signOut(context)
        Libre.transportForTests = null
        context.getSharedPreferences(SyncStore.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        withUpdateOffers(context)
    }

    private fun startAndWaitForFirstPoll() {
        LiveSyncService.start(context)
        assertTrue("no first poll", waitFor(20_000) { SyncStore(context).snapshot().lastOkAt != null })
        Thread.sleep(3_000)   // the round ends: the notification is posted and the next wake-up is set
    }

    private fun wakeLocks() = shell("dumpsys power").lines().filter { "SugarBridge:" in it && "PARTIAL_WAKE_LOCK" in it }

    private fun nextPollComes(): Boolean {
        val before = LiveSyncService.lastTick
        return waitFor(95_000) { LiveSyncService.lastTick != before }
    }

    @Test
    fun withoutRestrictionsTheProcessorMaySleepBetweenPolls() {
        shell("dumpsys deviceidle whitelist +$app")
        startAndWaitForFirstPoll()
        assertTrue("still holding the processor: ${wakeLocks()}", wakeLocks().isEmpty())
        assertTrue("no alarm is set for the next poll", shell("dumpsys alarm").contains("MINUTE_TICK"))
        assertTrue("the alarm didn't bring the next poll", nextPollComes())
    }

    @Test
    fun theAlarmWorksInDozeToo() {
        shell("dumpsys deviceidle whitelist +$app")
        startAndWaitForFirstPoll()
        shell("dumpsys battery unplug")
        assertTrue(shell("dumpsys deviceidle force-idle deep"), shell("dumpsys deviceidle get deep").trim() == "IDLE")
        assertTrue("no poll in Doze", nextPollComes())
    }

    @Test
    fun withRestrictionsKeepAwakeKeepsTheProcessorAwakeAsBefore() {
        shell("dumpsys deviceidle whitelist -$app")
        assumeTrue("exact alarms are allowed on this device", !context.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms())
        startAndWaitForFirstPoll()
        assertTrue(wakeLocks().toString(), wakeLocks().any { "SugarBridge:live" in it })
        assertFalse(shell("dumpsys alarm").contains("MINUTE_TICK"))
    }

    @Test
    fun withoutKeepAwakeNothingHoldsTheProcessor() {
        SettingsStore(context).update { it.copy(liveKeepAwake = false) }
        startAndWaitForFirstPoll()
        assertTrue(wakeLocks().toString(), wakeLocks().isEmpty())
    }
}
