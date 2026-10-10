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
 * На Android 14+ Health Connect встроен в систему и почти всегда доступен. Остальные состояния —
 * на случай отключённого или устаревшего модуля: без Health Connect данным некуда идти.
 */
fun healthConnectState(context: Context): HealthConnectState = when (HealthConnectClient.getSdkStatus(context)) {
    HealthConnectClient.SDK_AVAILABLE -> HealthConnectState.AVAILABLE
    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectState.UPDATE_REQUIRED
    else -> HealthConnectState.UNAVAILABLE
}

/** Health Connect в Google Play, а если Play нет — та же страница в браузере. */
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

/** Экран Health Connect с данными и разрешениями. */
fun healthConnectSettingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)

fun isInstalled(context: Context, packageName: String): Boolean = try {
    context.packageManager.getPackageInfo(packageName, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

suspend fun hasGlucosePermission(context: Context): Boolean =
    WRITE_GLUCOSE in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

// какой именно датчик (Libre 1, 2 или 3), LibreLinkUp не сообщает — пишем общее название
private val LIBRE_DEVICE = Device(type = Device.TYPE_UNKNOWN, manufacturer = "Abbott", model = "FreeStyle Libre")

fun Reading.toRecord() = BloodGlucoseRecord(
    time = time,
    zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
    // ключ из минуты замера: повторная запись того же замера не создаст дубль
    metadata = Metadata.autoRecorded(device = LIBRE_DEVICE, clientRecordId = recordIdFor(time)),
    level = BloodGlucose.milligramsPerDeciliter(mgDl),
    specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
)

/**
 * Минуты, на которые SugarBridge уже записал сахар. Свои записи Health Connect отдаёт
 * без разрешения на чтение и без ограничения в 30 дней (Android 14+).
 */
suspend fun ownMinuteKeys(context: Context, from: Instant, to: Instant): Set<Long> {
    val hc = HealthConnectClient.getOrCreate(context)
    val keys = HashSet<Long>()
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
        response.records.mapTo(keys) { minuteKey(it.time) }
        page = response.pageToken
    } while (page != null)
    return keys
}

/** Запись пачками: Health Connect принимает не больше 1000 записей за раз. */
suspend fun insertReadings(context: Context, readings: List<Reading>, onProgress: (Int) -> Unit = {}) {
    val hc = HealthConnectClient.getOrCreate(context)
    var done = 0
    for (chunk in readings.chunked(500)) {
        hc.insertRecords(chunk.map { it.toRecord() })
        done += chunk.size
        onProgress(done)
    }
}

/** Удаляет сахар за промежуток. Health Connect даёт удалить только записи самого SugarBridge. */
suspend fun deleteOwnGlucose(context: Context, from: Instant, to: Instant) {
    HealthConnectClient.getOrCreate(context).deleteRecords(BloodGlucoseRecord::class, TimeRangeFilter.between(from, to))
}
