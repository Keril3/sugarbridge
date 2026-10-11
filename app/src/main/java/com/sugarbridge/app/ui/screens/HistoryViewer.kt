package com.sugarbridge.app.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sugarbridge.app.ImportUi
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.between
import com.sugarbridge.app.glucose.downsample
import com.sugarbridge.app.glucose.estimateGmi
import com.sugarbridge.app.libreview.dayBounds
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.ui.components.ChartDialog
import com.sugarbridge.app.ui.components.ChoiceRow
import com.sugarbridge.app.ui.components.DateRangeDialog
import com.sugarbridge.app.ui.components.GlucoseChart
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.SecondaryButton
import com.sugarbridge.app.ui.components.XpWindow
import com.sugarbridge.app.ui.rememberDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

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
internal fun HistoryViewer(h: ImportUi, s: AppSettings) {
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
