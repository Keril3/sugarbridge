package com.sugarbridge.app.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Juggluco's and xDrip+'s readings, sent to SugarBridge on the phone itself. Android starts it only while
 * "From Juggluco or xDrip+" is on (see [LocalReadings.setBroadcasts]): any app may send these broadcasts, so it
 * is off unless asked for, a reading that can't be right is dropped, and a flood is taken one reading at a time.
 */
class LocalGlucoseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!SettingsStore(context).load().localBroadcasts) return
        // these apps send once a minute at most: anything faster would only wear out the storage and the battery
        val now = SystemClock.elapsedRealtime()
        val last = lastAt
        if (last != null && now - last < MIN_GAP_MS) return
        lastAt = now
        val read = try {
            read(intent)
        } catch (e: RuntimeException) {   // extras that can't even be unpacked: another app's mistake or trick
            DebugLog.add(context, "local", "a broadcast that can't be read, left out")
            return
        }
        val (source, reading) = read ?: return
        if (reading == null) {
            DebugLog.add(context, "local", "$source: a reading that can't be right, left out")
            return
        }
        val pending = goAsync()   // writing to Health Connect takes longer than a receiver may run
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Android allows a background broadcast a minute: whatever happens, it is finished in time
                withTimeoutOrNull(MAX_WORK_MS) { LocalReadings.receive(context.applicationContext, reading, source) }
            } finally {
                pending.finish()
            }
        }
    }

    /** Which app sent it and its reading (null if it can't be right); null for any other broadcast. */
    private fun read(intent: Intent): Pair<String, Reading?>? {
        val extras = intent.extras ?: return null
        return when (intent.action) {
            LocalGlucose.JUGGLUCO_ACTION -> "Juggluco" to LocalGlucose.reading(
                extras.getInt(LocalGlucose.JUGGLUCO_MGDL, 0).toDouble(),
                extras.getLong(LocalGlucose.JUGGLUCO_TIME, 0),
                LocalGlucose.trendOfRate(extras.getFloat(LocalGlucose.JUGGLUCO_RATE, Float.NaN)),
            )
            LocalGlucose.XDRIP_ACTION -> "xDrip+" to LocalGlucose.reading(
                extras.getDouble(LocalGlucose.XDRIP_MGDL, Double.NaN),
                extras.getLong(LocalGlucose.XDRIP_TIME, 0),
                LocalGlucose.trendOfName(extras.getString(LocalGlucose.XDRIP_SLOPE_NAME)),
            )
            else -> null
        }
    }

    companion object {
        private const val MIN_GAP_MS = 10_000L
        private const val MAX_WORK_MS = 50_000L
        private var lastAt: Long? = null   // only the main thread comes here

        /** For tests: the next broadcast is taken whenever it comes. */
        internal fun forgetLast() {
            lastAt = null
        }
    }
}
