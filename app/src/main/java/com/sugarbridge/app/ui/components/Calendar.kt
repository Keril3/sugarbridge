package com.sugarbridge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sugarbridge.app.R
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.rememberDateFormat
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields

private const val ALL = 0L

/**
 * Picks a period between [first] and [last]: ready-made periods, or two taps in a month calendar (the first and
 * the last day). Days with readings get a dot colored by how much of the day was in range ([marks]: day → %).
 * The month title switches to a grid of months, so a day years back is three taps away.
 */
@Composable
fun DateRangeDialog(
    title: String,
    first: LocalDate,
    last: LocalDate,
    from: LocalDate?,
    to: LocalDate?,
    marks: Map<LocalDate, Int>,
    onDismiss: () -> Unit,
    onPick: (LocalDate, LocalDate) -> Unit,
) {
    var start by rememberSaveable { mutableStateOf(from) }
    var end by rememberSaveable { mutableStateOf(to) }
    var editingEnd by rememberSaveable { mutableStateOf(false) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(to ?: last)) }
    var months by rememberSaveable { mutableStateOf(false) }   // the grid of months instead of days
    val firstMonth = YearMonth.from(first)
    val lastMonth = YearMonth.from(last)

    fun pick(day: LocalDate) {
        val s = start
        when {
            !editingEnd || s == null -> {
                start = day
                if (end == null || end!! < day) end = day
                editingEnd = true   // the next tap is the last day
            }
            day < s -> start = day
            else -> {
                end = day
                editingEnd = false
            }
        }
    }

    XpDialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        XpWindow(title, Modifier.padding(12.dp).widthIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            val presets = listOf(7L, 30L, 90L, 365L, ALL)
            fun presetStart(days: Long) = if (days == ALL) first else maxOf(first, last.minusDays(days - 1))
            ChoiceRow(
                options = presets,
                // "All" first: with a short history, 30 or 90 days also cover everything
                selected = presets.sortedBy { it != ALL }.firstOrNull { start == presetStart(it) && end == last } ?: -1L,
                label = { if (it == ALL) stringResource(R.string.history_all) else stringResource(R.string.history_last_days, it.toInt()) },
                onSelect = { days ->
                    start = presetStart(days)
                    end = last
                    editingEnd = false
                    month = lastMonth
                    months = false
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateBox(stringResource(R.string.cal_from), start, active = !editingEnd, Modifier.weight(1f)) {
                    editingEnd = false
                    start?.let { month = YearMonth.from(it) }
                    months = false
                }
                DateBox(stringResource(R.string.cal_to), end, active = editingEnd, Modifier.weight(1f)) {
                    editingEnd = true
                    end?.let { month = YearMonth.from(it) }
                    months = false
                }
            }
            MonthHeader(
                text = if (months) month.year.toString() else monthTitle(month),
                canBack = if (months) month.year > first.year else month > firstMonth,
                canForward = if (months) month.year < last.year else month < lastMonth,
                back = { month = if (months) month.minusYears(1) else month.minusMonths(1) },
                forward = { month = if (months) month.plusYears(1) else month.plusMonths(1) },
                onTitle = { months = !months },
            )
            if (months) {
                MonthGrid(month.year, firstMonth, lastMonth, start, end) {
                    month = it
                    months = false
                }
            } else {
                DayGrid(month, first, last, start, end, marks, ::pick)
            }
            Hint(stringResource(if (editingEnd) R.string.cal_hint_last else R.string.cal_hint_first))
            ButtonRow {
                PrimaryButton(stringResource(R.string.done), onClick = { start?.let { onPick(it, end ?: it) } }, enabled = start != null)
                SecondaryButton(stringResource(R.string.cancel), onClick = onDismiss)
            }
        }
    }
}

/** "From 5 Oct 2026": one end of the period; the one being chosen is outlined. */
@Composable
private fun DateBox(label: String, date: LocalDate?, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val xp = LocalXp.current
    val shape = RoundedCornerShape(8.dp)
    val format = rememberDateFormat("dMMMyyyy")
    Column(
        modifier
            .clip(shape)
            .border(if (active) 2.dp else 1.dp, if (active) LocalXp.current.buttonBorder else xp.dots, shape)
            .background(if (active) xp.accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = xp.textMuted)
        Text(date?.format(format) ?: "…", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MonthHeader(text: String, canBack: Boolean, canForward: Boolean, back: () -> Unit, forward: () -> Unit, onTitle: () -> Unit) {
    val earlier = stringResource(R.string.cal_earlier)
    val later = stringResource(R.string.cal_later)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("‹", onClick = back, enabled = canBack, modifier = Modifier.semantics { contentDescription = earlier })
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onTitle)
                .padding(vertical = 10.dp)
                .testTag("calendar-title"),
        )
        SecondaryButton("›", onClick = forward, enabled = canForward, modifier = Modifier.semantics { contentDescription = later })
    }
}

/** A month: weekday names, then the days in weeks starting on the locale's first day of the week. */
@Composable
private fun DayGrid(
    month: YearMonth,
    first: LocalDate,
    last: LocalDate,
    start: LocalDate?,
    end: LocalDate?,
    marks: Map<LocalDate, Int>,
    onDay: (LocalDate) -> Unit,
) {
    val xp = LocalXp.current
    val locale = currentLocale()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val weekdays = (0L..6L).map { firstDay.plus(it) }
    val offset = ((month.atDay(1).dayOfWeek.value - firstDay.value) + 7) % 7
    val cells = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val dayFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            weekdays.forEach { d: DayOfWeek ->
                Text(
                    d.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = xp.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            DayCell(day, enabled = day in first..last, start, end, marks[day], dayFormat) { onDay(day) }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, enabled: Boolean, start: LocalDate?, end: LocalDate?, inRange: Int?, format: DateTimeFormatter, onClick: () -> Unit) {
    val xp = LocalXp.current
    val edge = day == start || day == end
    val inside = start != null && end != null && day > start && day < end
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    edge -> LocalXp.current.buttonBorder
                    inside -> xp.accent.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = day.format(format) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                edge -> Color.White
                enabled -> xp.text
                else -> xp.textMuted.copy(alpha = 0.4f)
            },
        )
        // a dot for a day with readings: green when most of it was in range
        if (inRange != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (edge) Color.White else markColor(inRange)),
            )
        }
    }
}

private fun markColor(inRangePercent: Int): Color = when {
    inRangePercent >= 70 -> Tenshi.InRange
    inRangePercent >= 50 -> Tenshi.High
    else -> Tenshi.Low
}

/** Twelve months of a year; the ones without data can't be opened. */
@Composable
private fun MonthGrid(year: Int, firstMonth: YearMonth, lastMonth: YearMonth, start: LocalDate?, end: LocalDate?, onMonth: (YearMonth) -> Unit) {
    val xp = LocalXp.current
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..12).map { YearMonth.of(year, it) }.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    val enabled = m in firstMonth..lastMonth
                    val chosen = start != null && end != null && m >= YearMonth.from(start) && m <= YearMonth.from(end)
                    val shape = RoundedCornerShape(8.dp)
                    Text(
                        m.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = if (enabled) xp.text else xp.textMuted.copy(alpha = 0.4f),
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .border(1.dp, if (chosen) LocalXp.current.buttonBorder else xp.dots.copy(alpha = 0.5f), shape)
                            .background(if (chosen) xp.accent.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable(enabled = enabled, role = Role.Button) { onMonth(m) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/** "October 2026", capitalized as a title in every language. */
@Composable
private fun monthTitle(month: YearMonth): String {
    val locale = currentLocale()
    return month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
}
