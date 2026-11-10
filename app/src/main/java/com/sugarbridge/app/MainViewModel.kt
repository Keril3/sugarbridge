package com.sugarbridge.app

import android.app.Application
import android.content.ContentResolver
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
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.glucose.GlucoseBins
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.GmiCache
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
import com.sugarbridge.app.libreview.LibreViewActivity
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
import com.sugarbridge.app.local.LibreNotificationListener
import com.sugarbridge.app.local.LocalReadings
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
import com.sugarbridge.app.ui.screens.SettingsTab
import com.sugarbridge.app.ui.uiText
import com.sugarbridge.app.update.GitHubError
import com.sugarbridge.app.update.Release
import com.sugarbridge.app.update.UpdateOffer
import com.sugarbridge.app.update.UpdateRejected
import com.sugarbridge.app.update.Updater
import com.sugarbridge.app.update.scheduleUpdateChecks
import java.io.File
import java.io.IOException
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

/** How far back a history begun by "Add new readings" reaches into SugarBridge's records in Health Connect. */
private const val AUTO_ADD_DAYS = 90L

/** Everything the home and settings screens need. */
data class UiState(
    val signedIn: Boolean = false,
    val email: String? = null,
    val languageChosen: Boolean = true,
    val healthConnect: HealthConnectState = HealthConnectState.AVAILABLE,
    val canWrite: Boolean? = null,            // null = not checked yet
    val samsungHealth: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    val notificationAccess: Boolean = false,  // for the glucose from LibreLink's notification
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
    val marksKnown: Boolean = true,         // false until Health Connect was asked which days it already has
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
    var settingsTab by mutableStateOf(SettingsTab.SYNC)
    var readingFile by mutableStateOf(false)   // a file is being read
        private set
    var pendingMerge by mutableStateOf<PendingMerge?>(null)
        private set
    var update by mutableStateOf(UpdateUi())
        private set
    var updateOffer by mutableStateOf<Release?>(null)   // the window offering a new version
        private set
    var privacyOpen by mutableStateOf(false)   // the privacy window: from Health Connect's link or About
    var tab by mutableStateOf(Tab.NOW)

    private var gmiJob: Job? = null
    private val gmiCache = GmiCache(app)
    private var updateJob: Job? = null
    private var marksJob: Job? = null

    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiText> = _messages

    // the service and the background job write to the preferences: the screen updates by itself. One save changes
    // several keys and Android reports each of them, so they are taken together: one reading and one redraw a sync
    private var refreshQueued = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!refreshQueued) {
            refreshQueued = true
            viewModelScope.launch(Dispatchers.Main) {   // not Main.immediate: after the other keys of this save
                refreshQueued = false
                refreshLocal()
                showAddedReadings()
            }
        }
    }
    private var historyChangedAt = 0L   // the kept history's file when it was last shown
    private val syncPrefs = app.getSharedPreferences(SyncStore.NAME, Context.MODE_PRIVATE)
    private val settingsPrefs = app.getSharedPreferences(SettingsStore.NAME, Context.MODE_PRIVATE)

    init {
        syncPrefs.registerOnSharedPreferenceChangeListener(listener)
        settingsPrefs.registerOnSharedPreferenceChangeListener(listener)
        refreshAccount()
        refreshLocal()
        gmiCache.load()?.let { ui = ui.copy(gmi14 = it.days14, gmi90 = it.days90) }   // at once, not after reading the records
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

    /** "Error: …" with the failure described in the current language; its technical detail goes to the event log. */
    private fun sayError(id: Int, e: Throwable) {
        val problem = Problem.of(e, context)
        DebugLog.add(context, "error", "${problem.code} ${problem.detail.orEmpty()}".trim())
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
        viewModelScope.launch {
            // questions to Android's services, on every return to the screen: not on the thread that draws it
            val status = withContext(Dispatchers.IO) {
                val power = context.getSystemService(PowerManager::class.java)
                ui.copy(
                    healthConnect = healthConnectState(context),
                    samsungHealth = isInstalled(context, SAMSUNG_HEALTH_PACKAGE),
                    batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
                    notificationAccess = LibreNotificationListener.hasAccess(context),
                )
            }
            ui = ui.copy(
                healthConnect = status.healthConnect,
                samsungHealth = status.samsungHealth,
                batteryUnrestricted = status.batteryUnrestricted,
                notificationAccess = status.notificationAccess,
            )
            val hc = status.healthConnect
            val canWrite = hc == HealthConnectState.AVAILABLE && runCatching { hasGlucosePermission(context) }.getOrDefault(false)
            ui = ui.copy(canWrite = canWrite)
            if (ui.settings.liveEnabled && ui.signedIn && canWrite) LiveSyncService.start(context)
            autoSync()
            if (hc == HealthConnectState.AVAILABLE) refreshGmi()
        }
        // by itself only with the switch on; a version found before is still shown
        if (ui.settings.autoUpdateCheck) checkForUpdate(force = false) else update = update.copy(available = Updater.known(context))
    }

    /** Looks for a newer version on GitHub: by itself on start at most every 6 hours, or when asked. */
    fun checkForUpdate(force: Boolean = true) {
        if (update.checking || update.progress != null) return
        if (updateJob?.isActive == true) {   // the check by itself is under way: it isn't started twice, the button takes over
            if (!force) return
            updateJob?.cancel()
        }
        update = update.copy(checking = force, checkedNothing = false)
        updateJob = viewModelScope.launch {
            try {
                val found = withContext(Dispatchers.IO) { Updater.check(context, force) }
                update = update.copy(available = found, checkedNothing = force && found == null)
                // the check by itself offers a new version in a window, once; "Check for updates" shows it in place
                if (!force && found != null && UpdateOffer.shouldOffer(context, ui.settings, found)) updateOffer = found
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (force) sayUpdateError(e)
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
                withContext(Dispatchers.IO) {
                    val file = Updater.download(context, release) { p -> viewModelScope.launch { if (update.progress != null) update = update.copy(progress = p) } }
                    try {
                        Updater.verify(context, file)
                        Updater.install(context, file)
                    } finally {
                        file.delete()   // the installer has its own copy: tens of megabytes needn't stay in the cache
                    }
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
                sayUpdateError(e)
            } finally {
                update = update.copy(progress = null)
            }
        }
    }

    /** An update failure in GitHub's words: the update doesn't come from LibreLinkUp. The technical part goes to the log. */
    private fun sayUpdateError(e: Exception) {
        DebugLog.add(context, "error", "update: ${e.message ?: e.javaClass.simpleName}")
        say { res ->
            val reason = when (e) {
                is GitHubError -> res.getString(R.string.error_github_server, e.code)
                is IOException -> res.getString(R.string.error_github_network)
                else -> res.getString(R.string.error_other)
            }
            res.getString(R.string.update_failed, reason)
        }
    }

    /** The notification about a new version was tapped: offer it in the window. */
    fun showUpdateOffer() {
        val release = Updater.known(context) ?: return
        update = update.copy(available = release)
        updateOffer = release
    }

    /** The offer window was answered; it doesn't come back for this version. */
    fun closeUpdateOffer() {
        updateOffer?.let { UpdateOffer.markOffered(context, it.version) }
        updateOffer = null
    }

    /** Android's installer refused, for the Activity. Its own words are technical and go to the log. */
    fun updateFailed(conflict: Boolean, detail: String) {
        DebugLog.add(context, "error", "installer: $detail")
        say { res -> res.getString(R.string.update_failed, res.getString(if (conflict) R.string.update_other_key else R.string.error_other)) }
    }

    /**
     * Estimated HbA1c (GMI) for 14 and 90 days from all SugarBridge readings in Health Connect, in one pass
     * over 90 days of records. Kept between starts and counted again only when old enough, see [GmiCache].
     */
    private fun refreshGmi(force: Boolean = false) {
        if (gmiJob?.isActive == true) return
        if (!force && gmiCache.load()?.isFresh(Instant.now(), ui.settings.lowMgDl, ui.settings.highMgDl) == true) return
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
                val (gmi14, gmi90) = withContext(Dispatchers.Default) { days14.result() to days90.result() }
                ui = ui.copy(gmi14 = gmi14, gmi90 = gmi90)
                gmiCache.save(gmi14, gmi90, now, s.lowMgDl, s.highMgDl)
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
                if (result.written > 0 && ui.gmi90 == null) refreshGmi(force = true)   // the very first readings
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
        LibreViewActivity.forgetSession(context)
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
                val problem = Problem.of(e, context)
                DebugLog.add(context, "error", "check: ${problem.code} ${problem.detail.orEmpty()}".trim())
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
        if (after.autoUpdateCheck != before.autoUpdateCheck || after.updatePrompt != before.updatePrompt) {
            scheduleUpdateChecks(context, after)
            if (after.autoUpdateCheck && !before.autoUpdateCheck) checkForUpdate(force = false)
        }
        // only what the minute service shows or does matters to it, the theme too: its notification follows it
        val liveChanged = after.copy(
            backgroundMinutes = before.backgroundMinutes, autoUpdateCheck = before.autoUpdateCheck, updatePrompt = before.updatePrompt,
            textScale = before.textScale, lowVision = before.lowVision, developer = before.developer,
        ) != before
        when {
            after.liveEnabled && !before.liveEnabled -> if (ui.signedIn) LiveSyncService.start(context)
            !after.liveEnabled && before.liveEnabled -> LiveSyncService.stop(context)
            after.liveEnabled && liveChanged -> LiveSyncService.reload(context)
        }
        if (after.lowMgDl != before.lowMgDl || after.highMgDl != before.highMgDl) {
            val h = history
            if (h.loaded) {
                viewModelScope.launch {
                    val days = withContext(Dispatchers.Default) { summarize(h.readings, h.existing, after) }
                    if (history.readings === h.readings) history = history.copy(days = days)
                }
            }
            if (ui.gmi90 != null) refreshGmi(force = true)   // its time in range follows the new target range
        }
    }

    // ---------- history from LibreView ----------

    /**
     * A file from the picker or from another app. Only through a content: address, by which another app gives what
     * is its own: a file: address could point at SugarBridge's own files. Its size and name are another app's word,
     * so they are checked, and it is read away from the main thread, since that app may take its time.
     */
    fun importUri(uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            say(R.string.msg_file_unreadable)
            return
        }
        viewModelScope.launch {
            val (bytes, name) = withContext(Dispatchers.IO) {
                val read = try {
                    context.contentResolver.openInputStream(uri)?.use { LibreViewCsv.readAtMost(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                read to if (read != null) LibreViewCsv.cleanName(displayName(uri)) else null
            }
            if (bytes == null) say(R.string.msg_file_unreadable) else importBytes(bytes, name ?: "CSV")
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
            if (saved != null) showHistory(run, saved, skipped = 0, save = false, open = false, askHealthConnect = false)
        }
    }

    /**
     * Which days Health Connect already has, for a history restored at start. Asked only when the History tab
     * opens: it means reading all of SugarBridge's records for the whole history, and most starts never go there.
     */
    fun markHistoryDays() {
        val h = history
        if (!h.loaded || h.marksKnown || h.busy || marksJob?.isActive == true) return
        val run = importRun
        val settings = ui.settings
        marksJob = viewModelScope.launch {
            val existing = existingKeys(h.readings)
            val days = withContext(Dispatchers.Default) { summarize(h.readings, existing, settings) }
            val now = history
            if (run != importRun || now.readings !== h.readings || now.busy) return@launch
            // the days not yet in Health Connect are selected, unless the person already picked some meanwhile
            val selected = if (now.selected.isEmpty()) days.filterNot { it.complete }.filter { now.inRange(it.date) }.map { it.date }.toSet() else now.selected
            history = now.copy(existing = existing, days = days, selected = selected, marksKnown = true)
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
        } catch (e: OutOfMemoryError) {   // not an export: nothing a real one would come to
            if (run == importRun) say(R.string.msg_file_unreadable)
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

    /**
     * "Add new readings": each sync adds its readings to the kept history (see HistoryStore.append). Turned on, it
     * also fills the gap from the end of the history with SugarBridge's own records in Health Connect, or, without
     * a history, begins one with the last [AUTO_ADD_DAYS] days of them. Without the permission to read them the
     * syncs alone add what comes next.
     */
    fun setHistoryAutoAdd(on: Boolean) {
        updateSettings { it.copy(historyAutoAdd = on) }
        if (!on) return
        val run = ++importRun
        val unit = ui.settings.unit
        viewModelScope.launch {
            readingFile = true
            try {
                val saved = withContext(Dispatchers.IO) {
                    val now = Instant.now()
                    val end = HistoryStore.load(context, zone)?.readings?.lastOrNull()?.time
                    val own = mutableListOf<Reading>()
                    try {
                        forEachOwnGlucose(context, end?.plusSeconds(1) ?: now.minus(Duration.ofDays(AUTO_ADD_DAYS)), now) { time, mgDl ->
                            own += Reading(time, mgDl, 0)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Health Connect unavailable or not allowed to read: nothing to fill in
                    }
                    HistoryStore.append(context, own, unit, "Health Connect")
                    HistoryStore.load(context, zone)
                }
                if (saved != null) showHistory(run, saved, skipped = 0, save = false, open = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (run == importRun) sayError(R.string.msg_parse_failed, e)
            } finally {
                if (run == importRun) readingFile = false
            }
        }
    }

    /**
     * A sync added readings to the kept history, or began it: they are shown, and the chosen period and days
     * stay; a period that reached the last day reaches the new last day.
     */
    private fun showAddedReadings() {
        if (!ui.settings.historyAutoAdd || history.busy || readingFile || pendingMerge != null) return
        val changedAt = HistoryStore.changedAt(context)
        if (changedAt == historyChangedAt) return
        historyChangedAt = changedAt
        val run = importRun
        val before = history
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { HistoryStore.load(context, zone) }.getOrNull() } ?: return@launch
            if (run != importRun) return@launch
            if (!before.loaded) {
                showHistory(run, saved, skipped = 0, save = false, open = false)
                return@launch
            }
            val end = before.readings.last().time
            val added = saved.readings.filter { it.time.isAfter(end) }
            if (added.isEmpty()) return@launch
            // the syncs wrote them, so Health Connect is asked only about these
            val existing = before.existing + existingKeys(added)
            val days = withContext(Dispatchers.Default) { summarize(saved.readings, existing, ui.settings) }
            val now = history
            if (run != importRun || now.readings !== before.readings || now.busy) return@launch
            history = now.copy(
                readings = saved.readings,
                existing = existing,
                days = days,
                to = if (now.to == now.days.lastOrNull()?.date) days.last().date else now.to,
                updatedAt = saved.updatedAt,
            )
        }
    }

    /** Shows a history in the History tab, marks what Health Connect already has and keeps it for the next start. */
    private suspend fun showHistory(run: Int, saved: SavedHistory, skipped: Int, save: Boolean, open: Boolean, askHealthConnect: Boolean = true) {
        val existing = if (askHealthConnect) existingKeys(saved.readings) else emptySet()
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
            selected = if (askHealthConnect) days.filterNot { it.complete }.map { it.date }.toSet() else emptySet(),
            updatedAt = saved.updatedAt,
            marksKnown = askHealthConnect,
        )
        if (open) historyOpen = true
        if (save) withContext(Dispatchers.IO) { runCatching { HistoryStore.save(context, saved) } }
        historyChangedAt = HistoryStore.changedAt(context)
    }

    /** The link in the notice: Settings, scrolled to background sync; the notice is done. */
    fun openBackgroundSettings() {
        tab = Tab.SETTINGS
        settingsTab = SettingsTab.SYNC
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

    /** "From LibreLink's notification"; turned on without the access, Android's switch for it opens. */
    fun setLocalNotification(on: Boolean, openAccess: () -> Unit) {
        updateSettings { it.copy(localNotification = on) }
        if (on && !LibreNotificationListener.hasAccess(context)) openAccess()
    }

    /** "From Juggluco or xDrip+": the receiver for their broadcasts is on only with it. */
    fun setLocalBroadcasts(on: Boolean) {
        updateSettings { it.copy(localBroadcasts = on) }
        LocalReadings.setBroadcasts(context, on)
    }

    /** Deletes the loaded history from SugarBridge; what is in Health Connect stays. */
    fun deleteHistory() {
        importRun++   // an import still running must not bring the history back
        history = ImportUi()
        historyOpen = false
        pendingMerge = null
        // nor may the next sync begin a new one
        if (ui.settings.historyAutoAdd) updateSettings { it.copy(historyAutoAdd = false) }
        // right away, not in the background: a new import saved a moment later must not be deleted with it
        HistoryStore.clear(context)
    }

    /** Every pass over a history of years is done away from the main thread, or the screen would stop for it. */
    fun writeSelected() {
        val h = history
        if (h.selected.isEmpty() || h.busy) return
        history = h.copy(busy = true, progress = 0f)
        viewModelScope.launch {
            try {
                val chosen = withContext(Dispatchers.Default) { readingsOnDays(h.readings, h.selected, zone) }
                if (chosen.isEmpty()) return@launch
                if (healthConnectState(context) != HealthConnectState.AVAILABLE || !hasGlucosePermission(context)) {
                    say(R.string.msg_allow_hc_first)
                    return@launch
                }
                val existing = existingKeys(chosen)
                val now = Instant.now()
                // Health Connect won't take a time in the future; usually the file comes from another time zone
                val (future, toWrite) = withContext(Dispatchers.Default) {
                    chosen.count { it.time.isAfter(now) } to chosen.filter { minuteKey(it.time) !in existing && !it.time.isAfter(now) }
                }
                withContext(Dispatchers.IO) {
                    insertReadings(context, toWrite) { done ->
                        viewModelScope.launch { if (history.busy) history = history.copy(progress = done.toFloat() / toWrite.size) }
                    }
                }
                val allExisting = existingKeys(h.readings)
                val settings = ui.settings
                val days = withContext(Dispatchers.Default) { summarize(h.readings, allExisting, settings) }
                history = history.copy(existing = allExisting, days = days, selected = emptySet())
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
                val settings = ui.settings
                val days = withContext(Dispatchers.Default) { summarize(h.readings, allExisting, settings) }
                history = history.copy(existing = allExisting, days = days, selected = emptySet())
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
