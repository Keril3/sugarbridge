package com.sugarbridge.app.sync

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.sugarbridge.app.MainActivity
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.WRITE_GLUCOSE
import com.sugarbridge.app.health.deleteOwnGlucose
import com.sugarbridge.app.health.forEachOwnGlucose
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.libre.HttpResponse
import com.sugarbridge.app.libre.HttpTransport
import com.sugarbridge.app.libre.LIBRE_TIME
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking

/**
 * How long a new glucose value takes to reach Health Connect in minute mode, measured in real time.
 * The fake cloud publishes a value every minute, [CLOUD_LAG] after its sensor time, the way LibreLinkUp
 * shows a reading some seconds after the sensor took it. The test watches Health Connect and records when
 * each value shows up there. The report goes to logcat (tag Latency) and to files/latency.txt.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LatencyTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val started = Instant.now()

    /** A cloud that runs on the real clock: every minute brings a new current value. */
    private class RealTimeCloud : HttpTransport {
        override fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse {
            val newest = latestPublished(Instant.now())
            val data: Any = when (URI(url).path) {
                "/llu/auth/login" -> JSONObject()
                    .put("user", JSONObject().put("id", "latency"))
                    .put("authTicket", JSONObject().put("token", "LATENCY"))
                "/llu/connections" -> JSONArray().put(JSONObject().put("patientId", "p-1"))
                "/llu/connections/p-1/graph" -> JSONObject()
                    .put("connection", JSONObject().put("glucoseMeasurement", item(newest).put("TrendArrow", 3)))
                    .put("graphData", JSONArray().apply {
                        // the history has a point every 15 minutes, like the real one
                        val quarter = newest.epochSecond / 900 * 900
                        (48 downTo 1).forEach { put(item(Instant.ofEpochSecond(quarter - it * 900L))) }
                    })
                else -> return HttpResponse(404, "")
            }
            return HttpResponse(200, JSONObject().put("status", 0).put("data", data).toString())
        }

        private fun item(time: Instant) = JSONObject()
            .put("FactoryTimestamp", LocalDateTime.ofInstant(time, ZoneOffset.UTC).format(LIBRE_TIME))
            .put("ValueInMgPerDl", 100 + (time.epochSecond / 60 % 40))
    }

    @Before
    fun setUp() {
        assumeTrue("Health Connect is not available", healthConnectState(context) == HealthConnectState.AVAILABLE)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, WRITE_GLUCOSE)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        Libre.signOut(context)
        Libre.transportForTests = RealTimeCloud()
        Libre.signIn(context, "latency@example.com", "secret")
        context.getSharedPreferences(SyncStore.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking { deleteOwnGlucose(context, started.minus(Duration.ofHours(14)), started.plus(Duration.ofHours(1))) }
    }

    @After
    fun tearDown() {
        LiveSyncService.stop(context)
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0))
        runBlocking { deleteOwnGlucose(context, started.minus(Duration.ofHours(14)), started.plus(Duration.ofHours(1))) }
        Libre.signOut(context)
        Libre.transportForTests = null
        context.getSharedPreferences(SyncStore.NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun minuteModeDeliversEveryValueWithinAMinute() = measure(doze = false)

    /** The screen is off and the phone is in deep Doze: the foreground service and its wake lock keep it going. */
    @Test
    fun minuteModeKeepsDeliveringWhileDozing() = measure(doze = true)

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()

    private fun measure(doze: Boolean) {
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0, liveEnabled = true, liveMinutes = 1, liveKeepAwake = true))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(3000)   // MainActivity starts the service
        shell("input keyevent KEYCODE_HOME")
        if (doze) {
            shell("dumpsys battery unplug")
            shell("input keyevent KEYCODE_SLEEP")
            shell("dumpsys deviceidle force-idle")
        }
        val seen = LinkedHashMap<Instant, Instant>()   // reading time → when it appeared in Health Connect
        try {
            val end = SystemClock.uptimeMillis() + RUN.toMillis()
            while (SystemClock.uptimeMillis() < end) {
                val now = Instant.now()
                runBlocking {
                    forEachOwnGlucose(context, started.minus(Duration.ofMinutes(5)), now.plusSeconds(60)) { time, _ ->
                        if (time.isAfter(started) && time !in seen) seen[time] = now
                    }
                }
                Thread.sleep(2000)
            }
        } finally {
            if (doze) {
                shell("dumpsys deviceidle unforce")
                shell("dumpsys battery reset")
                shell("input keyevent KEYCODE_WAKEUP")
            }
        }

        // the delay that SugarBridge adds: from the moment the cloud published a value until it is in Health Connect
        val delays = seen.map { (time, appeared) -> Duration.between(time.plus(CLOUD_LAG), appeared).seconds }
        val report = buildString {
            appendLine("minute mode${if (doze) " in deep Doze" else ""}, poll every 1 min, cloud lag ${CLOUD_LAG.seconds} s, run ${RUN.toMinutes()} min")
            seen.forEach { (time, appeared) ->
                appendLine("reading $time → in Health Connect at $appeared (+${Duration.between(time.plus(CLOUD_LAG), appeared).seconds} s after the cloud had it)")
            }
            appendLine("values: ${seen.size}, SugarBridge delay s: min ${delays.minOrNull()} median ${delays.sorted().getOrNull(delays.size / 2)} max ${delays.maxOrNull()}")
        }
        report.lines().forEach { Log.i("Latency", it) }
        File(context.filesDir, if (doze) "latency_doze.txt" else "latency.txt").writeText(report)

        // RUN minutes bring at least RUN - 1 new values, and none may wait longer than a poll interval plus slack
        assertTrue(report, seen.size >= RUN.toMinutes() - 1)
        assertTrue(report, delays.all { it <= 75 })
    }

    companion object {
        private val CLOUD_LAG: Duration = Duration.ofSeconds(20)
        private val RUN: Duration = Duration.ofMinutes(5)

        /** The newest value the cloud has at [now]: sensor values every whole minute, visible [CLOUD_LAG] later. */
        fun latestPublished(now: Instant): Instant = Instant.ofEpochSecond(now.minus(CLOUD_LAG).epochSecond / 60 * 60)
    }
}
