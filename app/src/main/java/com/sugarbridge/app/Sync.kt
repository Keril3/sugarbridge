package com.sugarbridge.app

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

val WRITE_GLUCOSE = HealthPermission.getWritePermission(BloodGlucoseRecord::class)

private val LIBRE_DEVICE = Device(type = Device.TYPE_UNKNOWN, manufacturer = "Abbott", model = "FreeStyle Libre 2")

fun Reading.toRecord() = BloodGlucoseRecord(
    time = time,
    zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
    // ключ из времени замера: повторная запись того же замера не создаст дубль
    metadata = Metadata.autoRecorded(device = LIBRE_DEVICE, clientRecordId = "libre-${time.epochSecond}"),
    level = BloodGlucose.milligramsPerDeciliter(mgDl),
    specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
)

/** Итог одной синхронизации: сколько записано и последний замер (для экрана и уведомления). */
class SyncResult(val written: Int, val latest: Reading?)

/** Забирает замеры из LibreLinkUp и пишет в Health Connect те, что новее прошлой синхронизации. */
suspend fun syncOnce(context: Context): SyncResult {
    val hc = HealthConnectClient.getOrCreate(context)
    check(WRITE_GLUCOSE in hc.permissionController.getGrantedPermissions()) {
        "Нет разрешения на запись сахара в Health Connect"
    }
    val libre = Libre.client(context) ?: throw IllegalStateException("Сначала войдите в LibreLinkUp")
    val readings = withContext(Dispatchers.IO) { libre.readings() }

    val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    val lastSynced = prefs.getLong("last_synced", 0)
    val fresh = readings.filter { it.time.epochSecond > lastSynced }
    if (fresh.isNotEmpty()) {
        // за 12 часов замеров заметно меньше 1000 — это лимит Health Connect на одну запись
        hc.insertRecords(fresh.map { it.toRecord() })
        prefs.edit().putLong("last_synced", fresh.last().time.epochSecond).apply()
    }
    return SyncResult(fresh.size, readings.lastOrNull())
}
