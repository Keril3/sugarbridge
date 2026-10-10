package com.sugarbridge.app

import android.content.Context
import androidx.core.content.edit
import androidx.health.connect.client.HealthConnectClient
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Итог одной синхронизации: сколько записано, последний замер и все замеры за 12 часов. */
class SyncResult(val written: Int, val latest: Reading?, val readings: List<Reading>)

/** Фон, минутный сервис и кнопка на экране не должны синхронизировать одновременно. */
private val syncMutex = Mutex()

/** Забирает замеры из LibreLinkUp и пишет в Health Connect те, которых там ещё нет. */
suspend fun syncOnce(context: Context): SyncResult = syncMutex.withLock {
    check(healthConnectState(context) == HealthConnectState.AVAILABLE) { "Health Connect недоступен на этом телефоне" }
    val hc = HealthConnectClient.getOrCreate(context)
    check(WRITE_GLUCOSE in hc.permissionController.getGrantedPermissions()) {
        "Нет разрешения на запись сахара в Health Connect"
    }
    val libre = Libre.client(context) ?: throw IllegalStateException("Сначала войдите в LibreLinkUp")
    val readings = withContext(Dispatchers.IO) { libre.readings() }

    val store = SyncStore(context)
    val state = store.state()
    val now = Instant.now()
    val fresh = state.fresh(readings, now)
    if (fresh.isNotEmpty()) {
        // за 12 часов замеров заметно меньше 1000 — это лимит Health Connect на одну запись
        hc.insertRecords(fresh.map { it.toRecord() })
    }
    // порядок важен: сначала запись, потом состояние; при сбое между ними повтор безопасен благодаря ключам
    store.saveState(state.after(fresh, now))
    store.saveSuccess(readings, fresh.size)
    SyncResult(fresh.size, readings.lastOrNull(), readings)
}

/** То же, но ошибка запоминается, чтобы главный экран мог её показать. */
suspend fun syncAndRecord(context: Context): SyncResult = try {
    syncOnce(context)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    SyncStore(context).saveError(e.message ?: e.javaClass.simpleName)
    throw e
}

/** Что показать на главном экране: последние замеры, когда была удачная синхронизация, ошибка. */
data class SyncSnapshot(
    val recent: List<Reading> = emptyList(),
    val lastOkAt: Instant? = null,
    val lastWritten: Int = 0,
    val error: String? = null,
    val errorAt: Instant? = null,
    val updateMinutes: Double? = null,   // как часто LibreLinkUp на деле присылает новое значение
)

class SyncStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun state(): SyncState = SyncState(
        written = decodeKeys(prefs.getString("written", null)),
        // last_synced остался от версии из гайда: всё, что не новее его, уже записано со старыми ключами
        floor = prefs.getLong("floor", prefs.getLong("last_synced", 0)),
    )

    fun saveState(state: SyncState) {
        prefs.edit { putString("written", encodeKeys(state.written)).putLong("floor", state.floor) }
    }

    /** После удаления из Health Connect эти минуты снова можно записать. */
    fun forget(from: Instant, to: Instant) = saveState(state().forget(from, to))

    fun saveSuccess(readings: List<Reading>, written: Int) {
        val seen = (seen() + listOfNotNull(readings.lastOrNull()?.time?.epochSecond)).distinct().takeLast(16)
        prefs.edit {
            putString("recent", encodeReadings(readings))
            putLong("ok_at", System.currentTimeMillis())
            putInt("ok_written", written)
            putString("seen", seen.joinToString(","))
            remove("error")
            remove("error_at")
        }
    }

    fun saveError(message: String) {
        prefs.edit { putString("error", message).putLong("error_at", System.currentTimeMillis()) }
    }

    /** При выходе из аккаунта: замеры и ошибки на экране больше не нужны, а записанные минуты помним. */
    fun clearDisplay() {
        prefs.edit {
            listOf("recent", "ok_at", "ok_written", "seen", "error", "error_at").forEach { remove(it) }
        }
    }

    fun snapshot() = SyncSnapshot(
        recent = decodeReadings(prefs.getString("recent", null)),
        lastOkAt = prefs.getLong("ok_at", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        lastWritten = prefs.getInt("ok_written", 0),
        error = prefs.getString("error", null),
        errorAt = prefs.getLong("error_at", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        updateMinutes = typicalUpdateMinutes(seen().map(Instant::ofEpochSecond)),
    )

    private fun seen(): List<Long> = prefs.getString("seen", null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    companion object {
        const val NAME = "sync"
    }
}
