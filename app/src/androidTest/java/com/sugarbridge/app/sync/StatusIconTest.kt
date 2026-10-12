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

    /** Rows with a visible pixel left of column [before]: beside the number, everything but the arrow. */
    private fun rowsLeftOf(b: Bitmap, before: Int): IntRange {
        val rows = (0 until SIZE).filter { y -> (0 until before).any { x -> visible(b, x, y) } }
        return rows.first()..rows.last()
    }

    /** The height of the first digit alone, without a comma's tail. */
    private fun firstDigitHeight(icon: Bitmap) = height(rowsLeftOf(icon, runs(icon, rows = false).first().last + 1))

    /** The number's drawn height: above it is everything under the arrow, beside it everything left of the arrow. */
    private fun numberHeight(icon: Bitmap, above: Boolean): Int =
        if (above) runs(icon, rows = true).drop(1).let { it.last().last - it.first().first + 1 }
        else height(rowsLeftOf(icon, runs(icon, rows = false).last().first))

    /**
     * The arrow's step never changes the number, for short and long values, with a dot or a comma, beside and
     * above. When the square runs short, the arrow gives way instead.
     */
    @Test
    fun theArrowsStepNeverChangesTheNumber() {
        for (value in listOf("6,2", "6.4", "10.4", "22,2", "115", "250")) {
            for (above in listOf(false, true)) {
                for (numberStep in 0..3) {
                    val heights = (0..3).map { arrowStep -> numberHeight(StatusIcon.draw(value, 3, above, numberStep, arrowStep), above) }
                    assertTrue("$value, above $above, number $numberStep: $heights", heights.max() - heights.min() <= 1)
                }
            }
        }
    }

    /** Above the number every step of the number draws it larger, with a comma too. */
    @Test
    fun aboveTheNumberEveryStepCounts() {
        for (value in listOf("6,4", "6.4")) {
            val heights = (0..3).map { numberHeight(StatusIcon.draw(value, 3, true, it, 3), above = true) }
            assertTrue("$value: $heights", (0..2).all { heights[it] < heights[it + 1] })
        }
    }

    /** Next to a number of usual size the arrow grows with its step, and the number's step doesn't change it. */
    @Test
    fun theArrowKeepsItsSizeWhileThereIsRoom() {
        fun arrowWidth(numberStep: Int, arrowStep: Int) = height(runs(StatusIcon.draw("6,2", 3, false, numberStep, arrowStep), rows = false).last())
        assertTrue((0..2).all { arrowWidth(1, it) < arrowWidth(1, it + 1) })
        assertTrue((0..2).all { abs(arrowWidth(0, it) - arrowWidth(2, it)) <= 1 })
        assertTrue(arrowWidth(3, 3) <= arrowWidth(0, 3))   // at the largest number the largest arrow gives way
    }

    /** The comma's tail hangs below the digits: "6,4" has digits as tall as "6.4". */
    @Test
    fun aCommaDoesNotMakeTheDigitsSmaller() {
        fun firstDigit(value: String) = firstDigitHeight(StatusIcon.draw(value, 0, false, 2, 1))
        assertTrue("${firstDigit("6,4")} vs ${firstDigit("6.4")}", abs(firstDigit("6,4") - firstDigit("6.4")) <= 1)
    }

    /** Alone, the largest number is about as tall as the icon: as tall as the clock's digits in the status bar. */
    @Test
    fun theLargestNumberFillsTheIcon() {
        for (value in listOf("6.4", "6,2")) {
            val digit = firstDigitHeight(StatusIcon.draw(value, 0, false, 3, 1))
            assertTrue("$value: $digit of $SIZE", digit >= SIZE * 0.74)
        }
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
