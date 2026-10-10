package com.sugarbridge.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.PowerManager
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.text
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.SAMSUNG_HEALTH_PACKAGE
import com.sugarbridge.app.health.deleteOwnGlucose
import com.sugarbridge.app.health.hasGlucosePermission
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.health.insertReadings
import com.sugarbridge.app.health.isInstalled
import com.sugarbridge.app.health.ownMinuteKeys
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.libre.LoginFailed
import com.sugarbridge.app.libreview.DaySummary
import com.sugarbridge.app.libreview.LibreViewCsv
import com.sugarbridge.app.libreview.contiguousRanges
import com.sugarbridge.app.libreview.dayBounds
import com.sugarbridge.app.libreview.readingsOnDays
import com.sugarbridge.app.libreview.summarizeDays
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.sync.LiveSyncService
import com.sugarbridge.app.sync.SyncSnapshot
import com.sugarbridge.app.sync.SyncStore
import com.sugarbridge.app.sync.minuteKey
import com.sugarbridge.app.sync.scheduleSync
import com.sugarbridge.app.sync.syncAndRecord
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { NOW, HISTORY, SETTINGS }

/** Всё, что нужно главному экрану и настройкам. */
data class UiState(
    val signedIn: Boolean = false,
    val email: String? = null,
    val healthConnect: HealthConnectState = HealthConnectState.AVAILABLE,
    val canWrite: Boolean? = null,            // null — ещё не проверили
    val samsungHealth: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val sync: SyncSnapshot = SyncSnapshot(),
    val syncing: Boolean = false,
    val signingIn: Boolean = false,
    val checking: Boolean = false,
)

/** Импорт истории из LibreView: загруженные замеры, выбранный период и дни. */
data class ImportUi(
    val source: String = "",
    val readings: List<Reading> = emptyList(),
    val unit: GlucoseUnit = GlucoseUnit.MMOL,
    val skipped: Int = 0,
    val existing: Set<Long> = emptySet(),   // минуты, которые уже есть в Health Connect
    val days: List<DaySummary> = emptyList(),
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val selected: Set<LocalDate> = emptySet(),
    val busy: Boolean = false,
    val progress: Float? = null,
) {
    val loaded: Boolean get() = readings.isNotEmpty()
    val visibleDays: List<DaySummary> get() = days.filter { inRange(it.date) }
    val selectedReadings: Int get() = days.filter { it.date in selected }.sumOf { it.count }

    fun inRange(date: LocalDate) = (from == null || date >= from) && (to == null || date <= to)
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val settingsStore = SettingsStore(app)

    var ui by mutableStateOf(UiState())
        private set
    var history by mutableStateOf(ImportUi())
        private set
    var tab by mutableStateOf(Tab.NOW)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    // сервис и фоновая задача пишут в настройки — экран обновляется сам
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshLocal() }
    private val syncPrefs = app.getSharedPreferences(SyncStore.NAME, Context.MODE_PRIVATE)
    private val settingsPrefs = app.getSharedPreferences(SettingsStore.NAME, Context.MODE_PRIVATE)

    init {
        syncPrefs.registerOnSharedPreferenceChangeListener(listener)
        settingsPrefs.registerOnSharedPreferenceChangeListener(listener)
        refreshAccount()
        refreshLocal()
    }

    override fun onCleared() {
        syncPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        settingsPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    /** Сообщение внизу экрана — для Activity. */
    fun notify(text: String) = say(text)

    private fun refreshAccount() {
        ui = ui.copy(signedIn = Libre.client(context) != null, email = Libre.email(context))
    }

    private fun refreshLocal() {
        ui = ui.copy(settings = settingsStore.load(), sync = SyncStore(context).snapshot())
    }

    /** При каждом возвращении на экран: Health Connect, разрешение, Samsung Health, батарея. */
    fun refreshStatus() {
        refreshAccount()
        refreshLocal()
        val hc = healthConnectState(context)
        val power = context.getSystemService(PowerManager::class.java)
        ui = ui.copy(
            healthConnect = hc,
            samsungHealth = isInstalled(context, SAMSUNG_HEALTH_PACKAGE),
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
        )
        viewModelScope.launch {
            val canWrite = hc == HealthConnectState.AVAILABLE && runCatching { hasGlucosePermission(context) }.getOrDefault(false)
            ui = ui.copy(canWrite = canWrite)
            if (ui.settings.liveEnabled && ui.signedIn && canWrite) LiveSyncService.start(context)
            autoSync()
        }
    }

    /** Открыли приложение, а последней синхронизации больше двух минут — обновимся сами. */
    private fun autoSync() {
        val s = ui
        val stale = s.sync.lastOkAt?.let { Duration.between(it, Instant.now()) > Duration.ofMinutes(2) } ?: true
        if (s.signedIn && s.canWrite == true && stale) syncNow(quiet = true)
    }

    fun syncNow(quiet: Boolean = false) {
        if (ui.syncing) return
        ui = ui.copy(syncing = true)
        viewModelScope.launch {
            try {
                val result = syncAndRecord(context)
                if (!quiet) {
                    say(if (result.written == 0) "Новых замеров нет — всё уже в Health Connect ♡" else "Записано в Health Connect: ${result.written} ✧")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginFailed) {
                Libre.signOut(context)
                refreshAccount()
                say("Пароль LibreLinkUp не подошёл — войдите снова")
            } catch (e: Exception) {
                if (!quiet) say("Ошибка: ${e.message}")
            } finally {
                ui = ui.copy(syncing = false)
                refreshLocal()
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (ui.signingIn) return
        ui = ui.copy(signingIn = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { Libre.signIn(context, email.trim(), password) }
                say("Вход выполнен ♡")
                refreshStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("Не получилось: ${e.message}")
            } finally {
                ui = ui.copy(signingIn = false)
                refreshAccount()
            }
        }
    }

    fun signOut() {
        LiveSyncService.stop(context)   // режим остаётся включённым в настройках и вернётся после входа
        Libre.signOut(context)
        SyncStore(context).clearDisplay()
        refreshAccount()
        refreshLocal()
    }

    /** «Проверить связь»: запрос в LibreLinkUp без записи в Health Connect. */
    fun checkLibre() {
        if (ui.checking) return
        ui = ui.copy(checking = true)
        viewModelScope.launch {
            try {
                val client = Libre.client(context) ?: throw IllegalStateException("Сначала войдите в LibreLinkUp")
                val readings = withContext(Dispatchers.IO) { client.readings() }
                val last = readings.lastOrNull()?.text(unit = ui.settings.unit) ?: "—"
                say("LibreLinkUp на связи: ${readings.size} замеров за 12 часов, последний $last (регион ${client.region})")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("Ошибка: ${e.message}")
            } finally {
                ui = ui.copy(checking = false)
            }
        }
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val before = settingsStore.load()
        val after = settingsStore.update(change)
        ui = ui.copy(settings = after)
        if (after.backgroundMinutes != before.backgroundMinutes) scheduleSync(context, after)
        when {
            after.liveEnabled && !before.liveEnabled -> if (ui.signedIn) LiveSyncService.start(context)
            !after.liveEnabled && before.liveEnabled -> LiveSyncService.stop(context)
            after.liveEnabled && after != before -> LiveSyncService.reload(context)
        }
        if ((after.lowMgDl != before.lowMgDl || after.highMgDl != before.highMgDl) && history.loaded) {
            history = history.copy(days = summarize(history.readings, history.existing, after))
        }
    }

    // ---------- история из LibreView ----------

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val bytes = try {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            } catch (e: Exception) {
                null
            }
            if (bytes == null) say("Не удалось открыть файл") else importBytes(bytes, displayName(uri) ?: "CSV")
        }
    }

    /** Файл, который перехватило окно LibreView. */
    fun importFile(path: String) {
        viewModelScope.launch {
            val file = File(path)
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull().also { file.delete() } }
            if (bytes == null) say("Файл из LibreView потерялся, попробуйте ещё раз") else importBytes(bytes, "LibreView")
        }
    }

    private suspend fun importBytes(bytes: ByteArray, source: String) {
        tab = Tab.HISTORY
        history = ImportUi(source = source, busy = true)
        try {
            val parsed = withContext(Dispatchers.Default) { LibreViewCsv.parse(LibreViewCsv.decode(bytes), zone) }
            if (parsed.readings.isEmpty()) {
                history = ImportUi()
                say("В файле нет замеров глюкозы. Нужна выгрузка «Данные о глюкозе» из LibreView")
                return
            }
            val existing = existingKeys(parsed.readings)
            val days = summarize(parsed.readings, existing, ui.settings)
            history = ImportUi(
                source = source,
                readings = parsed.readings,
                unit = parsed.unit,
                skipped = parsed.skippedRows,
                existing = existing,
                days = days,
                from = days.first().date,
                to = days.last().date,
                selected = days.filterNot { it.complete }.map { it.date }.toSet(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            history = ImportUi()
            say("Не удалось прочитать файл: ${e.message}")
        }
    }

    private fun summarize(readings: List<Reading>, existing: Set<Long>, s: AppSettings) =
        summarizeDays(readings, zone, s.lowMgDl, s.highMgDl, existing)

    /** Какие минуты уже есть в Health Connect. Если Health Connect недоступен — считаем, что ничего. */
    private suspend fun existingKeys(readings: List<Reading>): Set<Long> {
        if (readings.isEmpty() || healthConnectState(context) != HealthConnectState.AVAILABLE) return emptySet()
        return try {
            withContext(Dispatchers.IO) {
                ownMinuteKeys(context, readings.first().time, readings.last().time.plusSeconds(60))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setRange(from: LocalDate, to: LocalDate) {
        val h = history.copy(from = minOf(from, to), to = maxOf(from, to))
        // в новом периоде по умолчанию выбраны дни, которых ещё нет в Health Connect
        history = h.copy(selected = h.visibleDays.filterNot { it.complete }.map { it.date }.toSet())
    }

    /** Последние N дней истории (null — вся история). */
    fun quickRange(days: Long?) {
        val all = history.days
        if (all.isEmpty()) return
        val last = all.last().date
        setRange(if (days == null) all.first().date else maxOf(all.first().date, last.minusDays(days - 1)), last)
    }

    fun toggleDay(date: LocalDate) {
        val s = history.selected
        history = history.copy(selected = if (date in s) s - date else s + date)
    }

    fun selectAll() {
        history = history.copy(selected = history.visibleDays.map { it.date }.toSet())
    }

    fun selectNone() {
        history = history.copy(selected = emptySet())
    }

    fun selectMissing() {
        history = history.copy(selected = history.visibleDays.filterNot { it.complete }.map { it.date }.toSet())
    }

    fun closeHistory() {
        history = ImportUi()
    }

    fun writeSelected() {
        val h = history
        val chosen = readingsOnDays(h.readings, h.selected, zone)
        if (chosen.isEmpty() || h.busy) return
        history = h.copy(busy = true, progress = 0f)
        viewModelScope.launch {
            try {
                if (healthConnectState(context) != HealthConnectState.AVAILABLE || !hasGlucosePermission(context)) {
                    say("Сначала разрешите SugarBridge запись в Health Connect")
                    return@launch
                }
                val existing = existingKeys(chosen)
                val now = Instant.now()
                // время «из будущего» Health Connect не примет — обычно это значит, что файл из другого часового пояса
                val future = chosen.count { it.time.isAfter(now) }
                val toWrite = chosen.filter { minuteKey(it.time) !in existing && !it.time.isAfter(now) }
                withContext(Dispatchers.IO) {
                    insertReadings(context, toWrite) { done ->
                        history = history.copy(progress = done.toFloat() / toWrite.size)
                    }
                }
                val allExisting = existingKeys(h.readings)
                history = history.copy(existing = allExisting, days = summarize(h.readings, allExisting, ui.settings), selected = emptySet())
                val already = chosen.size - toWrite.size - future
                say(
                    "Записано в Health Connect: ${toWrite.size} ✧" +
                        (if (already > 0) " · уже были: $already" else "") +
                        (if (future > 0) " · из будущего пропущено: $future — проверьте часовой пояс" else "")
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("Ошибка записи: ${e.message}")
            } finally {
                history = history.copy(busy = false, progress = null)
            }
        }
    }

    /** Удаляет из Health Connect все записи SugarBridge за выбранные дни (и из LibreLinkUp, и из истории). */
    fun deleteSelected() {
        val h = history
        if (h.selected.isEmpty() || h.busy) return
        history = h.copy(busy = true, progress = null)
        viewModelScope.launch {
            try {
                for (range in contiguousRanges(h.selected)) {
                    val from = dayBounds(range.start, zone).first
                    val to = dayBounds(range.endInclusive, zone).second
                    withContext(Dispatchers.IO) { deleteOwnGlucose(context, from, to) }
                    SyncStore(context).forget(from, to)   // эти минуты LibreLinkUp снова сможет записать
                }
                val allExisting = existingKeys(h.readings)
                history = history.copy(existing = allExisting, days = summarize(h.readings, allExisting, ui.settings), selected = emptySet())
                say("Удалено из Health Connect: ${h.selected.size} дн.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("Не удалось удалить: ${e.message}")
            } finally {
                history = history.copy(busy = false)
            }
        }
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}
