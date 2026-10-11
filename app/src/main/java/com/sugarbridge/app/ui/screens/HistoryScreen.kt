package com.sugarbridge.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sugarbridge.app.ImportUi
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.PendingMerge
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.estimateGmi
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.libreview.DaySummary
import com.sugarbridge.app.libreview.MergeMode
import com.sugarbridge.app.libreview.dayBounds
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.components.BottomSpace
import com.sugarbridge.app.ui.components.ButtonRow
import com.sugarbridge.app.ui.components.ChoiceRow
import com.sugarbridge.app.ui.components.DateRangeDialog
import com.sugarbridge.app.ui.components.DottedDivider
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.Pill
import com.sugarbridge.app.ui.components.PrimaryButton
import com.sugarbridge.app.ui.components.ScreenColumn
import com.sugarbridge.app.ui.components.SecondaryButton
import com.sugarbridge.app.ui.components.XpDialog
import com.sugarbridge.app.ui.components.XpMeter
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.rememberDateFormat
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val ALL_DAYS = 0L

/**
 * The History tab. Its start page loads exports and keeps the loaded history one tap away; the loaded history
 * itself (chart, period, days to write) opens over it, and "Back" returns to the start page.
 */
@Composable
fun HistoryScreen(vm: MainViewModel, actions: AppActions) {
    val h = vm.history
    if (h.loaded && vm.historyOpen) LoadedHistory(vm) else HistoryStart(vm, actions)
    vm.pendingMerge?.let { MergeDialog(it, h, onChoose = vm::applyMerge, onCancel = vm::cancelMerge) }
}

/** The start page: get an export from LibreView or a file, open or update the loaded history, delete it. */
@Composable
private fun HistoryStart(vm: MainViewModel, actions: AppActions) {
    val h = vm.history
    var confirmDeleteAll by remember { mutableStateOf(false) }
    ScreenColumn(Modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(2.dp))
        XpWindow(stringResource(R.string.history_title)) {
            if (vm.readingFile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.history_reading_file), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (h.loaded) {
                HistorySummary(h)
                PrimaryButton(stringResource(R.string.history_view_loaded), onClick = { vm.historyOpen = true }, modifier = Modifier.fillMaxWidth().testTag("view-loaded"))
                DottedDivider()
                Text(stringResource(R.string.history_update_intro), style = MaterialTheme.typography.bodyMedium)
                SecondaryButton(stringResource(R.string.history_open_libreview), onClick = actions::openLibreView, enabled = !vm.readingFile, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.history_pick_csv), onClick = actions::pickCsv, enabled = !vm.readingFile, modifier = Modifier.fillMaxWidth())
                DottedDivider()
                SecondaryButton(stringResource(R.string.history_delete_all), onClick = { confirmDeleteAll = true }, danger = true, enabled = !h.busy, modifier = Modifier.fillMaxWidth())
            } else {
                Text(stringResource(R.string.history_intro), style = MaterialTheme.typography.bodyMedium)
                PrimaryButton(stringResource(R.string.history_open_libreview), onClick = actions::openLibreView, enabled = !vm.readingFile, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.history_pick_csv), onClick = actions::pickCsv, enabled = !vm.readingFile, modifier = Modifier.fillMaxWidth())
                DottedDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Hint(stringResource(R.string.history_step_1))
                    Hint(stringResource(R.string.history_step_2))
                    Hint(stringResource(R.string.history_step_3))
                    Hint(stringResource(R.string.history_step_browser))
                }
            }
        }
        BottomSpace()
    }
    if (confirmDeleteAll) {
        XpDialog(onDismissRequest = { confirmDeleteAll = false }) {
            XpWindow(stringResource(R.string.history_delete_all_title)) {
                Text(stringResource(R.string.history_delete_all_text), style = MaterialTheme.typography.bodyMedium)
                ButtonRow {
                    SecondaryButton(stringResource(R.string.delete), onClick = { confirmDeleteAll = false; vm.deleteHistory() }, danger = true)
                    SecondaryButton(stringResource(R.string.cancel), onClick = { confirmDeleteAll = false })
                }
            }
        }
    }
}

/** "LibreView · readings: 19557", the dates it covers, the unit and when it was last updated. */
@Composable
private fun HistorySummary(h: ImportUi) {
    val dateFormat = rememberDateFormat("dMMMyyyy")
    Text(stringResource(R.string.history_loaded, h.source, h.readings.size), style = MaterialTheme.typography.bodyLarge)
    val first = h.days.first().date.format(dateFormat)
    val last = h.days.last().date.format(dateFormat)
    val unit = stringResource(h.unit.label)
    Hint(
        if (h.skipped > 0) stringResource(R.string.history_loaded_range_skipped, first, last, unit, h.skipped)
        else stringResource(R.string.history_loaded_range, first, last, unit)
    )
    h.updatedAt?.let { Hint(stringResource(R.string.history_loaded_updated, it.atZone(ZoneId.systemDefault()).format(dateFormat))) }
}

/** The loaded history: its chart, the period and the days to write to Health Connect. "Back" leads to the start page. */
@Composable
private fun LoadedHistory(vm: MainViewModel) {
    val h = vm.history
    val s = vm.ui.settings
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dayFormat = rememberDateFormat("EEdMMMyyyy")
    BackHandler { vm.historyOpen = false }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                XpWindow(stringResource(R.string.history_title)) {
                    HistorySummary(h)
                    SecondaryButton(stringResource(R.string.history_back), onClick = { vm.historyOpen = false }, modifier = Modifier.testTag("history-back"))
                }
            }
            item { HistoryViewer(h, s) }
            item { PeriodCard(h, s, onPick = { picking = true }, onQuick = vm::quickRange) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.history_days), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    TextButton(onClick = vm::selectMissing) { Text(stringResource(R.string.history_select_new)) }
                    TextButton(onClick = vm::selectAll) { Text(stringResource(R.string.history_select_all)) }
                    TextButton(onClick = vm::selectNone) { Text(stringResource(R.string.history_select_none)) }
                }
            }
            items(h.visibleDays.asReversed(), key = { it.date.toEpochDay() }) { day ->
                DayRow(day, dayFormat, selected = day.date in h.selected, s = s, enabled = !h.busy) { vm.toggleDay(day.date) }
            }
        }
        ActionBar(h, onWrite = vm::writeSelected, onDelete = { confirmDelete = true }, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (picking) {
        DateRangeDialog(
            stringResource(R.string.history_pick_title),
            first = h.days.first().date,
            last = h.days.last().date,
            from = h.from,
            to = h.to,
            marks = remember(h.days) { h.days.associate { it.date to it.inRangePercent } },
            onDismiss = { picking = false },
        ) { from, to ->
            vm.setRange(from, to)
            picking = false
        }
    }
    if (confirmDelete) {
        XpDialog(onDismissRequest = { confirmDelete = false }) {
            XpWindow(stringResource(R.string.history_delete_title)) {
                Text(stringResource(R.string.history_delete_text, h.selected.size), style = MaterialTheme.typography.bodyMedium)
                ButtonRow {
                    SecondaryButton(stringResource(R.string.delete), onClick = { confirmDelete = false; vm.deleteSelected() }, danger = true)
                    SecondaryButton(stringResource(R.string.cancel), onClick = { confirmDelete = false })
                }
            }
        }
    }
}

/**
 * A new export while a history is loaded: how the two compare, and the choice to add the new readings, add
 * them over the differing ones, or replace the whole history with the new file.
 */
@Composable
private fun MergeDialog(p: PendingMerge, h: ImportUi, onChoose: (MergeMode) -> Unit, onCancel: () -> Unit) {
    val dateFormat = rememberDateFormat("dMMMyyyy")
    val zone = ZoneId.systemDefault()
    XpDialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        XpWindow(stringResource(R.string.merge_title), Modifier.padding(12.dp).widthIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            val from = p.plan.from?.atZone(zone)?.format(dateFormat) ?: "—"
            val to = p.plan.to?.atZone(zone)?.format(dateFormat) ?: "—"
            Text(stringResource(R.string.merge_file, p.plan.incoming, from, to), style = MaterialTheme.typography.bodyLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MergeCount(stringResource(R.string.merge_new), p.plan.added, Tenshi.InRange)
                MergeCount(stringResource(R.string.merge_same), p.plan.same, LocalXp.current.textMuted)
                MergeCount(stringResource(R.string.merge_changed), p.plan.changed, Tenshi.High)
                MergeCount(stringResource(R.string.merge_only_old), p.plan.onlyOld, LocalXp.current.textMuted)
            }
            PrimaryButton(stringResource(R.string.merge_add), onClick = { onChoose(MergeMode.ADD) }, modifier = Modifier.fillMaxWidth().testTag("merge-add"))
            SecondaryButton(stringResource(R.string.merge_overwrite), onClick = { onChoose(MergeMode.OVERWRITE) }, enabled = p.plan.changed > 0, modifier = Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.merge_replace), onClick = { onChoose(MergeMode.REPLACE) }, modifier = Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.fillMaxWidth())
            Hint(stringResource(R.string.merge_hint))
        }
    }
}

@Composable
private fun MergeCount(label: String, count: Int, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun PeriodCard(h: ImportUi, s: AppSettings, onPick: () -> Unit, onQuick: (Long?) -> Unit) {
    val locale = currentLocale()
    val dateFormat = rememberDateFormat("dMMMyyyy")
    XpWindow(stringResource(R.string.history_period)) {
        SecondaryButton(
            "${h.from?.format(dateFormat) ?: "…"} — ${h.to?.format(dateFormat) ?: "…"}",
            onClick = onPick,
            enabled = !h.busy,
            modifier = Modifier.fillMaxWidth().testTag("sync-period"),
        )
        val options = listOf(ALL_DAYS, 90L, 30L, 7L)
        fun days(option: Long) = if (option == ALL_DAYS) null else option
        ChoiceRow(
            options = options,
            selected = options.firstOrNull { matchesQuick(h, days(it)) } ?: -1L,
            label = { if (it == ALL_DAYS) stringResource(R.string.history_all) else stringResource(R.string.history_last_days, it.toInt()) },
            onSelect = { onQuick(days(it)) },
            enabled = !h.busy,
        )
        val visible = h.visibleDays
        if (visible.isNotEmpty()) {
            val count = visible.sumOf { it.count }
            val avg = visible.sumOf { it.avgMgDl * it.count } / count
            val inRange = visible.sumOf { it.inRangePercent * it.count } / count
            Hint(stringResource(R.string.history_period_summary, visible.size, count, formatGlucose(avg, s.unit, locale), stringResource(s.unit.label), inRange))
            val gmi = remember(h.readings, h.from, h.to) {
                val zone = ZoneId.systemDefault()
                estimateGmi(h.readings, dayBounds(visible.first().date, zone).first, dayBounds(visible.last().date, zone).second)
            }
            if (gmi != null) {
                Hint(
                    if (gmi.enough) stringResource(R.string.history_period_gmi, gmi.percent, gmi.mmolPerMol)
                    else stringResource(R.string.history_period_gmi_low, gmi.percent, gmi.mmolPerMol, (gmi.coverage * 100).roundToInt())
                )
            }
        }
    }
}

private fun matchesQuick(h: ImportUi, days: Long?): Boolean {
    val first = h.days.firstOrNull()?.date ?: return false
    val last = h.days.last().date
    val from = if (days == null) first else maxOf(first, last.minusDays(days - 1))
    return h.from == from && h.to == last
}

@Composable
private fun DayRow(day: DaySummary, format: DateTimeFormatter, selected: Boolean, s: AppSettings, enabled: Boolean, onToggle: () -> Unit) {
    val xp = LocalXp.current
    val locale = currentLocale()
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Tenshi.ButtonBorder else xp.windowBorder, shape)
            .background(xp.windowBody.copy(alpha = if (selected) 1f else 0.85f))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Tenshi.ButtonBorder, uncheckedColor = xp.dots),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(day.date.format(format), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(
                    R.string.history_day_summary,
                    day.count,
                    formatGlucose(day.avgMgDl, s.unit, locale),
                    formatGlucose(day.minMgDl, s.unit, locale),
                    formatGlucose(day.maxMgDl, s.unit, locale),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = xp.textMuted,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                XpMeter(day.inRangePercent / 100f, Modifier.width(110.dp), color = Tenshi.InRange)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.history_day_in_range, day.inRangePercent), style = MaterialTheme.typography.labelSmall, color = xp.textMuted)
            }
        }
        Spacer(Modifier.width(6.dp))
        when {
            day.complete -> Pill(stringResource(R.string.history_in_hc), Tenshi.InRange)
            day.alreadyInHealthConnect > 0 -> Pill(stringResource(R.string.history_partly), xp.accent)
        }
    }
}

@Composable
private fun ActionBar(h: ImportUi, onWrite: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val xp = LocalXp.current
    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, spotColor = xp.shadow)
            .clip(shape)
            .border(2.dp, xp.windowBorder, shape)
            .background(xp.windowBody)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (h.selected.isEmpty()) stringResource(R.string.history_select_prompt)
            else stringResource(R.string.history_selected, h.selected.size, h.selectedReadings),
            style = MaterialTheme.typography.bodyMedium,
        )
        h.progress?.let { p -> XpMeter(p, Modifier.fillMaxWidth()) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                stringResource(R.string.history_write),
                onClick = onWrite,
                enabled = h.selected.isNotEmpty(),
                loading = h.busy,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(stringResource(R.string.delete), onClick = onDelete, enabled = h.selected.isNotEmpty() && !h.busy, danger = true)
        }
    }
}
