package com.sugarbridge.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.formatGlucose
import com.sugarbridge.app.glucose.levelOf
import com.sugarbridge.app.ui.currentLocale
import com.sugarbridge.app.ui.rememberClockFormat
import com.sugarbridge.app.ui.screens.levelText
import com.sugarbridge.app.ui.theme.LocalNight
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.Monocraft
import com.sugarbridge.app.ui.theme.Tenshi
import com.sugarbridge.app.ui.theme.color
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

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

/**
 * A chart of the latest readings with the target range band. With [interactive], a finger moving along it
 * picks the point under it, which gets a guide line and a label with its time and value.
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
) {
    val xp = LocalXp.current
    val night = LocalNight.current
    val measurer = rememberTextMeasurer()
    val labelStyle = remember(xp) { TextStyle(color = xp.textMuted, fontSize = 10.sp, fontFamily = Monocraft) }
    val tipStyle = remember(xp) { TextStyle(color = xp.text, fontSize = 13.sp, fontFamily = Monocraft) }
    val locale = currentLocale()
    val clock = rememberClockFormat()
    val line = if (night) Color(0xFF8FC4FF) else Color(0xFF2E7FE0)
    val zone = ZoneId.systemDefault()

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

        // hour labels: every 3 hours on a 12-hour chart, more often on a shorter one
        val hours = Duration.between(g.start, g.end).toHours()
        val every = when {
            hours <= 4 -> 1
            hours <= 7 -> 2
            else -> 3
        }
        var tick = g.start.atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
        val dots = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx()))
        while (!tick.toInstant().isAfter(g.end)) {
            if (tick.hour % every == 0) {
                val px = g.x(tick.toInstant())
                drawLine(xp.dots.copy(alpha = 0.6f), Offset(px, g.top), Offset(px, g.bottom), 1.dp.toPx(), pathEffect = dots)
                val text = measurer.measure(tick.format(clock), labelStyle)
                drawText(text, topLeft = Offset((px - text.size.width / 2).coerceIn(0f, g.right - text.size.width), g.bottom + 4.dp.toPx()))
            }
            tick = tick.plusHours(1)
        }

        // the line and the fill under it
        val path = Path()
        readings.forEachIndexed { i, r ->
            if (i == 0) path.moveTo(g.x(r.time), g.y(r.mgDl)) else path.lineTo(g.x(r.time), g.y(r.mgDl))
        }
        val area = Path().apply {
            addPath(path)
            lineTo(g.x(readings.last().time), g.bottom)
            lineTo(g.x(readings.first().time), g.bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), Color.Transparent), startY = g.top, endY = g.bottom))
        drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (showDots) {
            readings.forEach { r -> drawCircle(levelOf(r.mgDl, lowMgDl, highMgDl).color(), 2.2.dp.toPx(), Offset(g.x(r.time), g.y(r.mgDl))) }
        }

        // the latest reading: a dot glowing in the color of its level
        val last = readings.last()
        val lastAt = Offset(g.x(last.time), g.y(last.mgDl))
        val lastColor = levelOf(last.mgDl, lowMgDl, highMgDl).color()
        drawCircle(lastColor.copy(alpha = 0.25f), 9.dp.toPx(), lastAt)
        drawCircle(Color.White, 5.dp.toPx(), lastAt)
        drawCircle(lastColor, 3.5.dp.toPx(), lastAt)

        // the point under the finger: a guide line, a ring and a label with its time and value
        val pick = selected?.let { readings.getOrNull(it) }
        if (pick != null) {
            val at = Offset(g.x(pick.time), g.y(pick.mgDl))
            val color = levelOf(pick.mgDl, lowMgDl, highMgDl).color()
            drawLine(xp.accent, Offset(at.x, g.top), Offset(at.x, g.bottom), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            drawCircle(Color.White, 7.dp.toPx(), at)
            drawCircle(color, 5.dp.toPx(), at)
            val label = measurer.measure("${pick.time.atZone(zone).format(clock)}  ${formatGlucose(pick.mgDl, unit, locale)}", tipStyle)
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

/**
 * The chart full screen: pick 3, 6 or 12 hours and move a finger along it to read every value.
 * Opens from the chart on the home screen.
 */
@Composable
fun ChartDialog(
    title: String,
    readings: List<Reading>,
    lowMgDl: Double,
    highMgDl: Double,
    unit: GlucoseUnit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SkyBackground {
            var hours by rememberSaveable { mutableIntStateOf(12) }
            var selected by remember { mutableStateOf<Int?>(null) }
            val shown = remember(readings, hours) {
                val last = readings.lastOrNull()?.time ?: Instant.now()
                readings.filter { !it.time.isBefore(last.minus(Duration.ofHours(hours.toLong()))) }
            }
            val pick = selected?.let { shown.getOrNull(it) } ?: shown.lastOrNull()
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
                XpWindow(title, Modifier.weight(1f)) {
                    ChoiceRow(
                        options = listOf(3, 6, 12),
                        selected = hours,
                        label = { stringResource(R.string.duration_hours, it) },
                        onSelect = {
                            hours = it
                            selected = null
                        },
                    )
                    if (pick != null) Readout(pick, lowMgDl, highMgDl, unit)
                    GlucoseChart(
                        shown, lowMgDl, highMgDl, unit,
                        modifier = Modifier.weight(1f).testTag("chart-full"),
                        height = Dp.Unspecified,
                        interactive = true,
                        selected = selected,
                        onSelect = { selected = it },
                        showDots = true,
                    )
                    Hint(stringResource(R.string.chart_drag_hint))
                    SecondaryButton(stringResource(R.string.close), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The value of the picked point in large type, with its time and level. */
@Composable
private fun Readout(r: Reading, lowMgDl: Double, highMgDl: Double, unit: GlucoseUnit) {
    val locale = currentLocale()
    val clock = rememberClockFormat()
    val level = levelOf(r.mgDl, lowMgDl, highMgDl)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(formatGlucose(r.mgDl, unit, locale), style = MaterialTheme.typography.displaySmall, color = level.color())
        Column {
            Text(stringResource(unit.label), style = MaterialTheme.typography.labelLarge)
            Text(r.time.atZone(ZoneId.systemDefault()).format(clock), style = MaterialTheme.typography.bodySmall, color = LocalXp.current.textMuted)
        }
        Spacer(Modifier.width(4.dp))
        Pill(levelText(level), level.color())
    }
}
