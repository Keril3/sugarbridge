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
import com.sugarbridge.app.glucose.GlucoseBins
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.GmiResult
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.text
import com.sugarbridge.app.health.HealthConnectState
import com.sugarbridge.app.health.SAMSUNG_HEALTH_PACKAGE
import com.sugarbridge.app.health.deleteOwnGlucose
import com.sugarbridge.app.health.forEachOwnGlucose
import com.sugarbridge.app.health.hasGlucosePermission
import com.sugarbridge.app.health.healthConnectState
import com.sugarbridge.app.health.insertReadings
import com.sugarbridge.app.health.isInstalled
import com.sugarbridge.app.health.ownMinuteKeys
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.libre.LoginFailed
import com.sugarbridge.app.libreview.DaySummary
import com.sugarbridge.app.libreview.HistoryStore
import com.sugarbridge.app.libreview.LibreViewCsv
import com.sugarbridge.app.libreview.MergeMode
import com.sugarbridge.app.libreview.MergePlan
import com.sugarbridge.app.libreview.SavedHistory
import com.sugarbridge.app.libreview.contiguousRanges
import com.sugarbridge.app.libreview.dayBounds
import com.sugarbridge.app.libreview.merge
import com.sugarbridge.app.libreview.planMerge
import com.sugarbridge.app.libreview.readingsOnDays
import com.sugarbridge.app.libreview.summarizeDays
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.sync.LiveSyncService
import com.sugarbridge.app.sync.Problem
import com.sugarbridge.app.sync.SyncBlocked
import com.sugarbridge.app.sync.SyncSnapshot
import com.sugarbridge.app.sync.SyncStore
import com.sugarbridge.app.sync.minuteKey
import com.sugarbridge.app.sync.scheduleSync
import com.sugarbridge.app.sync.syncAndRecord
import com.sugarbridge.app.ui.UiText
import com.sugarbridge.app.ui.clockFormat
import com.sugarbridge.app.ui.uiText
import com.sugarbridge.app.update.Release
import com.sugarbridge.app.update.UpdateRejected
import com.sugarbridge.app.update.Updater
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { NOW, HISTORY, SETTINGS }

/** Everything the home and settings screens need. */
data class UiState(
    val signedIn: Boolean = false,
    val email: String? = null,
    val languageChosen: Boolean = true,
    val healthConnect: HealthConnectState = HealthConnectState.AVAILABLE,
    val canWrite: Boolean? = null,            // null = not checked yet
    val samsungHealth: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val sync: SyncSnapshot = SyncSnapshot(),
    val syncing: Boolean = false,
    val signingIn: Boolean = false,
    val checking: Boolean = false,
    val checkResult: CheckResult? = null,
    val gmi14: GmiResult? = null,   // estimated HbA1c for 14 and 90 days from our own records in Health Connect
    val gmi90: GmiResult? = null,
)

/** A newer version on GitHub and the state of downloading it. */
data class UpdateUi(
    val available: Release? = null,
    val checking: Boolean = false,
    val progress: Float? = null,     // downloading: 0..1
    val checkedNothing: Boolean = false,
)

/** History import from LibreView: the loaded readings, the chosen period and days. */
data class ImportUi(
    val source: String = "",
    val readings: List<Reading> = emptyList(),
    val unit: GlucoseUnit = GlucoseUnit.MMOL,
    val skipped: Int = 0,
    val existing: Set<Long> = emptySet(),   // minutes already in Health Connect
    val days: List<DaySummary> = emptyList(),
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val selected: Set<LocalDate> = emptySet(),
    val busy: Boolean = false,
    val progress: Float? = null,
    val updatedAt: Instant? = null,         // when the history was last imported or updated
) {
    val loaded: Boolean get() = readings.isNotEmpty()
    val visibleDays: List<DaySummary> get() = days.filter { inRange(it.date) }
    val selectedReadings: Int get() = days.filter { it.date in selected }.sumOf { it.count }

    fun inRange(date: LocalDate) = (from == null || date >= from) && (to == null || date <= to)
}

/** A new export read while a history is loaded: it waits for the choice how to merge it. */
class PendingMerge(
    val readings: List<Reading>,
    val unit: GlucoseUnit,
    val skipped: Int,
    val source: String,
    val plan: MergePlan,
)

/** The result of "Check connection", shown under the button: what LibreLinkUp answered, or why it didn't. */
class CheckResult(val ok: Boolean, val text: UiText)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Context get() = getApplication()
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val settingsStore = SettingsStore(app)

    var ui by mutableStateOf(UiState())
        private set
    var history by mutableStateOf(ImportUi())
        private set
    private var importRun = 0   // the latest change of the history; an older one still running is dropped
    var historyOpen by mutableStateOf(false)   // the loaded history is shown, not the History start page
    var focusBackground by mutableStateOf(false)   // Settings scroll to the background sync window once
    var readingFile by mutableStateOf(false)   // a file is being read
        private set
    var pendingMerge by mutableStateOf<PendingMerge?>(null)
        private set
    var update by mutableStateOf(UpdateUi())
        private set
    var tab by mutableStateOf(Tab.NOW)

    private var gmiJob: Job? = null
    private var gmiAt: Instant? = null

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiText> = _messages

    // the service and the background job write to the preferences: the screen updates by itself
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshLocal() }
    private val syncPrefs = app.getSharedPreferences(SyncStore.NAME, Context.MODE_PRIVATE)
    private val settingsPrefs = app.getSharedPreferences(SettingsStore.NAME, Context.MODE_PRIVATE)

    init {
        syncPrefs.registerOnSharedPreferenceChangeListener(listener)
        settingsPrefs.registerOnSharedPreferenceChangeListener(listener)
        refreshAccount()
        refreshLocal()
        restoreHistory()
    }

    override fun onCleared() {
        syncPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        settingsPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun say(text: UiText) {
        _messages.tryEmit(text)
    }

    private fun say(id: Int, vararg args: Any) = say(uiText(id, *args))

    /** "Error: …" with the failure described in the current language. */
    private fun sayError(id: Int, e: Throwable) {
        val problem = Problem.of(e)
        say { res -> res.getString(id, problem.text(res)) }
    }

    /** A message at the bottom of the screen, for the Activity. */
    fun notify(id: Int) = say(id)

    private fun refreshAccount() {
        ui = ui.copy(signedIn = Libre.client(context) != null, email = Libre.email(context))
    }

    private fun refreshLocal() {
        ui = ui.copy(
            settings = settingsStore.load(),
            languageChosen = settingsStore.languageChosen,
            sync = SyncStore(context).snapshot(),
        )
    }

    /** The language screen was answered (or skipped): it won't be shown again. */
    fun languageChosen() {
        settingsStore.languageChosen = true
    }

    /** On every return to the screen: Health Connect, the permission, Samsung Health, the battery. */
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
            if (hc == HealthConnectState.AVAILABLE) refreshGmi()
        }
        checkForUpdate(force = false)
    }

    /** Looks for a newer version on GitHub: by itself on start at most every 6 hours, or when asked. */
    fun checkForUpdate(force: Boolean = true) {
        if (update.checking || update.progress != null) return
        update = update.copy(checking = force, checkedNothing = false)
        viewModelScope.launch {
            try {
                val found = withContext(Dispatchers.IO) { Updater.check(context, force) }
                update = update.copy(available = found, checkedNothing = force && found == null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (force) sayError(R.string.update_failed, e)
            } finally {
                update = update.copy(checking = false)
            }
        }
    }

    /** Downloads the new version, checks it and hands it to Android, which asks the person to confirm. */
    fun installUpdate() {
        val release = update.available ?: return
        if (update.progress != null) return
        update = update.copy(progress = 0f)
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    Updater.download(context, release) { p -> update = update.copy(progress = p) }
                }
                withContext(Dispatchers.IO) {
                    Updater.verify(context, file)
                    Updater.install(context, file)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateRejected) {
                val reason = when (e.reason) {
                    UpdateRejected.Reason.OTHER_KEY -> R.string.update_other_key
                    UpdateRejected.Reason.NOT_NEWER -> R.string.update_not_newer
                    UpdateRejected.Reason.NOT_SUGARBRIDGE -> R.string.update_not_sugarbridge
                }
                say { res -> res.getString(R.string.update_failed, res.getString(reason)) }
            } catch (e: Exception) {
                sayError(R.string.update_failed, e)
            } finally {
                update = update.copy(progress = null)
            }
        }
    }

    /** What Android's installer answered, for the Activity. */
    fun updateFailed(message: String) = say(R.string.update_failed, message)

    /**
     * Estimated HbA1c (GMI) for 14 and 90 days from all SugarBridge readings in Health Connect, in one pass
     * over 90 days of records. Recalculated at most every 10 minutes: it barely changes in that time.
     */
    private fun refreshGmi(force: Boolean = false) {
        if (gmiJob?.isActive == true) return
        val last = gmiAt
        if (!force && last != null && Duration.between(last, Instant.now()) < Duration.ofMinutes(10)) return
        gmiJob = viewModelScope.launch {
            val now = Instant.now()
            val s = ui.settings
            val days14 = GlucoseBins(now.minus(Duration.ofDays(14)), now, s.lowMgDl, s.highMgDl)
            val days90 = GlucoseBins(now.minus(Duration.ofDays(90)), now, s.lowMgDl, s.highMgDl)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    forEachOwnGlucose(context, now.minus(Duration.ofDays(90)), now) { time, mgDl ->
                        days14.add(time, mgDl)
                        days90.add(time, mgDl)
                    }
                }.isSuccess
            }
            if (ok) {
                ui = ui.copy(gmi14 = days14.result(), gmi90 = days90.result())
                gmiAt = now
            }
        }
    }

    /** The app was opened and the last sync is older than two minutes: refresh by ourselves. */
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
                val result = syncAndRecord(context, if (quiet) "app opened" else "button")
                if (!quiet) {
                    if (result.written == 0) say(R.string.msg_nothing_new) else say(R.string.msg_written, result.written)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginFailed) {
                Libre.signOut(context)
                refreshAccount()
                say(R.string.msg_sign_in_again)
            } catch (e: Exception) {
                if (!quiet) sayError(R.string.msg_error, e)
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
                languageChosen()
                say(R.string.msg_signed_in)
                refreshStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sayError(R.string.msg_sign_in_failed, e)
            } finally {
                ui = ui.copy(signingIn = false)
                refreshAccount()
            }
        }
    }

    fun signOut() {
        LiveSyncService.stop(context)   // the mode stays on in the settings and comes back after signing in
        Libre.signOut(context)
        SyncStore(context).clearDisplay()
        ui = ui.copy(checkResult = null)
        refreshAccount()
        refreshLocal()
    }

    /** "Check connection": a request to LibreLinkUp without writing to Health Connect. */
    fun checkLibre() {
        if (ui.checking) return
        ui = ui.copy(checking = true)
        viewModelScope.launch {
            try {
                val client = Libre.client(context) ?: throw SyncBlocked(Problem.Code.SIGNED_OUT)
                val readings = withContext(Dispatchers.IO) { client.readings() }
                val last = readings.lastOrNull()
                val unit = ui.settings.unit
                val region = client.region
                val text = UiText { res ->
                    val locale = res.configuration.locales[0]
                    val value = last?.text(res.getString(unit.label), unit, locale) ?: "—"
                    val time = last?.time?.atZone(zone)?.format(clockFormat(context, locale)) ?: "—"
                    res.getString(R.string.msg_check_ok, readings.size, value, time, region)
                }
                ui = ui.copy(checkResult = CheckResult(true, text))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val problem = Problem.of(e)
                ui = ui.copy(checkResult = CheckResult(false, UiText { res -> res.getString(R.string.msg_error, problem.text(res)) }))
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
        // only what the minute service shows or does matters to it, the theme too: its notification follows it
        val liveChanged = after.copy(
            backgroundMinutes = before.backgroundMinutes,
            textScale = before.textScale, lowVision = before.lowVision, developer = before.developer,
        ) != before
        when {
            after.liveEnabled && !before.liveEnabled -> if (ui.signedIn) LiveSyncService.start(context)
            !after.liveEnabled && before.liveEnabled -> LiveSyncService.stop(context)
            after.liveEnabled && liveChanged -> LiveSyncService.reload(context)
        }
        if (after.lowMgDl != before.lowMgDl || after.highMgDl != before.highMgDl) {
            if (history.loaded) history = history.copy(days = summarize(history.readings, history.existing, after))
            if (ui.gmi90 != null) refreshGmi(force = true)   // its time in range follows the new target range
        }
    }

    // ---------- history from LibreView ----------

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            val bytes = try {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            } catch (e: Exception) {
                null
            }
            if (bytes == null) say(R.string.msg_file_unreadable) else importBytes(bytes, displayName(uri) ?: "CSV")
        }
    }

    /** The file caught by the LibreView window. */
    fun importFile(path: String) {
        viewModelScope.launch {
            val file = File(path)
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull().also { file.delete() } }
            if (bytes == null) say(R.string.msg_file_lost) else importBytes(bytes, "LibreView")
        }
    }

    /** The kept history comes back on every start, until it is deleted. */
    private fun restoreHistory() {
        val run = ++importRun
        viewModelScope.launch {
            readingFile = true
            val saved = withContext(Dispatchers.IO) { runCatching { HistoryStore.load(context, zone) }.getOrNull() }
            readingFile = false
            if (saved != null) showHistory(run, saved, skipped = 0, save = false, open = false)
        }
    }

    /**
     * Reads an export. Without a loaded history it becomes the history and opens; with one, the two are
     * compared and the choice how to merge them waits in [pendingMerge].
     */
    private suspend fun importBytes(bytes: ByteArray, source: String) {
        val run = ++importRun
        tab = Tab.HISTORY
        readingFile = true
        try {
            val parsed = withContext(Dispatchers.Default) { LibreViewCsv.parse(LibreViewCsv.decode(bytes), zone) }
            if (run != importRun) return
            when {
                parsed.readings.isEmpty() -> say(R.string.msg_no_glucose)
                history.loaded -> {
                    val plan = withContext(Dispatchers.Default) { planMerge(history.readings, parsed.readings) }
                    if (run == importRun) pendingMerge = PendingMerge(parsed.readings, parsed.unit, parsed.skippedRows, source, plan)
                }
                else -> showHistory(run, SavedHistory(parsed.readings, parsed.unit, source, Instant.now()), parsed.skippedRows, save = true, open = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (run == importRun) sayError(R.string.msg_parse_failed, e)
        } finally {
            if (run == importRun) readingFile = false
        }
    }

    /** The choice for a new export: add it, add it over the differing readings, or let it replace the history. */
    fun applyMerge(mode: MergeMode) {
        val p = pendingMerge ?: return
        pendingMerge = null
        val run = ++importRun
        val old = history.readings
        viewModelScope.launch {
            val merged = withContext(Dispatchers.Default) { merge(old, p.readings, mode) }
            showHistory(run, SavedHistory(merged, p.unit, p.source, Instant.now()), p.skipped, save = true, open = true)
        }
    }

    fun cancelMerge() {
        pendingMerge = null
    }

    /** Shows a history in the History tab, marks what Health Connect already has and keeps it for the next start. */
    private suspend fun showHistory(run: Int, saved: SavedHistory, skipped: Int, save: Boolean, open: Boolean) {
        val existing = existingKeys(saved.readings)
        val days = withContext(Dispatchers.Default) { summarize(saved.readings, existing, ui.settings) }
        if (run != importRun) return
        history = ImportUi(
            source = saved.source,
            readings = saved.readings,
            unit = saved.unit,
            skipped = skipped,
            existing = existing,
            days = days,
            from = days.first().date,
            to = days.last().date,
            selected = days.filterNot { it.complete }.map { it.date }.toSet(),
            updatedAt = saved.updatedAt,
        )
        if (open) historyOpen = true
        if (save) withContext(Dispatchers.IO) { runCatching { HistoryStore.save(context, saved) } }
    }

    /** The link in the notice: Settings, scrolled to background sync; the notice is done. */
    fun openBackgroundSettings() {
        tab = Tab.SETTINGS
        focusBackground = true
        updateSettings { it.copy(backgroundNotice = false) }
    }

    fun dismissBackgroundNotice() = updateSettings { it.copy(backgroundNotice = false) }

    /** "All-time history" on the home screen: the History tab, with the loaded history open if there is one. */
    fun openHistory() {
        tab = Tab.HISTORY
        historyOpen = history.loaded
    }

    private fun summarize(readings: List<Reading>, existing: Set<Long>, s: AppSettings) =
        summarizeDays(readings, zone, s.lowMgDl, s.highMgDl, existing)

    /** Which minutes are already in Health Connect. If Health Connect is unavailable, assume none. */
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
        // in a new period, the days not yet in Health Connect are selected by default
        history = h.copy(selected = h.visibleDays.filterNot { it.complete }.map { it.date }.toSet())
    }

    /** The last N days of the history (null = all of it). */
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

    /** Deletes the loaded history from SugarBridge; what is in Health Connect stays. */
    fun deleteHistory() {
        importRun++   // an import still running must not bring the history back
        history = ImportUi()
        historyOpen = false
        pendingMerge = null
        // right away, not in the background: a new import saved a moment later must not be deleted with it
        HistoryStore.clear(context)
    }

    fun writeSelected() {
        val h = history
        val chosen = readingsOnDays(h.readings, h.selected, zone)
        if (chosen.isEmpty() || h.busy) return
        history = h.copy(busy = true, progress = 0f)
        viewModelScope.launch {
            try {
                if (healthConnectState(context) != HealthConnectState.AVAILABLE || !hasGlucosePermission(context)) {
                    say(R.string.msg_allow_hc_first)
                    return@launch
                }
                val existing = existingKeys(chosen)
                val now = Instant.now()
                // Health Connect won't take a time in the future; usually the file comes from another time zone
                val future = chosen.count { it.time.isAfter(now) }
                val toWrite = chosen.filter { minuteKey(it.time) !in existing && !it.time.isAfter(now) }
                withContext(Dispatchers.IO) {
                    insertReadings(context, toWrite) { done ->
                        history = history.copy(progress = done.toFloat() / toWrite.size)
                    }
                }
                val allExisting = existingKeys(h.readings)
                history = history.copy(existing = allExisting, days = summarize(h.readings, allExisting, ui.settings), selected = emptySet())
                refreshGmi(force = true)   // past days change the estimated HbA1c
                val already = chosen.size - toWrite.size - future
                say { res ->
                    listOfNotNull(
                        res.getString(R.string.msg_written, toWrite.size),
                        if (already > 0) res.getString(R.string.msg_already_there, already) else null,
                        if (future > 0) res.getString(R.string.msg_future_skipped, future) else null,
                    ).joinToString(" · ")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sayError(R.string.msg_write_failed, e)
            } finally {
                history = history.copy(busy = false, progress = null)
            }
        }
    }

    /** Deletes all SugarBridge records for the selected days from Health Connect (both from LibreLinkUp and from the history). */
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
                    SyncStore(context).forget(from, to)   // LibreLinkUp may write these minutes again
                }
                val allExisting = existingKeys(h.readings)
                history = history.copy(existing = allExisting, days = summarize(h.readings, allExisting, ui.settings), selected = emptySet())
                refreshGmi(force = true)
                say(R.string.msg_deleted, h.selected.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sayError(R.string.msg_delete_failed, e)
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
