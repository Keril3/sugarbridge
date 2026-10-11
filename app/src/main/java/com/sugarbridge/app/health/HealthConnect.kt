package com.sugarbridge.app.health

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.sync.minuteKey
import com.sugarbridge.app.sync.recordIdFor
import java.time.Instant
import java.time.ZoneId

const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"

val WRITE_GLUCOSE = HealthPermission.getWritePermission(BloodGlucoseRecord::class)

enum class HealthConnectState { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

/**
 * On Android 14+ Health Connect is part of the system and almost always available. The other states cover
 * a disabled or outdated module: without Health Connect the data has nowhere to go.
 */
fun healthConnectState(context: Context): HealthConnectState = when (HealthConnectClient.getSdkStatus(context)) {
    HealthConnectClient.SDK_AVAILABLE -> HealthConnectState.AVAILABLE
    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectState.UPDATE_REQUIRED
    else -> HealthConnectState.UNAVAILABLE
}

/** Health Connect in Google Play or, without Play, the same page in a browser. */
fun healthConnectStoreIntent(context: Context): Intent {
    val market = Intent(
        Intent.ACTION_VIEW,
        "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding".toUri(),
    ).setPackage("com.android.vending")
    return if (market.resolveActivity(context.packageManager) != null) {
        market
    } else {
        Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE".toUri())
    }
}

/** The Health Connect screen with data and permissions. */
fun healthConnectSettingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

fun isInstalled(context: Context, packageName: String): Boolean = try {
    context.packageManager.getPackageInfo(packageName, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

suspend fun hasGlucosePermission(context: Context): Boolean =
    WRITE_GLUCOSE in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

// LibreLinkUp doesn't tell which sensor it is (Libre 1, 2 or 3), so the common name is written
private val LIBRE_DEVICE = Device(type = Device.TYPE_UNKNOWN, manufacturer = "Abbott", model = "FreeStyle Libre")

fun Reading.toRecord() = BloodGlucoseRecord(
    time = time,
    zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
    // an id from the reading's minute: writing the same reading again won't create a duplicate
    metadata = Metadata.autoRecorded(device = LIBRE_DEVICE, clientRecordId = recordIdFor(time)),
    level = BloodGlucose.milligramsPerDeciliter(mgDl),
    specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
)

/**
 * Minutes for which SugarBridge has already written glucose. Health Connect returns an app's own records
 * without the read permission and without the 30-day limit (Android 14+).
 */
suspend fun ownMinuteKeys(context: Context, from: Instant, to: Instant): Set<Long> {
    val keys = HashSet<Long>()
    forEachOwnGlucose(context, from, to) { time, _ -> keys += minuteKey(time) }
    return keys
}

/** All readings SugarBridge wrote to Health Connect within a period: time and mg/dL. */
suspend fun forEachOwnGlucose(context: Context, from: Instant, to: Instant, action: (Instant, Double) -> Unit) {
    val hc = HealthConnectClient.getOrCreate(context)
    var page: String? = null
    do {
        val response = hc.readRecords(
            ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to),
                dataOriginFilter = setOf(DataOrigin(context.packageName)),
                pageSize = 5000,
                pageToken = page,
            )
        )
        response.records.forEach { action(it.time, it.level.inMilligramsPerDeciliter) }
        page = response.pageToken
    } while (page != null)
}

/** Writes in batches: Health Connect takes at most 1000 records at a time. */
suspend fun insertReadings(context: Context, readings: List<Reading>, onProgress: (Int) -> Unit = {}) {
    val hc = HealthConnectClient.getOrCreate(context)
    var done = 0
    for (chunk in readings.chunked(500)) {
        hc.insertRecords(chunk.map { it.toRecord() })
        done += chunk.size
        onProgress(done)
    }
}

/** Deletes glucose within a period. Health Connect only lets SugarBridge delete its own records. */
suspend fun deleteOwnGlucose(context: Context, from: Instant, to: Instant) {
    HealthConnectClient.getOrCreate(context).deleteRecords(BloodGlucoseRecord::class, TimeRangeFilter.between(from, to))
}
