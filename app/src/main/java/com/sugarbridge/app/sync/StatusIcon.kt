package com.sugarbridge.app.sync

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import com.sugarbridge.app.glucose.trendAngle
import kotlin.math.max
import kotlin.math.min

/**
 * The glucose value drawn as the status bar icon. Android shows a notification icon in a small square and
 * shrinks anything wider to fit it, so the number and the trend arrow share one square.
 *
 * The number and the arrow are sized from Settings ([SCALES] of a base size). The number comes first: its
 * height depends only on its own step. When the square runs short, its digits get narrower (down to
 * [NARROWEST] of their width), then the arrow gives way, down to its smallest step, and only then the number
 * gets lower, which happens only to long values at the largest steps. A decimal comma hangs below the digits,
 * so the digits keep their height in languages that write one.
 */
object StatusIcon {
    /** Pixels of the square; Android scales it down to the status bar, a large one stays crisp. */
    const val SIZE = 160

    /** The size steps shown in Settings: two smaller and two larger than the base size. */
    val SCALES = listOf(0.8f, 0.9f, 1.1f, 1.25f)

    /** Sizes at ×1, of the square. Beside the number the arrow takes width; above it, height, so both are smaller. */
    private class Layout(val number: Float, val arrow: Float)

    private val BESIDE = Layout(number = 0.62f, arrow = 0.30f)
    private val ABOVE = Layout(number = 0.46f, arrow = 0.22f)
    private const val MARGIN = 0.02f        // of the square, on every side
    private const val GAP = 0.065f          // between the number and the arrow
    private const val SPACING = 0.07f       // between two characters, of the digits' height
    private const val NARROWEST = 0.5f      // how narrow the digits may get before the arrow and then the number give way

    fun draw(value: String, trend: Int, arrowAbove: Boolean, numberSize: Int, arrowSize: Int, color: Int = Color.WHITE): Bitmap {
        val s = SIZE.toFloat()
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        if (value.isEmpty()) return bitmap
        val layout = if (arrowAbove) ABOVE else BESIDE
        val inner = s * (1 - 2 * MARGIN)
        val angle = trendAngle(trend)
        val number = Number(value, color)
        val wanted = s * layout.number * SCALES[numberSize.coerceIn(SCALES.indices)]
        var arrow = 0f
        var gap = 0f
        if (angle != null) {
            gap = s * GAP
            val chosen = s * layout.arrow * SCALES[arrowSize.coerceIn(SCALES.indices)]
            val smallest = s * layout.arrow * SCALES.first()
            val left = inner - gap - if (arrowAbove) number.extentFor(wanted) else number.narrowestWidthFor(wanted)
            arrow = min(chosen, max(left, smallest))
        }
        if (arrowAbove) number.fit(wanted, inner, inner - arrow - gap) else number.fit(wanted, inner - arrow - gap, inner)

        val arrowCenter: Pair<Float, Float>
        if (arrowAbove) {
            // the arrow, the gap and the number stand as one block in the middle of the square
            val top = (s - (arrow + gap + number.extent)) / 2
            number.draw(canvas, (s - number.width) / 2, top + arrow + gap)
            arrowCenter = s / 2 to top + arrow / 2
        } else {
            // the digits in the middle, unless a comma's tail would leave the square; the arrow beside the digits
            val top = ((s - number.height) / 2).coerceAtMost(s * (1 - MARGIN) - number.extent)
            val left = (s - (number.width + gap + arrow)) / 2
            number.draw(canvas, left, top)
            arrowCenter = left + number.width + gap + arrow / 2 to top + number.height / 2
        }
        if (angle != null) drawArrow(canvas, arrowCenter.first, arrowCenter.second, arrow, angle, color)
        return bitmap
    }

    /** The digits laid out by their outlines, so a dot or a comma takes only its own width. */
    private class Number(val text: String, color: Int) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = 100f
        }
        private val glyphs = text.map { c -> Rect().also { paint.getTextBounds(c.toString(), 0, 1, it) } }
        private val top = glyphs.minOf { it.top }       // at text size 100
        private val bottom = text.indices.filter { text[it].isLetterOrDigit() }.maxOfOrNull { glyphs[it].bottom } ?: glyphs.maxOf { it.bottom }
        private val tail = glyphs.maxOf { it.bottom } - bottom   // how far a comma hangs below the digits
        private var scale = 1f       // of text size 100
        private var narrow = 1f      // horizontal squeeze

        /** The digits' height, what the size steps set. */
        var height = 0f
            private set

        /** The height with a comma's tail. */
        var extent = 0f
            private set
        var width = 0f
            private set

        private fun naturalWidth() = glyphs.sumOf { it.width() } + SPACING * (bottom - top) * (glyphs.size - 1)

        fun extentFor(digits: Float) = digits * (bottom - top + tail) / (bottom - top)

        fun narrowestWidthFor(digits: Float) = naturalWidth() * digits / (bottom - top) * NARROWEST

        /** Digits [wantHeight] tall if they fit [roomHeight] and, narrowed at most to [NARROWEST], [roomWidth]; lower if not. */
        fun fit(wantHeight: Float, roomWidth: Float, roomHeight: Float) {
            scale = min(wantHeight / (bottom - top), roomHeight / (bottom - top + tail))
            narrow = min(1f, roomWidth / (naturalWidth() * scale))
            if (narrow < NARROWEST) {
                narrow = NARROWEST
                scale = roomWidth / (naturalWidth() * NARROWEST)
            }
            paint.textSize = 100f * scale
            paint.textScaleX = narrow
            width = naturalWidth() * scale * narrow
            height = (bottom - top) * scale
            extent = (bottom - top + tail) * scale
        }

        fun draw(canvas: Canvas, left: Float, top: Float) {
            var x = left
            val baseline = top - this.top * scale
            text.forEachIndexed { i, c ->
                val g = glyphs[i]
                canvas.drawText(c.toString(), x - g.left * scale * narrow, baseline, paint)
                x += (g.width() + SPACING * (bottom - this.top)) * scale * narrow
            }
        }
    }

    /**
     * A bold arrow in a [box]-sized square, drawn pointing right and turned the way the app turns its arrow.
     * A diagonal arrow is drawn longer, so that turned it still fills the square instead of shrinking.
     */
    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, box: Float, angle: Float, color: Int) {
        val half = box * (if (angle % 90f == 0f) 0.40f else 0.56f)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = box * 0.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.withRotation(angle, cx, cy) {
            drawLine(cx - half, cy, cx + half, cy, stroke)
            drawLine(cx + half - box * 0.36f, cy - box * 0.32f, cx + half, cy, stroke)
            drawLine(cx + half - box * 0.36f, cy + box * 0.32f, cx + half, cy, stroke)
        }
    }
}
