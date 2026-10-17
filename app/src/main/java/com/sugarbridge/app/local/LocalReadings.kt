package com.sugarbridge.app.local

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.WRITE_GLUCOSE
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.health.toRecord
import com.sugarbridge.app.libreview.HistoryStore
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.sync.SyncStore
import com.sugarbridge.app.sync.withSyncLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException

/**
 * A reading from an app on this phone goes the way a synced one does, without the internet: to Health Connect
 * (each minute once, whoever brought it), onto the screens and into the notification, into the kept history if
 * it grows by itself, and into the event log.
 */
object LocalReadings {

    private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** Returns whether it was new to Health Connect. */
    suspend fun receive(context: Context, reading: Reading, source: String): Boolean = withSyncLock {
        val at = reading.time.atZone(ZoneId.systemDefault()).format(CLOCK)
        try {
            val now = Instant.now()
            val store = SyncStore(context)
            val state = store.state()
            val fresh = state.fresh(listOf(reading), now)
            if (fresh.isNotEmpty()) {
                if (healthConnectState(context) != HealthConnectState.AVAILABLE) error("Health Connect is not available")
                val hc = HealthConnectClient.getOrCreate(context)
                if (WRITE_GLUCOSE !in hc.permissionController.getGrantedPermissions()) error("no permission to write glucose")
                hc.insertRecords(fresh.map { it.toRecord() })
                store.saveState(state.after(fresh, now))
            }
            store.saveLocal(reading, source, now)   // the screens and minute mode's notification redraw on it
            val settings = SettingsStore(context).load()
            if (settings.historyAutoAdd) runCatching { HistoryStore.append(context, listOf(reading), settings.unit, source) }
            DebugLog.add(context, "local", "$source: ${reading.mgDl} mg/dL at $at; wrote ${fresh.size}")
            fresh.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.add(context, "error", "$source: ${reading.mgDl} mg/dL at $at not written: ${e.message ?: e.javaClass.simpleName}")
            false
        }
    }

    /** Juggluco and xDrip+ can reach SugarBridge only while this is on: Android keeps the receiver off otherwise. */
    fun setBroadcasts(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, LocalGlucoseReceiver::class.java),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
