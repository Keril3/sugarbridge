package com.sugarbridge.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.ImportUi
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.libreview.DaySummary
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.ui.AppActions
import com.sugarbridge.app.ui.components.ButtonRow
import com.sugarbridge.app.ui.components.CardTitle
import com.sugarbridge.app.ui.components.ChoiceRow
import com.sugarbridge.app.ui.components.GradientButton
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.Pill
import com.sugarbridge.app.ui.components.RibbonDivider
import com.sugarbridge.app.ui.components.SoftButton
import com.sugarbridge.app.ui.components.TenshiCard
import com.sugarbridge.app.ui.theme.Tenshi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private const val ALL_DAYS = 0L

@Composable
fun HistoryScreen(vm: MainViewModel, actions: AppActions) {
    val h = vm.history
    val s = vm.ui.settings
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dayFormat = remember(Locale.getDefault()) { DateTimeFormatter.ofPattern("EE, d MMM yyyy", Locale.getDefault()) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (h.loaded) 170.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SourceCard(h, actions, onClose = vm::closeHistory) }
            if (h.busy && !h.loaded) {
                item {
                    TenshiCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 3.dp)
                            Text("Читаю историю…", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (h.loaded) {
                item { PeriodCard(h, s, onPick = { picking = true }, onQuick = vm::quickRange) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Дни", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 4.dp))
                        TextButton(onClick = vm::selectMissing) { Text("Новые") }
                        TextButton(onClick = vm::selectAll) { Text("Все") }
                        TextButton(onClick = vm::selectNone) { Text("Ничего") }
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
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить из Health Connect?") },
            text = {
                Text(
                    "Все записи сахара от SugarBridge за выбранные дни (${h.selected.size}) пропадут из Health Connect и Samsung Health — " +
                        "и из истории LibreView, и полученные через LibreLinkUp. Данные в LibreView не тронем."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteSelected() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

@Composable
private fun SourceCard(h: ImportUi, actions: AppActions, onClose: () -> Unit) {
    TenshiCard {
        CardTitle("Вся история из LibreView ☁")
        if (h.loaded) {
            Text("${h.source}: ${h.readings.size} замеров", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Hint(
                "С ${h.days.first().date.format(SHORT)} по ${h.days.last().date.format(SHORT)} · в файле ${h.unit.label}" +
                    if (h.skipped > 0) " · пропущено строк: ${h.skipped}" else ""
            )
            ButtonRow {
                SoftButton("Другой файл", onClick = actions::pickCsv, enabled = !h.busy)
                SoftButton("Закрыть", onClick = onClose, enabled = !h.busy)
            }
        } else {
            Text(
                "LibreLinkUp помнит только 12 часов. Вся история хранится в LibreView: скачайте её и выберите, какие дни отправить в Samsung Health.",
                style = MaterialTheme.typography.bodyMedium,
            )
            GradientButton("Открыть LibreView", onClick = actions::openLibreView, modifier = Modifier.fillMaxWidth())
            SoftButton("Выбрать скачанный CSV", onClick = actions::pickCsv, modifier = Modifier.fillMaxWidth())
            RibbonDivider()
            Hint(
                "1. Войдите аккаунтом LibreLink — тем, что в приложении с датчиком, а не LibreLinkUp.\n" +
                    "2. «История глюкозы» → «Загрузить данные о глюкозе».\n" +
                    "3. Отметьте «Я не робот» и нажмите «Загрузить» — SugarBridge заберёт файл сам.\n" +
                    "Скачали CSV в браузере? Откройте его через SugarBridge или выберите кнопкой выше."
            )
        }
    }
}

@Composable
private fun PeriodCard(h: ImportUi, s: AppSettings, onPick: () -> Unit, onQuick: (Long?) -> Unit) {
    TenshiCard {
        CardTitle("Период")
        SoftButton(
            "${h.from?.format(SHORT) ?: "…"} — ${h.to?.format(SHORT) ?: "…"}",
            onClick = onPick,
            enabled = !h.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        val options = listOf(ALL_DAYS, 90L, 30L, 7L)
        fun days(option: Long) = if (option == ALL_DAYS) null else option
        ChoiceRow(
            options = options,
            selected = options.firstOrNull { matchesQuick(h, days(it)) } ?: -1L,
            label = { if (it == ALL_DAYS) "Вся история" else "$it дн." },
            onSelect = { onQuick(days(it)) },
            enabled = !h.busy,
        )
        val visible = h.visibleDays
        if (visible.isNotEmpty()) {
            val count = visible.sumOf { it.count }
            val avg = visible.sumOf { it.avgMgDl * it.count } / count
            val inRange = visible.sumOf { it.inRangePercent * it.count } / count
            Hint("В периоде: ${visible.size} дн., $count замеров · среднее ${formatGlucose(avg, s.unit)} ${s.unit.label} · в диапазоне $inRange%")
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
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (selected) 0.95f else 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, onClick = onToggle),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
            Column(Modifier.weight(1f)) {
                Text(day.date.format(format), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${day.count} замеров · ср. ${formatGlucose(day.avgMgDl, s.unit)} · " +
                        "${formatGlucose(day.minMgDl, s.unit)}–${formatGlucose(day.maxMgDl, s.unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                InRangeBar(day.inRangePercent)
            }
            Spacer(Modifier.width(8.dp))
            when {
                day.complete -> Pill("✓ в HC", Tenshi.InRange)
                day.alreadyInHealthConnect > 0 -> Pill("частично", Tenshi.Lavender)
            }
        }
    }
}

/** Полоска «сколько времени в диапазоне». */
@Composable
private fun InRangeBar(percent: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(120.dp).height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.fillMaxWidth(percent / 100f).height(6.dp).clip(CircleShape).background(Tenshi.InRange))
        }
        Spacer(Modifier.width(6.dp))
        Text("$percent% в диапазоне", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionBar(h: ImportUi, onWrite: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (h.selected.isEmpty()) "Отметьте дни, которые нужны в Samsung Health" else "Выбрано: ${h.selected.size} дн. · ${h.selectedReadings} замеров",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            h.progress?.let { p ->
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GradientButton(
                    "Записать в Health Connect",
                    onClick = onWrite,
                    enabled = h.selected.isNotEmpty(),
                    loading = h.busy,
                    modifier = Modifier.weight(1f),
                )
                SoftButton("Удалить", onClick = onDelete, enabled = h.selected.isNotEmpty() && !h.busy, danger = true)
            }
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
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        DateRangePicker(
            state = state,
            modifier = Modifier.weight(1f),
            title = { Text("Какие дни синхронизировать", Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}

// DatePicker работает с полуночью UTC выбранного дня
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
