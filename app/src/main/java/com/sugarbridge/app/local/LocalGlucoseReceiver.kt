package com.sugarbridge.app.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Juggluco's and xDrip+'s readings, sent to SugarBridge on the phone itself. Android starts it only while
 * "From Juggluco or xDrip+" is on (see [LocalReadings.setBroadcasts]): any app may send these broadcasts, so it
 * is off unless asked for, and a reading that can't be right is dropped.
 */
class LocalGlucoseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!SettingsStore(context).load().localBroadcasts) return
        val extras = intent.extras ?: return
        val (source, reading) = when (intent.action) {
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
            else -> return
        }
        if (reading == null) {
            DebugLog.add(context, "local", "$source: a reading that can't be right, left out")
            return
        }
        val pending = goAsync()   // writing to Health Connect takes longer than a receiver may run
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LocalReadings.receive(context.applicationContext, reading, source)
            } finally {
                pending.finish()
            }
        }
    }
}
