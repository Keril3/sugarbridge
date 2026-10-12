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
 * The glucose drawn as the status bar icon. Android shows a notification icon in a small square and shrinks
 * anything wider to fit it, so the number and the arrow share one square: [above] puts the arrow over the
 * number, [beside] next to it, [number] and [arrow] draw either alone.
 *
 * The digits always keep the font's own proportions; the sizes are steps from Settings ([SCALES]). Together,
 * the number and the arrow are sized in one unit, so at the same step the arrow is as tall as the digits; the
 * steps are independent while both fit, and at the largest ones both are made smaller by the same factor.
 * Over the number the digits can be larger: the arrow takes height there, beside it takes width, and a number
 * is wider than tall. A decimal comma hangs below the digits and doesn't make them smaller.
 */
object StatusIcon {
    /** Pixels of the square; Android scales it down to the status bar, a large one stays crisp. */
    const val SIZE = 160

    /** The size steps shown in Settings: two smaller and two larger than the base size. */
    val SCALES = listOf(0.8f, 0.9f, 1.1f, 1.25f)

    // the digits' height and the arrow's square at ×1, of the square: over the number the arrow takes height,
    // beside it width, which a number has less to spare of
    private const val UNIT_ABOVE = 0.36f
    private const val UNIT_BESIDE = 0.32f
    private const val MARGIN = 0.02f        // of the square, on every side
    private const val GAP = 0.18f           // between the arrow and the number, of the digits' height
    private const val SPACING = 0.07f       // between two characters, of the digits' height

    private fun scale(step: Int) = SCALES[step.coerceIn(SCALES.indices)]

    /**
     * The number alone. ×1.25 makes it as large as the square allows for this value (as wide as the square for
     * "6,4" or "10,4", as tall for "45"); every smaller step is that much smaller, so each step counts.
     */
    fun number(value: String, numberSize: Int, color: Int = Color.WHITE): Bitmap {
        val s = SIZE.toFloat()
        val bitmap = createBitmap(SIZE, SIZE)
        if (value.isEmpty()) return bitmap
        val inner = s * (1 - 2 * MARGIN)
        val number = Number(value, color)
        val largest = min(inner / number.widthFor(1f), inner / number.extentFor(1f))
        number.size(largest * scale(numberSize) / SCALES.last())
        number.draw(Canvas(bitmap), (s - number.width) / 2, (s - number.extent) / 2)
        return bitmap
    }

    /** The trend arrow alone, when the number is left out: ×1.25 fills the square. null without a trend. */
    fun arrow(trend: Int, arrowSize: Int = SCALES.lastIndex, color: Int = Color.WHITE): Bitmap? {
        val angle = trendAngle(trend) ?: return null
        val s = SIZE.toFloat()
        val box = s * (1 - 2 * MARGIN) * scale(arrowSize) / SCALES.last()
        return createBitmap(SIZE, SIZE).also { drawArrow(Canvas(it), s / 2, s / 2, box, angle, color) }
    }

    /** The arrow over the number. Without a trend it is the number alone. */
    fun above(value: String, trend: Int, numberSize: Int, arrowSize: Int, color: Int = Color.WHITE): Bitmap {
        val angle = trendAngle(trend) ?: return number(value, numberSize, color)
        val s = SIZE.toFloat()
        val bitmap = createBitmap(SIZE, SIZE)
        if (value.isEmpty()) return bitmap
        val inner = s * (1 - 2 * MARGIN)
        val number = Number(value, color)
        var digits = s * UNIT_ABOVE * scale(numberSize)
        var arrow = s * UNIT_ABOVE * scale(arrowSize)
        val width = max(number.widthFor(digits), arrow)
        val height = arrow + digits * GAP + number.extentFor(digits)
        val fit = min(1f, min(inner / width, inner / height))
        digits *= fit
        arrow *= fit
        number.size(digits)
        val gap = digits * GAP
        // the arrow, the gap and the number stand as one block in the middle of the square
        val top = (s - (arrow + gap + number.extent)) / 2
        val canvas = Canvas(bitmap)
        number.draw(canvas, (s - number.width) / 2, top + arrow + gap)
        drawArrow(canvas, s / 2, top + arrow / 2, arrow, angle, color)
        return bitmap
    }

    /** The arrow beside the number, at the middle of the digits. Without a trend it is the number alone. */
    fun beside(value: String, trend: Int, numberSize: Int, arrowSize: Int, color: Int = Color.WHITE): Bitmap {
        val angle = trendAngle(trend) ?: return number(value, numberSize, color)
        val s = SIZE.toFloat()
        val bitmap = createBitmap(SIZE, SIZE)
        if (value.isEmpty()) return bitmap
        val inner = s * (1 - 2 * MARGIN)
        val number = Number(value, color)
        var digits = s * UNIT_BESIDE * scale(numberSize)
        var arrow = s * UNIT_BESIDE * scale(arrowSize)
        val width = number.widthFor(digits) + digits * GAP + arrow
        // the arrow may stand out above the digits and below the comma's tail
        val height = max(number.extentFor(digits), (digits + arrow) / 2) + max(0f, (arrow - digits) / 2)
        val fit = min(1f, min(inner / width, inner / height))
        digits *= fit
        arrow *= fit
        number.size(digits)
        val gap = digits * GAP
        val over = max(0f, (arrow - digits) / 2)                    // the arrow over the digits' top
        val under = max(number.extent, (digits + arrow) / 2)       // the lowest point under it
        val top = (s - (over + under)) / 2 + over
        val left = (s - (number.width + gap + arrow)) / 2
        val canvas = Canvas(bitmap)
        number.draw(canvas, left, top)
        drawArrow(canvas, left + number.width + gap + arrow / 2, top + digits / 2, arrow, angle, color)
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

        /** The height with a comma's tail. */
        var extent = 0f
            private set
        var width = 0f
            private set

        private fun naturalWidth() = glyphs.sumOf { it.width() } + SPACING * (bottom - top) * (glyphs.size - 1)

        fun widthFor(digits: Float) = naturalWidth() * digits / (bottom - top)

        fun extentFor(digits: Float) = digits * (bottom - top + tail) / (bottom - top)

        /** Digits [digits] tall, in the font's own proportions. */
        fun size(digits: Float) {
            scale = digits / (bottom - top)
            paint.textSize = 100f * scale
            width = naturalWidth() * scale
            extent = (bottom - top + tail) * scale
        }

        fun draw(canvas: Canvas, left: Float, top: Float) {
            var x = left
            val baseline = top - this.top * scale
            text.forEachIndexed { i, c ->
                val g = glyphs[i]
                canvas.drawText(c.toString(), x - g.left * scale, baseline, paint)
                x += (g.width() + SPACING * (bottom - this.top)) * scale
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
