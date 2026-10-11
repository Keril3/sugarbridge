package com.sugarbridge.app.debug

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import android.os.Process
import androidx.health.connect.client.HealthConnectClient
import androidx.work.WorkManager
import com.sugarbridge.app.R
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.SAMSUNG_HEALTH_PACKAGE
import com.sugarbridge.app.health.WRITE_GLUCOSE
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.sync.LiveSyncService
import com.sugarbridge.app.sync.SyncStore
import com.sugarbridge.app.sync.SyncWorker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One line of the status in the developer tools; [warn] marks what can hold the glucose back. */
class StatusLine(val label: String, val value: String, val warn: Boolean = false)

/** Everything that decides how fast a reading travels, in the words of the app language. */
suspend fun collectStatus(context: Context): List<StatusLine> = withContext(Dispatchers.IO) {
    val settings = SettingsStore(context).load()
    val clock = DateTimeFormatter.ofPattern("HH:mm:ss")
    val zone = ZoneId.systemDefault()
    fun s(id: Int, vararg args: Any) = context.getString(id, *args)
    val lines = mutableListOf<StatusLine>()

    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    lines += StatusLine(s(R.string.dev_version), "${info.versionName} (${info.longVersionCode})")

    // minute mode: is the service alive, and when did it last ask LibreLinkUp
    if (settings.liveEnabled) {
        val service = s(if (LiveSyncService.running) R.string.dev_service_running else R.string.dev_service_stopped)
        lines += StatusLine(s(R.string.settings_live_title), s(R.string.dev_minute_on, settings.liveMinutes) + " · " + service, warn = !LiveSyncService.running)
        val tick = LiveSyncService.lastTick
        if (tick > 0) {
            val ago = (System.currentTimeMillis() - tick) / 1000
            lines += StatusLine(s(R.string.dev_last_poll), s(R.string.dev_seconds_ago, Instant.ofEpochMilli(tick).atZone(zone).format(clock), ago), warn = ago > settings.liveMinutes * 60 + 90)
        }
    } else {
        lines += StatusLine(s(R.string.settings_live_title), s(R.string.dev_off))
    }

    // the background job: when WorkManager plans to run it next
    if (settings.backgroundMinutes > 0) {
        val work = runCatching { WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncWorker.WORK_NAME).get() }.getOrNull()?.firstOrNull()
        val next = work?.nextScheduleTimeMillis?.takeIf { it in 1 until Long.MAX_VALUE }
            ?.let { Instant.ofEpochMilli(it).atZone(zone).format(clock) } ?: "?"
        lines += StatusLine(s(R.string.settings_background_title), s(R.string.dev_background_on, settings.backgroundMinutes, next) + " · " + (work?.state?.name ?: "?"))
    } else {
        lines += StatusLine(s(R.string.settings_background_title), s(R.string.dev_off))
    }

    // what Android does to background work
    val power = context.getSystemService(PowerManager::class.java)
    val unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
    lines += StatusLine(s(R.string.dev_battery), s(if (unrestricted) R.string.dev_battery_none else R.string.dev_battery_limited), warn = !unrestricted)
    val bucket = context.getSystemService(UsageStatsManager::class.java).appStandbyBucket
    lines += StatusLine(s(R.string.dev_bucket), bucketName(bucket), warn = bucket >= UsageStatsManager.STANDBY_BUCKET_RARE)
    val yes = s(R.string.dev_yes)
    val no = s(R.string.dev_no)
    lines += StatusLine(s(R.string.dev_doze), (if (power.isDeviceIdleMode) yes else no) + " / " + (if (power.isPowerSaveMode) yes else no), warn = power.isPowerSaveMode)

    // where the readings go
    val hc = healthConnectState(context) == HealthConnectState.AVAILABLE
    val canWrite = hc && runCatching { WRITE_GLUCOSE in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() }.getOrDefault(false)
    lines += StatusLine(s(R.string.dev_hc), s(if (canWrite) R.string.dev_allowed else R.string.dev_denied), warn = !canWrite)
    val samsung = runCatching { context.packageManager.getPackageInfo(SAMSUNG_HEALTH_PACKAGE, 0).versionName }.getOrNull()
    lines += StatusLine("Samsung Health", samsung ?: s(R.string.dev_not_installed), warn = samsung == null)

    // how fresh the values are when they arrive
    val snapshot = SyncStore(context).snapshot()
    snapshot.updateMinutes?.let { lines += StatusLine("LibreLinkUp", s(R.string.sync_update_rate, String.format(Locale.getDefault(), "%.1f", it))) }
    snapshot.deliveryMinutes?.let { lines += StatusLine(s(R.string.dev_delivery), s(R.string.sync_delivery, String.format(Locale.getDefault(), "%.1f", it))) }
    lines += StatusLine(s(R.string.dev_usage), s(if (hasUsageAccess(context)) R.string.dev_allowed else R.string.dev_denied))
    lines
}

private fun bucketName(bucket: Int): String = when (bucket) {
    5 -> "exempted"
    UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
    UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set"
    UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
    UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
    UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
    else -> bucket.toString()
} + " ($bucket)"

/** Whether the user let SugarBridge read Android's usage statistics (Settings → Usage access). */
fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}

/**
 * When Samsung Health was opened and closed and when it ran a background service, from Android's usage
 * statistics. Health Connect doesn't tell other apps when Samsung Health reads data; this is the nearest sign
 * of its activity. Empty without usage access.
 */
fun samsungHealthEvents(context: Context, since: Instant): List<LogEntry> {
    if (!hasUsageAccess(context)) return emptyList()
    val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(since.toEpochMilli(), System.currentTimeMillis())
    val out = mutableListOf<LogEntry>()
    val e = UsageEvents.Event()
    var open = false
    var pausedAt: Long? = null   // switching between its own screens pauses one and resumes the next at once
    fun add(at: Long, text: String) {
        out += LogEntry(Instant.ofEpochMilli(at), "s.health", text)
    }
    while (events.hasNextEvent()) {
        events.getNextEvent(e)
        if (e.packageName != SAMSUNG_HEALTH_PACKAGE) continue
        when (e.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> {
                val paused = pausedAt
                if (open && paused != null && e.timeStamp - paused > 2_000) add(paused, "closed")
                if (!open || (paused != null && e.timeStamp - paused > 2_000)) add(e.timeStamp, "opened")
                open = true
                pausedAt = null
            }
            UsageEvents.Event.ACTIVITY_PAUSED -> pausedAt = e.timeStamp
            UsageEvents.Event.FOREGROUND_SERVICE_START -> add(e.timeStamp, "background service started")
            UsageEvents.Event.FOREGROUND_SERVICE_STOP -> add(e.timeStamp, "background service stopped")
        }
    }
    val paused = pausedAt
    if (open && paused != null) add(paused, "closed")
    return out
}
