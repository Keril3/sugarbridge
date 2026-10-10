package com.sugarbridge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sugarbridge.app.GlucoseUnit
import com.sugarbridge.app.Reading
import com.sugarbridge.app.formatGlucose
import com.sugarbridge.app.levelOf
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** График последних замеров с полосой целевого диапазона. */
@Composable
fun GlucoseChart(
    readings: List<Reading>,
    lowMgDl: Double,
    highMgDl: Double,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = remember(labelColor) { TextStyle(color = labelColor, fontSize = 10.sp, fontFamily = Nunito) }
    val hours = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        if (readings.size < 2) return@Canvas
        val left = 4.dp.toPx()
        val right = size.width - 34.dp.toPx()   // справа подписи порогов
        val top = 8.dp.toPx()
        val bottom = size.height - 18.dp.toPx() // снизу подписи часов

        val start = readings.first().time
        val end = maxOf(readings.last().time, start.plus(Duration.ofHours(1)))
        val span = Duration.between(start, end).seconds.toFloat()
        val values = readings.map { it.mgDl }
        val yMin = minOf(lowMgDl - 20, values.min() - 10).coerceAtLeast(20.0)
        val yMax = maxOf(highMgDl + 30, values.max() + 15)
        fun x(t: Instant) = left + (right - left) * Duration.between(start, t).seconds / span
        fun y(mg: Double) = (bottom - (bottom - top) * ((mg - yMin) / (yMax - yMin))).toFloat()

        // полоса целевого диапазона и её границы
        drawRect(
            Tenshi.InRange.copy(alpha = 0.10f),
            topLeft = Offset(left, y(highMgDl)),
            size = Size(right - left, y(lowMgDl) - y(highMgDl)),
        )
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        listOf(lowMgDl, highMgDl).forEach { mg ->
            drawLine(Tenshi.InRange.copy(alpha = 0.45f), Offset(left, y(mg)), Offset(right, y(mg)), 1.dp.toPx(), pathEffect = dash)
            val text = measurer.measure(formatGlucose(mg, unit), labelStyle)
            drawText(text, topLeft = Offset(right + 6.dp.toPx(), y(mg) - text.size.height / 2))
        }

        // подписи времени каждые 3 часа
        val zone = ZoneId.systemDefault()
        var tick = start.atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
        while (!tick.toInstant().isAfter(end)) {
            if (tick.hour % 3 == 0) {
                val px = x(tick.toInstant())
                drawLine(gridColor, Offset(px, top), Offset(px, bottom), 1.dp.toPx())
                val text = measurer.measure(tick.format(hours), labelStyle)
                drawText(text, topLeft = Offset((px - text.size.width / 2).coerceIn(0f, right - text.size.width), bottom + 3.dp.toPx()))
            }
            tick = tick.plusHours(1)
        }

        // линия и заливка под ней
        val line = Path()
        readings.forEachIndexed { i, r ->
            if (i == 0) line.moveTo(x(r.time), y(r.mgDl)) else line.lineTo(x(r.time), y(r.mgDl))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(readings.last().time), bottom)
            lineTo(x(readings.first().time), bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Tenshi.Sakura.copy(alpha = 0.28f), Color.Transparent), startY = top, endY = bottom))
        drawPath(
            line,
            Brush.horizontalGradient(Tenshi.Rainbow, startX = left, endX = right),
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // последний замер — точка с сиянием цвета уровня
        val last = readings.last()
        val color = levelOf(last.mgDl, lowMgDl, highMgDl).color()
        val at = Offset(x(last.time), y(last.mgDl))
        drawCircle(color.copy(alpha = 0.25f), 9.dp.toPx(), at)
        drawCircle(Color.White, 5.dp.toPx(), at)
        drawCircle(color, 3.5.dp.toPx(), at)
    }
}
