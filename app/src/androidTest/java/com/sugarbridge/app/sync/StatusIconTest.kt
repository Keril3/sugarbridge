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

/** The status bar icon is measured by its pixels: how large the number and the arrow are and how they stand. */
@RunWith(AndroidJUnit4::class)
class StatusIconTest {

    private fun visible(b: Bitmap, x: Int, y: Int) = Color.alpha(b.getPixel(x, y)) > 40

    /** Runs of lines that have any visible pixel: rows (within [across] columns) or columns (within [across] rows). */
    private fun runs(b: Bitmap, rows: Boolean, across: IntRange = 0 until SIZE): List<IntRange> {
        val lines = (0 until SIZE).map { i -> across.any { j -> if (rows) visible(b, j, i) else visible(b, i, j) } }
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

    private fun size(r: IntRange) = r.last - r.first + 1

    /** The rows of the number: above it stands the arrow (the first run of rows), beside it the whole height. */
    private fun numberRows(icon: Bitmap, above: Boolean): IntRange =
        if (above) runs(icon, rows = true).drop(1).let { it.first().first..it.last().last } else 0 until SIZE

    /** The first digit's box: its columns and its rows. */
    private fun firstDigit(icon: Bitmap, above: Boolean): Pair<IntRange, IntRange> {
        val columns = runs(icon, rows = false, across = numberRows(icon, above)).first()
        val rows = runs(icon, rows = true, across = columns).let { r -> if (above) r.last() else r.maxBy { size(it) } }
        return columns to rows
    }

    private fun digitHeight(icon: Bitmap, above: Boolean) = size(firstDigit(icon, above).second)

    /** The arrow's height: above the number the first run of rows, beside it the rows of the last run of columns. */
    private fun arrowHeight(icon: Bitmap, above: Boolean): Int =
        if (above) size(runs(icon, rows = true).first())
        else size(runs(icon, rows = true, across = runs(icon, rows = false).last()).let { it.first().first..it.last().last })

    @Test
    fun theNumberIsLargeAndTheArrowStandsApartAboveIt() {
        val icon = StatusIcon.draw("6.4", 4, arrowAbove = true, numberSize = 3, arrowSize = 1)
        val rows = runs(icon, rows = true)
        assertEquals("the arrow and the number: $rows", 2, rows.size)
        val (arrow, number) = rows
        assertTrue("number ${size(number)} px of $SIZE", size(number) >= SIZE * 0.4)
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

    /** The digits are never narrowed: beside the arrow they have the same proportions as above it or alone. */
    @Test
    fun theDigitsKeepTheirProportionsEverywhere() {
        for (value in listOf("3,7", "6.4", "10.4", "22,2", "250")) {
            val (c, r) = firstDigit(StatusIcon.draw(value, 0, false, 3, 0), above = false)
            val alone = size(c).toFloat() / size(r)
            for (above in listOf(true, false)) {
                for ((n, a) in listOf(3 to 3, 3 to 0, 0 to 3, 1 to 1)) {
                    val (columns, rows) = firstDigit(StatusIcon.draw(value, 4, above, n, a), above)
                    val ratio = size(columns).toFloat() / size(rows)
                    assertTrue("$value, above $above, $n/$a: $ratio vs $alone", abs(ratio - alone) <= 0.07f)
                }
            }
        }
    }

    /** At the same step the arrow is as tall as the digits, beside them and above them. */
    @Test
    fun atTheSameStepTheArrowIsAsTallAsTheDigits() {
        for (above in listOf(false, true)) {
            for (step in listOf(1, 3)) {
                val icon = StatusIcon.draw("3,7", 5, above, step, step)   // 5: straight up, as tall as its square
                val digit = digitHeight(icon, above)
                val arrow = arrowHeight(icon, above)
                assertTrue("above $above, step $step: arrow $arrow, digits $digit", abs(arrow - digit) <= digit * 0.08)
            }
        }
    }

    /** While the number and the arrow fit the square together, the step of one doesn't change the other. */
    @Test
    fun stepsAreIndependentWhileTheyFit() {
        for (above in listOf(false, true)) {
            val digits = (0..3).map { digitHeight(StatusIcon.draw("3,7", 5, above, 1, it), above) }
            assertTrue("above $above, number at ×0.9: $digits", digits.max() - digits.min() <= 1)
            val arrows = (0..2).map { arrowHeight(StatusIcon.draw("3,7", 5, above, it, 1), above) }
            assertTrue("above $above, arrow at ×0.9: $arrows", arrows.max() - arrows.min() <= 1)
        }
    }

    /** Every step makes the number larger, also when the square runs short and everything is made smaller. */
    @Test
    fun largerStepsDrawLarger() {
        for (above in listOf(false, true)) {
            for (value in listOf("3,7", "6.4")) {
                val digits = (0..3).map { digitHeight(StatusIcon.draw(value, 5, above, it, 3), above) }
                assertTrue("$value, above $above: $digits", (0..2).all { digits[it] < digits[it + 1] })
            }
            val arrows = (0..3).map { arrowHeight(StatusIcon.draw("6.4", 5, above, 1, it), above) }
            assertTrue("above $above: $arrows", (0..2).all { arrows[it] < arrows[it + 1] })
        }
        val alone = (0..3).map { digitHeight(StatusIcon.draw("6.4", 0, false, it, 1), false) }
        assertTrue("$alone", (0..2).all { alone[it] < alone[it + 1] })
    }

    /** The comma's tail hangs below the digits: "6,4" has digits as tall as "6.4". */
    @Test
    fun aCommaDoesNotMakeTheDigitsSmaller() {
        fun digit(value: String) = digitHeight(StatusIcon.draw(value, 0, false, 2, 1), false)
        assertTrue("${digit("6,4")} vs ${digit("6.4")}", abs(digit("6,4") - digit("6.4")) <= 1)
    }

    /** Alone, the largest number is as wide as the icon allows. */
    @Test
    fun theLargestNumberAloneFillsTheIcon() {
        for (value in listOf("6.4", "6,2")) {
            val icon = StatusIcon.draw(value, 0, false, 3, 1)
            val columns = runs(icon, rows = false)
            val width = columns.last().last - columns.first().first + 1
            assertTrue("$value: $width of $SIZE", width >= SIZE * 0.9)
            assertTrue("$value: digits ${digitHeight(icon, false)} of $SIZE", digitHeight(icon, false) >= SIZE * 0.55)
        }
    }

    @Test
    fun withoutATrendTheNumberIsInTheMiddle() {
        val number = runs(StatusIcon.draw("6.4", 0, true, 3, 1), rows = true).single()
        assertTrue("$number", abs((number.first + number.last) / 2 - SIZE / 2) <= 3)
    }

    @Test
    fun nothingTouchesTheEdges() {
        for (value in listOf("2.2", "6.4", "10.4", "22.2", "22,2", "45", "120", "400")) {
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
