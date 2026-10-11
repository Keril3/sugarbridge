package com.sugarbridge.app.sync

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sugarbridge.app.sync.StatusIcon.SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** The status bar icon is measured by its pixels: how large the number is and how far the arrow stands from it. */
@RunWith(AndroidJUnit4::class)
class StatusIconTest {

    private fun visible(b: Bitmap, x: Int, y: Int) = Color.alpha(b.getPixel(x, y)) > 40

    /** Runs of rows (or columns) that have any visible pixel. */
    private fun runs(b: Bitmap, rows: Boolean): List<IntRange> {
        val lines = (0 until SIZE).map { i -> (0 until SIZE).any { j -> if (rows) visible(b, j, i) else visible(b, i, j) } }
        val result = mutableListOf<IntRange>()
        var start = -1
        lines.forEachIndexed { i, on ->
            if (on && start < 0) start = i
            if (!on && start >= 0) {
                result += start until i
                start = -1
            }
        }
        if (start >= 0) result += start until SIZE
        return result
    }

    private fun height(r: IntRange) = r.last - r.first + 1

    @Test
    fun theNumberIsLargeAndTheArrowStandsApartAboveIt() {
        val icon = StatusIcon.draw("6.4", 4, arrowAbove = true, numberSize = 3, arrowSize = 1)
        val rows = runs(icon, rows = true)
        assertEquals("the arrow and the number: $rows", 2, rows.size)
        val (arrow, number) = rows
        assertTrue("number ${height(number)} px of $SIZE", height(number) >= SIZE * 0.45)
        assertTrue("gap ${number.first - arrow.last} px", number.first - arrow.last >= SIZE * 0.06)
    }

    @Test
    fun besideTheNumberTheArrowKeepsAGapToo() {
        val icon = StatusIcon.draw("6.4", 4, arrowAbove = false, numberSize = 3, arrowSize = 1)
        val columns = runs(icon, rows = false)
        val arrow = columns.last()
        val beforeArrow = columns[columns.size - 2]
        assertTrue("gap ${arrow.first - beforeArrow.last} px", arrow.first - beforeArrow.last >= SIZE * 0.06)
    }

    @Test
    fun largerStepsDrawLarger() {
        fun numberHeight(step: Int) = height(runs(StatusIcon.draw("6.4", 0, true, step, 1), rows = true).single())
        fun arrowHeight(step: Int) = height(runs(StatusIcon.draw("6.4", 5, true, 1, step), rows = true).first())
        assertTrue((0..2).all { numberHeight(it) < numberHeight(it + 1) })
        assertTrue((0..2).all { arrowHeight(it) < arrowHeight(it + 1) })
    }

    @Test
    fun withoutATrendTheNumberIsInTheMiddle() {
        val number = runs(StatusIcon.draw("6.4", 0, true, 3, 1), rows = true).single()
        assertTrue("$number", abs((number.first + number.last) / 2 - SIZE / 2) <= 3)
    }

    @Test
    fun nothingTouchesTheEdges() {
        for (value in listOf("2.2", "6.4", "10.4", "22.2", "45", "120", "400")) {
            for (trend in listOf(0, 1, 3, 5)) {
                for (above in listOf(true, false)) {
                    for (step in 0..3) {
                        val icon = StatusIcon.draw(value, trend, above, numberSize = 3, arrowSize = step)
                        val edge = (0 until SIZE).any { i ->
                            visible(icon, i, 0) || visible(icon, i, SIZE - 1) || visible(icon, 0, i) || visible(icon, SIZE - 1, i)
                        }
                        assertTrue("$value, trend $trend, above $above, arrow $step touches the edge", !edge)
                    }
                }
            }
        }
    }
}
