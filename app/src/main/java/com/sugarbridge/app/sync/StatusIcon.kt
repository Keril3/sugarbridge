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
import kotlin.math.min

/**
 * The glucose value drawn as the status bar icon. Android shows a notification icon as a small square in one
 * color, so everything has to fit a square: by default the number takes the full width and the trend arrow
 * stands above it with a clear gap ([arrowAbove]); beside the number both are smaller. The sizes are steps from
 * Settings, see [NUMBER_SIZES] and [ARROW_SIZES].
 */
object StatusIcon {
    /** Pixels of the square; Android scales it down to the status bar, a large one stays crisp. */
    const val SIZE = 144

    /** How much of the free width the number may take. */
    val NUMBER_SIZES = listOf(0.70f, 0.80f, 0.90f, 1f)

    /** How much of the square the arrow takes: its height above the number, its width beside it. */
    val ARROW_SIZES = listOf(0.24f, 0.31f, 0.38f, 0.45f)

    private const val MARGIN = 0.02f   // of the square, on every side
    private const val GAP = 0.08f      // between the number and the arrow

    fun draw(value: String, trend: Int, arrowAbove: Boolean, numberSize: Int, arrowSize: Int, color: Int = Color.WHITE): Bitmap {
        val s = SIZE.toFloat()
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        val inner = s * (1 - 2 * MARGIN)
        val angle = trendAngle(trend)
        val arrow = if (angle != null) s * ARROW_SIZES[arrowSize.coerceIn(ARROW_SIZES.indices)] else 0f
        val gap = if (angle != null) s * GAP else 0f
        val share = NUMBER_SIZES[numberSize.coerceIn(NUMBER_SIZES.indices)]

        // the number: as large as the room allows, measured by its real outline, not by the font's line height
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = 100f
            letterSpacing = -0.02f
        }
        val bounds = Rect()
        paint.getTextBounds(value, 0, value.length, bounds)
        if (bounds.isEmpty) return bitmap   // nothing to draw: no size to fit
        val (roomWidth, roomHeight) = if (arrowAbove) inner to inner - arrow - gap else inner - arrow - gap to inner
        val scale = min(roomWidth * share / bounds.width(), roomHeight / bounds.height())
        paint.textSize = 100f * scale
        paint.getTextBounds(value, 0, value.length, bounds)
        val numberWidth = bounds.width().toFloat()
        val numberHeight = bounds.height().toFloat()

        val arrowCenter: Pair<Float, Float>
        if (arrowAbove) {
            // the arrow, the gap and the number stand as one block in the middle of the square
            val top = (s - (arrow + gap + numberHeight)) / 2
            arrowCenter = s / 2 to top + arrow / 2
            canvas.drawText(value, (s - numberWidth) / 2 - bounds.left, top + arrow + gap - bounds.top, paint)
        } else {
            val left = (s - (numberWidth + gap + arrow)) / 2
            canvas.drawText(value, left - bounds.left, (s - numberHeight) / 2 - bounds.top, paint)
            arrowCenter = left + numberWidth + gap + arrow / 2 to s / 2
        }
        if (angle != null) drawArrow(canvas, arrowCenter.first, arrowCenter.second, arrow, angle, color)
        return bitmap
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
