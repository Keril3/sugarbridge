package com.sugarbridge.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sugarbridge.app.ImportUi
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.estimateGmi
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.libreview.DaySummary
import com.sugarbridge.app.libreview.dayBounds
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.components.ButtonRow
import com.sugarbridge.app.ui.components.ChoiceRow
import com.sugarbridge.app.ui.components.DottedDivider
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.Pill
import com.sugarbridge.app.ui.components.PrimaryButton
import com.sugarbridge.app.ui.components.SecondaryButton
import com.sugarbridge.app.ui.components.XpMeter
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.rememberDateFormat
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private const val ALL_DAYS = 0L

@Composable
fun HistoryScreen(vm: MainViewModel, actions: AppActions) {
    val h = vm.history
    val s = vm.ui.settings
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dayFormat = rememberDateFormat("EEdMMMyyyy")

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = if (h.loaded) 170.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SourceCard(h, actions, onClose = vm::closeHistory) }
            if (h.busy && !h.loaded) {
                item {
                    XpWindow(stringResource(R.string.history_title)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 3.dp)
                            Text(stringResource(R.string.history_reading_file), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (h.loaded) {
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
        }
        if (h.loaded) {
            ActionBar(h, onWrite = vm::writeSelected, onDelete = { confirmDelete = true }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (picking && h.loaded) {
        RangePickerDialog(h, onDismiss = { picking = false }) { from, to ->
            vm.setRange(from, to)
            picking = false
        }
    }
    if (confirmDelete) {
        Dialog(onDismissRequest = { confirmDelete = false }) {
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

@Composable
private fun SourceCard(h: ImportUi, actions: AppActions, onClose: () -> Unit) {
    val dateFormat = rememberDateFormat("dMMMyyyy")
    XpWindow(stringResource(R.string.history_title)) {
        if (h.loaded) {
            Text(stringResource(R.string.history_loaded, h.source, h.readings.size), style = MaterialTheme.typography.bodyLarge)
            val first = h.days.first().date.format(dateFormat)
            val last = h.days.last().date.format(dateFormat)
            val unit = stringResource(h.unit.label)
            Hint(
                if (h.skipped > 0) stringResource(R.string.history_loaded_range_skipped, first, last, unit, h.skipped)
                else stringResource(R.string.history_loaded_range, first, last, unit)
            )
            ButtonRow {
                SecondaryButton(stringResource(R.string.history_other_file), onClick = actions::pickCsv, enabled = !h.busy)
                SecondaryButton(stringResource(R.string.close), onClick = onClose, enabled = !h.busy)
            }
        } else {
            Text(stringResource(R.string.history_intro), style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(stringResource(R.string.history_open_libreview), onClick = actions::openLibreView, modifier = Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.history_pick_csv), onClick = actions::pickCsv, modifier = Modifier.fillMaxWidth())
            DottedDivider()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Hint(stringResource(R.string.history_step_1))
                Hint(stringResource(R.string.history_step_2))
                Hint(stringResource(R.string.history_step_3))
                Hint(stringResource(R.string.history_step_browser))
            }
        }
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
            modifier = Modifier.fillMaxWidth(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(h: ImportUi, onDismiss: () -> Unit, onPick: (LocalDate, LocalDate) -> Unit) {
    val first = h.days.first().date
    val last = h.days.last().date
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = h.from?.toUtcMillis(),
        initialSelectedEndDateMillis = h.to?.toUtcMillis(),
        initialDisplayedMonthMillis = (h.to ?: last).toUtcMillis(),
        yearRange = first.year..last.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toUtcDate() in first..last
            override fun isSelectableYear(year: Int) = year in first.year..last.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val a = state.selectedStartDateMillis
                    if (a != null) onPick(a.toUtcDate(), (state.selectedEndDateMillis ?: a).toUtcDate())
                },
                enabled = state.selectedStartDateMillis != null,
            ) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = { Text(stringResource(R.string.history_pick_title), Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}

// DatePicker works with UTC midnight of the chosen day
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
