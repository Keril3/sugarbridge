package com.sugarbridge.app.ui.screens

import androidx.annotation.StringRes
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.ImportUi
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.between
import com.sugarbridge.app.glucose.downsample
import com.sugarbridge.app.glucose.estimateGmi
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.libreview.DaySummary
import com.sugarbridge.app.libreview.dayBounds
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.components.ButtonRow
import com.sugarbridge.app.ui.components.ChartDialog
import com.sugarbridge.app.ui.components.ChoiceRow
import com.sugarbridge.app.ui.components.DateRangeDialog
import com.sugarbridge.app.ui.components.DottedDivider
import com.sugarbridge.app.ui.components.GlucoseChart
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.Pill
import com.sugarbridge.app.ui.components.PrimaryButton
import com.sugarbridge.app.ui.components.SecondaryButton
import com.sugarbridge.app.ui.components.XpDialog
import com.sugarbridge.app.ui.components.XpMeter
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.rememberDateFormat
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
            if (h.loaded) item { HistoryViewer(h, s) }
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

/** How much of the history the chart shows at once; CUSTOM is a period picked in the calendar. */
private enum class ViewSpan(val days: Long, @param:StringRes val label: Int) {
    DAY(1, R.string.view_day),
    WEEK(7, R.string.view_week),
    MONTH(30, R.string.view_month),
    QUARTER(91, R.string.view_quarter),
    ALL(0, R.string.view_all),
    CUSTOM(0, R.string.view_custom),
}

/**
 * The imported history as a chart: a day, a week, a month, three months, all of it or a picked period, with
 * arrows to step back and forth in time. A tap opens it full screen, where a finger reads every value.
 */
@Composable
private fun HistoryViewer(h: ImportUi, s: AppSettings) {
    val first = h.days.first().date
    val last = h.days.last().date
    val zone = ZoneId.systemDefault()
    var span by rememberSaveable { mutableStateOf(ViewSpan.WEEK) }
    var end by rememberSaveable(first, last) { mutableStateOf(last) }
    var custom by rememberSaveable(first, last) { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var picking by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }

    val (from, to) = when {
        span == ViewSpan.CUSTOM && custom != null -> custom!!
        span == ViewSpan.ALL -> first to last
        else -> maxOf(first, end.minusDays(maxOf(span.days, 1) - 1)) to end
    }
    val length = ChronoUnit.DAYS.between(from, to) + 1
    val shown = remember(h.readings, from, to) { h.readings.between(dayBounds(from, zone).first, dayBounds(to, zone).second) }
    val drawn = remember(shown) { downsample(shown, 700) }

    fun shift(days: Long) {
        if (span == ViewSpan.CUSTOM && custom != null) {
            val newTo = (to.plusDays(days)).coerceIn(first, last)
            custom = maxOf(first, newTo.minusDays(length - 1)) to newTo
        } else {
            end = end.plusDays(days).coerceIn(first, last)
        }
    }

    XpWindow(stringResource(R.string.view_title)) {
        ChoiceRow(
            options = ViewSpan.entries.toList(),
            selected = span,
            label = { stringResource(it.label) },
            onSelect = {
                if (it == ViewSpan.CUSTOM) picking = true else span = it
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val earlier = stringResource(R.string.cal_earlier)
            val later = stringResource(R.string.cal_later)
            SecondaryButton("‹", onClick = { shift(-length) }, enabled = span != ViewSpan.ALL && from > first, modifier = Modifier.semantics { contentDescription = earlier })
            Text(
                periodLabel(from, to),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { picking = true }
                    .padding(vertical = 8.dp)
                    .testTag("view-period"),
            )
            SecondaryButton("›", onClick = { shift(length) }, enabled = span != ViewSpan.ALL && to < last, modifier = Modifier.semantics { contentDescription = later })
        }
        if (drawn.size >= 2) {
            GlucoseChart(
                drawn, s.lowMgDl, s.highMgDl, s.unit,
                modifier = Modifier.clickable { full = true }.testTag("history-chart"),
                height = 190.dp,
                markLatest = false,
            )
            DayStats(shown, s)
            val gmi = remember(shown, from, to) {
                if (length >= 7) estimateGmi(shown, dayBounds(from, zone).first, dayBounds(to, zone).second) else null
            }
            Hint(
                buildList {
                    add(stringResource(R.string.view_readings, shown.size))
                    if (gmi != null) {
                        add(
                            if (gmi.enough) stringResource(R.string.history_period_gmi, gmi.percent, gmi.mmolPerMol)
                            else stringResource(R.string.history_period_gmi_low, gmi.percent, gmi.mmolPerMol, (gmi.coverage * 100).roundToInt())
                        )
                    }
                    add(stringResource(R.string.chart_hint))
                }.joinToString("\n")
            )
        } else {
            Hint(stringResource(R.string.view_no_data))
        }
    }

    if (picking) {
        DateRangeDialog(
            stringResource(R.string.view_pick_title),
            first = first,
            last = last,
            from = from,
            to = to,
            marks = remember(h.days) { h.days.associate { it.date to it.inRangePercent } },
            onDismiss = { picking = false },
        ) { a, b ->
            custom = a to b
            span = ViewSpan.CUSTOM
            picking = false
        }
    }
    if (full) {
        ChartDialog(
            periodLabel(from, to),
            remember(shown) { downsample(shown, 1500) },
            s.lowMgDl, s.highMgDl, s.unit,
            hourChoices = null,
        ) { full = false }
    }
}

/** "Sat, 11 Oct 2026" for a day, "5 Oct – 11 Oct 2026" for a period. */
@Composable
private fun periodLabel(from: LocalDate, to: LocalDate): String {
    val day = rememberDateFormat("EEdMMMyyyy")
    val short = rememberDateFormat("dMMM")
    val full = rememberDateFormat("dMMMyyyy")
    return when {
        from == to -> from.format(day)
        from.year == to.year -> from.format(short) + " – " + to.format(full)
        else -> from.format(full) + " – " + to.format(full)
    }
}
