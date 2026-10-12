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
 * The digits always keep the font's own proportions, beside the arrow as above it. The number and the arrow
 * are sized in one unit ([SCALES] of it, from Settings): at the same step the arrow is as tall as the digits.
 * The steps are independent while the number and the arrow fit the square together. When they don't (the
 * largest steps, long values like 10.4 or 250), both are made smaller by the same factor, so they keep the
 * proportion that was chosen. A decimal comma hangs below the digits and doesn't make them smaller.
 */
object StatusIcon {
    /** Pixels of the square; Android scales it down to the status bar, a large one stays crisp. */
    const val SIZE = 160

    /** The size steps shown in Settings: two smaller and two larger than the base size. */
    val SCALES = listOf(0.8f, 0.9f, 1.1f, 1.25f)

    // the unit, of the square: the digits' height and the arrow's square at ×1. Beside the number the arrow
    // takes width, above it height; without an arrow the number gets the whole square
    private const val UNIT_BESIDE = 0.32f
    private const val UNIT_ABOVE = 0.36f
    private const val UNIT_ALONE = 0.49f
    private const val MARGIN = 0.02f        // of the square, on every side
    private const val GAP = 0.18f           // between the number and the arrow, of the digits' height
    private const val SPACING = 0.07f       // between two characters, of the digits' height

    fun draw(value: String, trend: Int, arrowAbove: Boolean, numberSize: Int, arrowSize: Int, color: Int = Color.WHITE): Bitmap {
        val s = SIZE.toFloat()
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        if (value.isEmpty()) return bitmap
        val inner = s * (1 - 2 * MARGIN)
        val angle = trendAngle(trend)
        val number = Number(value, color)
        val unit = s * when {
            angle == null -> UNIT_ALONE
            arrowAbove -> UNIT_ABOVE
            else -> UNIT_BESIDE
        }
        var digits = unit * SCALES[numberSize.coerceIn(SCALES.indices)]
        var arrow = if (angle != null) unit * SCALES[arrowSize.coerceIn(SCALES.indices)] else 0f

        // the whole picture for these sizes; everything in it grows in proportion, so one factor fits it
        fun gap() = if (arrow > 0f) digits * GAP else 0f
        val extent = number.extentFor(digits)
        val width = if (arrowAbove) max(number.widthFor(digits), arrow) else number.widthFor(digits) + gap() + arrow
        // beside: the arrow is centred on the digits and may stand out above them and below the comma's tail
        val height = if (arrowAbove) arrow + gap() + extent else max(extent, (digits + arrow) / 2) + max(0f, (arrow - digits) / 2)
        val fit = min(1f, min(inner / width, inner / height))
        digits *= fit
        arrow *= fit
        number.size(digits)
        val gap = gap()

        val arrowCenter: Pair<Float, Float>
        if (arrowAbove) {
            // the arrow, the gap and the number stand as one block in the middle of the square
            val top = (s - (arrow + gap + number.extent)) / 2
            number.draw(canvas, (s - number.width) / 2, top + arrow + gap)
            arrowCenter = s / 2 to top + arrow / 2
        } else {
            // the arrow beside the digits, at their middle; together they stand in the middle of the square
            val above = max(0f, (arrow - number.height) / 2)                   // the arrow over the digits' top
            val below = max(number.extent, (number.height + arrow) / 2)       // the lowest point under it
            val top = (s - (above + below)) / 2 + above
            val left = (s - (number.width + gap + arrow)) / 2
            number.draw(canvas, left, top)
            arrowCenter = left + number.width + gap + arrow / 2 to top + number.height / 2
        }
        if (angle != null) drawArrow(canvas, arrowCenter.first, arrowCenter.second, arrow, angle, color)
        return bitmap
    }

    /**
     * The trend arrow alone, filling the square: the icon of the Live Update capsule, where the number stands
     * next to it as text (see [LiveUpdate]). null without a trend.
     */
    fun arrow(trend: Int, color: Int = Color.WHITE): Bitmap? {
        val angle = trendAngle(trend) ?: return null
        val s = SIZE.toFloat()
        return createBitmap(SIZE, SIZE).also { drawArrow(Canvas(it), s / 2, s / 2, s * (1 - 2 * MARGIN), angle, color) }
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

        /** The digits' height, what the size steps set. */
        var height = 0f
            private set

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
            height = (bottom - top) * scale
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
