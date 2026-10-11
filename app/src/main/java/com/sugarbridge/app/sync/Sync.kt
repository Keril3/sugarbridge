package com.sugarbridge.app.sync

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.WRITE_GLUCOSE
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.health.toRecord
import com.sugarbridge.app.libre.Libre
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The outcome of one sync: how many were written, the latest reading, all readings for 12 hours and the timings. */
class SyncResult(
    val written: Int,
    val latest: Reading?,
    val readings: List<Reading>,
    val fetchMillis: Long = 0,   // the LibreLinkUp request
    val writeMillis: Long = 0,   // the Health Connect insert
)

/** The background job, the minute service and the button on screen must not sync at the same time. */
private val syncMutex = Mutex()

/** Fetches readings from LibreLinkUp and writes the ones Health Connect doesn't have yet. */
suspend fun syncOnce(context: Context): SyncResult = syncMutex.withLock {
    if (healthConnectState(context) != HealthConnectState.AVAILABLE) throw SyncBlocked(Problem.Code.NO_HEALTH_CONNECT)
    val hc = HealthConnectClient.getOrCreate(context)
    if (WRITE_GLUCOSE !in hc.permissionController.getGrantedPermissions()) throw SyncBlocked(Problem.Code.NO_PERMISSION)
    val libre = Libre.client(context) ?: throw SyncBlocked(Problem.Code.SIGNED_OUT)
    val fetchStart = SystemClock.elapsedRealtime()
    val readings = withContext(Dispatchers.IO) { libre.readings() }
    val writeStart = SystemClock.elapsedRealtime()

    val store = SyncStore(context)
    val state = store.state()
    val now = Instant.now()
    val fresh = state.fresh(readings, now)
    if (fresh.isNotEmpty()) {
        // 12 hours hold far fewer than 1000 readings, the Health Connect limit for one insert
        hc.insertRecords(fresh.map { it.toRecord() })
    }
    // the order matters: first the records, then the state; if it fails in between, the record ids make a retry safe
    store.saveState(state.after(fresh, now))
    store.saveSuccess(readings, fresh.size)
    val done = SystemClock.elapsedRealtime()
    SyncResult(fresh.size, readings.lastOrNull(), readings, writeStart - fetchStart, done - writeStart)
}

/**
 * The same, but a failure is remembered so the home screen can show it, and every run goes to the event log
 * with its [trigger]: the minute mode, the background job, the app being opened or the button.
 */
suspend fun syncAndRecord(context: Context, trigger: String = "other"): SyncResult = try {
    syncOnce(context).also { DebugLog.add(context, "sync", describe(trigger, it)) }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    val problem = Problem.of(e)
    SyncStore(context).saveError(problem)
    DebugLog.add(context, "error", "$trigger: ${problem.code} ${problem.detail.orEmpty()}".trim())
    throw e
}

/** "minute: newest 18:05:00, 64 s old; 48 points, wrote 1 (18:05); LibreLinkUp 610 ms, Health Connect 85 ms". */
private fun describe(trigger: String, r: SyncResult, now: Instant = Instant.now()): String {
    val zone = ZoneId.systemDefault()
    val clock = DateTimeFormatter.ofPattern("HH:mm:ss")
    val newest = r.latest?.let { "newest ${it.time.atZone(zone).format(clock)}, ${Duration.between(it.time, now).seconds} s old" } ?: "no readings"
    return "$trigger: $newest; ${r.readings.size} points, wrote ${r.written}; LibreLinkUp ${r.fetchMillis} ms, Health Connect ${r.writeMillis} ms"
}

/** What the home screen shows: the latest readings, the last successful sync and the error. */
data class SyncSnapshot(
    val recent: List<Reading> = emptyList(),
    val lastOkAt: Instant? = null,
    val lastWritten: Int = 0,
    val error: Problem? = null,
    val errorAt: Instant? = null,
    val updateMinutes: Double? = null,   // how often LibreLinkUp actually delivers a new value
    val deliveryMinutes: Double? = null, // how long after the sensor reading a new value reaches SugarBridge
)

class SyncStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun state(): SyncState = SyncState(
        written = decodeKeys(prefs.getString("written", null)),
        // last_synced is left from version 1.0.0: everything not newer than it was written with the old ids
        floor = prefs.getLong("floor", prefs.getLong("last_synced", 0)),
    )

    fun saveState(state: SyncState) {
        prefs.edit { putString("written", encodeKeys(state.written)).putLong("floor", state.floor) }
    }

    /** After deleting from Health Connect these minutes can be written again. */
    fun forget(from: Instant, to: Instant) = saveState(state().forget(from, to))

    fun saveSuccess(readings: List<Reading>, written: Int, now: Instant = Instant.now()) {
        val before = seen()
        val newest = readings.lastOrNull()
        val seen = (before + listOfNotNull(newest?.time?.epochSecond)).distinct().takeLast(16)
        // the first time a value shows up: how old it already was (LibreLink upload, the cloud, our polling)
        val lags = lags().toMutableList()
        if (newest != null && newest.time.epochSecond !in before) {
            val lag = Duration.between(newest.time, now).seconds
            if (lag in 0..MAX_LAG_SECONDS) lags += lag
        }
        prefs.edit {
            putString("lags", lags.takeLast(16).joinToString(","))
            putString("recent", encodeReadings(readings))
            putLong(KEY_OK_AT, System.currentTimeMillis())
            putInt("ok_written", written)
            putString("seen", seen.joinToString(","))
            remove("error")
            remove("error_at")
        }
    }

    fun saveError(problem: Problem) {
        prefs.edit { putString("error", problem.encode()).putLong("error_at", System.currentTimeMillis()) }
    }

    /** On sign-out: the readings and errors on screen are no longer needed, the written minutes are kept. */
    fun clearDisplay() {
        prefs.edit {
            listOf("recent", "ok_at", "ok_written", "seen", "lags", "error", "error_at").forEach { remove(it) }
        }
    }

    fun snapshot() = SyncSnapshot(
        recent = decodeReadings(prefs.getString("recent", null)),
        lastOkAt = prefs.getLong(KEY_OK_AT, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        lastWritten = prefs.getInt("ok_written", 0),
        error = prefs.getString("error", null)?.let(Problem::decode),
        errorAt = prefs.getLong("error_at", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        updateMinutes = typicalUpdateMinutes(seen().map(Instant::ofEpochSecond)),
        deliveryMinutes = medianMinutes(lags()),
    )

    private fun seen(): List<Long> = prefs.getString("seen", null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    private fun lags(): List<Long> = prefs.getString("lags", null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    companion object {
        const val NAME = "sync"
        /** Changes on every successful sync, whoever made it. */
        const val KEY_OK_AT = "ok_at"
        private const val MAX_LAG_SECONDS = 30 * 60L   // older values come from catching up, not from a delay
    }
}
