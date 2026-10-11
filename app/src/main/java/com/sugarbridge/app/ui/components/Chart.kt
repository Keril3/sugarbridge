package com.sugarbridge.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.glucose.levelOf
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.rememberClockFormat
import com.sugarbridge.app.ui.rememberDateFormat
import com.sugarbridge.app.ui.screens.levelText
import com.sugarbridge.app.ui.theme.LocalNight
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Tenshi
import com.sugarbridge.app.ui.theme.color
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil

/** Where things are on the chart: shared by drawing and by touch, so a finger finds the point it is over. */
private class ChartGeometry(readings: List<Reading>, low: Double, high: Double, width: Float, height: Float, density: Density) {
    val left = with(density) { 4.dp.toPx() }
    val right = width - with(density) { 36.dp.toPx() }      // range bound labels on the right
    val top = with(density) { 10.dp.toPx() }
    val bottom = height - with(density) { 20.dp.toPx() }    // hour labels at the bottom
    val start: Instant = readings.first().time
    val end: Instant = maxOf(readings.last().time, start.plus(Duration.ofHours(1)))
    private val span = Duration.between(start, end).seconds.toFloat()
    private val values = readings.map { it.mgDl }
    val yMin = minOf(low - 20, values.min() - 10).coerceAtLeast(20.0)
    val yMax = maxOf(high + 30, values.max() + 15)

    fun x(t: Instant) = left + (right - left) * Duration.between(start, t).seconds / span
    fun y(mg: Double) = (bottom - (bottom - top) * ((mg - yMin) / (yMax - yMin))).toFloat()
}

/** The reading closest in time to a finger at [x]. */
private fun nearest(readings: List<Reading>, geometry: ChartGeometry, x: Float): Int =
    readings.indices.minBy { abs(geometry.x(readings[it].time) - x) }

/** Readings closer than this belong to one line; a longer pause (the sensor was off) breaks it. */
private fun gapAfter(readings: List<Reading>): Long {
    val steps = readings.zipWithNext { a, b -> b.time.epochSecond - a.time.epochSecond }.sorted()
    val typical = steps.getOrNull(steps.size / 2) ?: 0
    return maxOf(45 * 60L, typical * 5 / 2)
}

/** Spans of indexes without a gap inside. */
private fun segments(readings: List<Reading>, gap: Long): List<IntRange> {
    val result = mutableListOf<IntRange>()
    var start = 0
    for (i in 1 until readings.size) {
        if (readings[i].time.epochSecond - readings[i - 1].time.epochSecond > gap) {
            result += start until i
            start = i
        }
    }
    if (readings.isNotEmpty()) result += start until readings.size
    return result
}

/**
 * A chart of readings with the target range band: hours along the bottom for a day, dates for longer periods.
 * A gap in the data breaks the line. With [interactive], a finger moving along it picks the point under it,
 * which gets a guide line and a label with its time and value. [markLatest] makes the last point glow, as the
 * current value does on the home screen.
 */
@Composable
fun GlucoseChart(
    readings: List<Reading>,
    lowMgDl: Double,
    highMgDl: Double,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    interactive: Boolean = false,
    selected: Int? = null,
    onSelect: (Int) -> Unit = {},
    showDots: Boolean = false,
    markLatest: Boolean = true,
) {
    val xp = LocalXp.current
    val night = LocalNight.current
    val measurer = rememberTextMeasurer()
    // the theme's font: pixels, or the plain one in the low vision look
    val small = MaterialTheme.typography.labelSmall
    val labelStyle = remember(xp, small) { small.copy(color = xp.textMuted, fontSize = small.fontSize * 0.9f) }
    val tipStyle = MaterialTheme.typography.bodyMedium.copy(color = xp.text)
    val locale = currentLocale()
    val clock = rememberClockFormat()
    val dayFormat = rememberDateFormat("dMMM")
    val monthFormat = rememberDateFormat("MMMyy")
    val line = if (night) Color(0xFF8FC4FF) else Color(0xFF2E7FE0)
    val zone = ZoneId.systemDefault()
    val parts = remember(readings) { segments(readings, gapAfter(readings)) }
    val long = readings.size >= 2 && Duration.between(readings.first().time, readings.last().time) > Duration.ofHours(20)

    val touch = if (interactive && readings.size >= 2) {
        Modifier.pointerInput(readings, lowMgDl, highMgDl) {
            awaitEachGesture {
                val geometry = ChartGeometry(readings, lowMgDl, highMgDl, size.width.toFloat(), size.height.toFloat(), this)
                val down = awaitFirstDown()
                down.consume()
                onSelect(nearest(readings, geometry, down.position.x))
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    onSelect(nearest(readings, geometry, change.position.x))
                }
            }
        }
    } else {
        Modifier
    }
    val sized = if (height == Dp.Unspecified) modifier.fillMaxWidth() else modifier.fillMaxWidth().height(height)

    Canvas(sized.then(touch)) {
        if (readings.size < 2) return@Canvas
        val g = ChartGeometry(readings, lowMgDl, highMgDl, size.width, size.height, this)

        // the target range band and its bounds
        drawRect(Tenshi.InRange.copy(alpha = 0.10f), Offset(g.left, g.y(highMgDl)), Size(g.right - g.left, g.y(lowMgDl) - g.y(highMgDl)))
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        listOf(lowMgDl, highMgDl).forEach { mg ->
            drawLine(Tenshi.InRange.copy(alpha = 0.55f), Offset(g.left, g.y(mg)), Offset(g.right, g.y(mg)), 1.dp.toPx(), pathEffect = dash)
            val text = measurer.measure(formatGlucose(mg, unit, locale), labelStyle)
            drawText(text, topLeft = Offset(g.right + 6.dp.toPx(), g.y(mg) - text.size.height / 2))
        }

        // time marks: hours within a day, then days, weeks and months; a label that would overlap is skipped
        val dots = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx()))
        var labelEnd = Float.NEGATIVE_INFINITY
        timeMarks(g.start.atZone(zone), g.end.atZone(zone)).forEach { (at, kind) ->
            val px = g.x(at.toInstant())
            drawLine(xp.dots.copy(alpha = 0.6f), Offset(px, g.top), Offset(px, g.bottom), 1.dp.toPx(), pathEffect = dots)
            val label = when (kind) {
                MarkKind.HOUR -> at.format(clock)
                MarkKind.DAY -> at.format(dayFormat)
                MarkKind.MONTH -> at.format(monthFormat)
            }
            val text = measurer.measure(label, labelStyle)
            val x = (px - text.size.width / 2).coerceIn(0f, g.right - text.size.width)
            if (x >= labelEnd + 6.dp.toPx()) {
                drawText(text, topLeft = Offset(x, g.bottom + 4.dp.toPx()))
                labelEnd = x + text.size.width
            }
        }

        // the line and the fill under it, one piece per stretch without a gap
        parts.forEach { part ->
            if (part.first == part.last) {
                val r = readings[part.first]
                drawCircle(line, 2.dp.toPx(), Offset(g.x(r.time), g.y(r.mgDl)))
                return@forEach
            }
            val path = Path()
            part.forEach { i ->
                val r = readings[i]
                if (i == part.first) path.moveTo(g.x(r.time), g.y(r.mgDl)) else path.lineTo(g.x(r.time), g.y(r.mgDl))
            }
            val area = Path().apply {
                addPath(path)
                lineTo(g.x(readings[part.last].time), g.bottom)
                lineTo(g.x(readings[part.first].time), g.bottom)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), Color.Transparent), startY = g.top, endY = g.bottom))
            drawPath(path, line, style = Stroke(if (long) 1.8.dp.toPx() else 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (showDots) {
            readings.forEach { r -> drawCircle(levelOf(r.mgDl, lowMgDl, highMgDl).color(), 2.2.dp.toPx(), Offset(g.x(r.time), g.y(r.mgDl))) }
        }

        // the latest reading: a dot glowing in the color of its level
        if (markLatest) {
            val last = readings.last()
            val lastAt = Offset(g.x(last.time), g.y(last.mgDl))
            val lastColor = levelOf(last.mgDl, lowMgDl, highMgDl).color()
            drawCircle(lastColor.copy(alpha = 0.25f), 9.dp.toPx(), lastAt)
            drawCircle(Color.White, 5.dp.toPx(), lastAt)
            drawCircle(lastColor, 3.5.dp.toPx(), lastAt)
        }

        // the point under the finger: a guide line, a ring and a label with its time and value
        val pick = selected?.let { readings.getOrNull(it) }
        if (pick != null) {
            val at = Offset(g.x(pick.time), g.y(pick.mgDl))
            val color = levelOf(pick.mgDl, lowMgDl, highMgDl).color()
            drawLine(xp.accent, Offset(at.x, g.top), Offset(at.x, g.bottom), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            drawCircle(Color.White, 7.dp.toPx(), at)
            drawCircle(color, 5.dp.toPx(), at)
            val time = pick.time.atZone(zone)
            val stamp = if (long) time.format(dayFormat) + " " + time.format(clock) else time.format(clock)
            val label = measurer.measure(stamp + "  " + formatGlucose(pick.mgDl, unit, locale), tipStyle)
            val pad = 6.dp.toPx()
            val boxW = label.size.width + 2 * pad
            val boxH = label.size.height + 2 * pad
            val boxX = (at.x - boxW / 2).coerceIn(0f, size.width - boxW)
            val boxY = if (at.y - boxH - 12.dp.toPx() > g.top) at.y - boxH - 12.dp.toPx() else at.y + 12.dp.toPx()
            drawRoundRect(xp.windowBody, Offset(boxX, boxY), Size(boxW, boxH), CornerRadius(6.dp.toPx()))
            drawRoundRect(color, Offset(boxX, boxY), Size(boxW, boxH), CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            drawText(label, topLeft = Offset(boxX + pad, boxY + pad))
        }
    }
}

internal enum class MarkKind { HOUR, DAY, MONTH }

/**
 * Where the time marks go between [start] and [end]: every 1-6 hours within about a day, then every 1, 2, 7
 * or 14 days, and for longer periods the first of every month or of every few months, about six marks at most.
 */
internal fun timeMarks(start: ZonedDateTime, end: ZonedDateTime): List<Pair<ZonedDateTime, MarkKind>> {
    val hours = Duration.between(start, end).toMinutes() / 60.0
    val marks = mutableListOf<Pair<ZonedDateTime, MarkKind>>()
    if (hours <= 30) {
        val every = when {
            hours <= 4 -> 1
            hours <= 7 -> 2
            hours <= 13 -> 3
            else -> 6
        }
        var tick = start.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        while (!tick.isAfter(end)) {
            if (tick.hour % every == 0) marks += tick to MarkKind.HOUR
            tick = tick.plusHours(1)
        }
        return marks
    }
    val days = hours / 24
    if (days <= 100) {
        val every = when {
            days <= 8 -> 1L
            days <= 16 -> 2L
            days <= 40 -> 7L
            else -> 14L
        }
        var tick = start.toLocalDate().plusDays(1).atStartOfDay(start.zone)
        while (!tick.isAfter(end)) {
            marks += tick to MarkKind.DAY
            tick = tick.plusDays(every)
        }
        return marks
    }
    val months = Duration.between(start, end).toDays() / 30.4
    val every = maxOf(1L, ceil(months / 6).toLong())
    var tick = start.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(start.zone)
    while (!tick.isAfter(end)) {
        marks += tick to MarkKind.MONTH
        tick = tick.plusMonths(every)
    }
    return marks
}

/**
 * The chart full screen: move a finger along it to read every value. From the home screen it offers 3, 6 or
 * 12 hours ([hourChoices]); from the history it shows the period it was opened with.
 */
@Composable
fun ChartDialog(
    title: String,
    readings: List<Reading>,
    lowMgDl: Double,
    highMgDl: Double,
    unit: GlucoseUnit,
    hourChoices: List<Int>? = listOf(3, 6, 12),
    onDismiss: () -> Unit,
) {
    XpDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SkyBackground {
            var hours by rememberSaveable { mutableIntStateOf(hourChoices?.last() ?: 0) }
            var selected by remember { mutableStateOf<Int?>(null) }
            val shown = remember(readings, hours) {
                if (hourChoices == null) {
                    readings
                } else {
                    val last = readings.lastOrNull()?.time ?: Instant.now()
                    readings.filter { !it.time.isBefore(last.minus(Duration.ofHours(hours.toLong()))) }
                }
            }
            val pick = selected?.let { shown.getOrNull(it) } ?: shown.lastOrNull()
            val long = shown.size >= 2 && Duration.between(shown.first().time, shown.last().time) > Duration.ofHours(20)
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                XpWindow(title, Modifier.weight(1f)) {
                    if (hourChoices != null) {
                        ChoiceRow(
                            options = hourChoices,
                            selected = hours,
                            label = { stringResource(R.string.duration_hours, it) },
                            onSelect = {
                                hours = it
                                selected = null
                            },
                        )
                    }
                    if (pick != null) Readout(pick, lowMgDl, highMgDl, unit, withDate = long)
                    GlucoseChart(
                        shown, lowMgDl, highMgDl, unit,
                        modifier = Modifier.weight(1f).testTag("chart-full"),
                        height = Dp.Unspecified,
                        interactive = true,
                        selected = selected,
                        onSelect = { selected = it },
                        showDots = !long,
                        markLatest = hourChoices != null,
                    )
                    Hint(stringResource(R.string.chart_drag_hint))
                    SecondaryButton(stringResource(R.string.close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The value of the picked point in large type, with its time and level. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Readout(r: Reading, lowMgDl: Double, highMgDl: Double, unit: GlucoseUnit, withDate: Boolean = false) {
    val locale = currentLocale()
    val clock = rememberClockFormat()
    val day = rememberDateFormat("EEdMMMyyyy")
    val level = levelOf(r.mgDl, lowMgDl, highMgDl)
    // the level badge goes to its own line when the date leaves no room beside it
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(formatGlucose(r.mgDl, unit, locale), style = MaterialTheme.typography.displaySmall, color = level.color())
            Column {
                Text(stringResource(unit.label), style = MaterialTheme.typography.labelLarge)
                val time = r.time.atZone(ZoneId.systemDefault())
                Text(
                    if (withDate) time.format(day) + ", " + time.format(clock) else time.format(clock),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalXp.current.textMuted,
                )
            }
        }
        Pill(levelText(level), level.color())
    }
}
