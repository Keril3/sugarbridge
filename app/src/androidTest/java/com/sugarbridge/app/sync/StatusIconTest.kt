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

    private fun span(r: List<IntRange>) = r.last().last - r.first().first + 1

    /** The arrow's height beside the number: the rows of the last run of columns. */
    private fun besideArrowHeight(icon: Bitmap) = span(runs(icon, rows = true, across = runs(icon, rows = false).last()))

    /** The first digit's box, under the arrow if there is one above: its columns and its rows. */
    private fun firstDigit(icon: Bitmap, arrowAbove: Boolean): Pair<IntRange, IntRange> {
        val numberRows = if (arrowAbove) runs(icon, rows = true).drop(1).let { it.first().first..it.last().last } else 0 until SIZE
        val columns = runs(icon, rows = false, across = numberRows).first()
        val rows = runs(icon, rows = true, across = columns).let { r -> if (arrowAbove) r.last() else r.maxBy { size(it) } }
        return columns to rows
    }

    private fun digitHeight(icon: Bitmap, arrowAbove: Boolean) = size(firstDigit(icon, arrowAbove).second)

    /** Alone, the largest number fills the width of its icon and is tall. */
    @Test
    fun theLargestNumberFillsItsIcon() {
        for (value in listOf("6.4", "6,5")) {
            val icon = StatusIcon.number(value, 3)
            assertTrue("$value: width ${span(runs(icon, rows = false))} of $SIZE", span(runs(icon, rows = false)) >= SIZE * 0.9)
            assertTrue("$value: digits ${digitHeight(icon, false)} of $SIZE", digitHeight(icon, false) >= SIZE * 0.55)
        }
    }

    /** The largest arrow fills its own icon; every step draws it larger. */
    @Test
    fun theArrowFillsItsIcon() {
        val up = StatusIcon.arrow(5, 3)!!   // straight up: as tall as its square
        assertTrue("${span(runs(up, rows = true))} of $SIZE", span(runs(up, rows = true)) >= SIZE * 0.9)
        val heights = (0..3).map { span(runs(StatusIcon.arrow(5, it)!!, rows = true)) }
        assertTrue("$heights", (0..2).all { heights[it] < heights[it + 1] })
        assertEquals(null, StatusIcon.arrow(0, 3))   // no trend, no arrow
    }

    @Test
    fun everyNumberStepDrawsLarger() {
        for (value in listOf("3,7", "6.4", "10.4")) {
            val alone = (0..3).map { digitHeight(StatusIcon.number(value, it), false) }
            assertTrue("$value alone: $alone", (0..2).all { alone[it] < alone[it + 1] })
            val beside = (0..3).map { digitHeight(StatusIcon.beside(value, 5, it, 3), false) }
            assertTrue("$value beside: $beside", (0..2).all { beside[it] < beside[it + 1] })
        }
    }

    @Test
    fun overTheNumberTheArrowStandsApart() {
        val icon = StatusIcon.above("6.4", 4, numberSize = 3, arrowSize = 1)
        val rows = runs(icon, rows = true)
        assertEquals("the arrow and the number: $rows", 2, rows.size)
        val (arrow, number) = rows
        assertTrue("number ${size(number)} px of $SIZE", size(number) >= SIZE * 0.4)
        assertTrue("gap ${number.first - arrow.last} px", number.first - arrow.last >= SIZE * 0.06)
    }

    @Test
    fun besideTheNumberTheArrowKeepsAGapToo() {
        val columns = runs(StatusIcon.beside("6.4", 4, numberSize = 3, arrowSize = 1), rows = false)
        val gap = columns.last().first - columns[columns.size - 2].last
        assertTrue("gap $gap px", gap >= SIZE * 0.06)
    }

    /** Over the number and beside it, at the same step the arrow is as tall as the digits. */
    @Test
    fun atTheSameStepTheArrowIsAsTallAsTheDigits() {
        for (step in listOf(1, 3)) {
            val above = StatusIcon.above("3,7", 5, step, step)   // 5: straight up, as tall as its square
            val overArrow = size(runs(above, rows = true).first())
            assertTrue("above, step $step: arrow $overArrow, digits ${digitHeight(above, true)}", abs(overArrow - digitHeight(above, true)) <= digitHeight(above, true) * 0.08)
            val beside = StatusIcon.beside("3,7", 5, step, step)
            val besideArrow = besideArrowHeight(beside)
            assertTrue("beside, step $step: arrow $besideArrow, digits ${digitHeight(beside, false)}", abs(besideArrow - digitHeight(beside, false)) <= digitHeight(beside, false) * 0.08)
        }
    }

    /** While the number and the arrow fit together, the step of one doesn't change the other. */
    @Test
    fun stepsAreIndependentWhileTheyFit() {
        val over = (0..3).map { digitHeight(StatusIcon.above("3,7", 5, 1, it), true) }
        assertTrue("over, number at ×0.9: $over", over.max() - over.min() <= 1)
        val overArrows = (0..2).map { size(runs(StatusIcon.above("3,7", 5, it, 1), rows = true).first()) }
        assertTrue("over, arrow at ×0.9: $overArrows", overArrows.max() - overArrows.min() <= 1)
        val beside = (0..3).map { digitHeight(StatusIcon.beside("3,7", 5, 1, it), false) }
        assertTrue("beside, number at ×0.9: $beside", beside.max() - beside.min() <= 1)
        val besideArrows = (0..2).map { besideArrowHeight(StatusIcon.beside("3,7", 5, it, 1)) }
        assertTrue("beside, arrow at ×0.9: $besideArrows", besideArrows.max() - besideArrows.min() <= 1)
    }

    /** Over the number the digits are larger than beside it, at the same steps. */
    @Test
    fun overTheNumberTheDigitsAreLarger() {
        for (value in listOf("6,4", "6.4", "10.4")) {
            val over = digitHeight(StatusIcon.above(value, 3, 3, 2), true)
            val beside = digitHeight(StatusIcon.beside(value, 3, 3, 2), false)
            assertTrue("$value: over $over, beside $beside", over > beside)
        }
    }

    /** The digits are never narrowed: under the arrow they have the same proportions as alone. */
    @Test
    fun theDigitsKeepTheirProportions() {
        for (value in listOf("3,7", "6.4", "10.4", "22,2", "250")) {
            val (c, r) = firstDigit(StatusIcon.number(value, 3), false)
            val alone = size(c).toFloat() / size(r)
            for ((n, a) in listOf(3 to 3, 3 to 0, 0 to 3, 1 to 1)) {
                for (over in listOf(true, false)) {
                    val icon = if (over) StatusIcon.above(value, 4, n, a) else StatusIcon.beside(value, 4, n, a)
                    val (columns, rows) = firstDigit(icon, over)
                    val ratio = size(columns).toFloat() / size(rows)
                    assertTrue("$value, over $over, $n/$a: $ratio vs $alone", abs(ratio - alone) <= 0.07f)
                }
            }
        }
    }

    /** The comma's tail hangs below the digits: "6,4" has digits as tall as "6.4". */
    @Test
    fun aCommaDoesNotMakeTheDigitsSmaller() {
        fun digit(value: String) = digitHeight(StatusIcon.number(value, 2), false)
        assertTrue("${digit("6,4")} vs ${digit("6.4")}", abs(digit("6,4") - digit("6.4")) <= 1)
    }

    @Test
    fun withoutATrendTheNumberIsAloneInTheMiddle() {
        val icon = StatusIcon.above("6.4", 0, 3, 1)
        assertTrue(icon.sameAs(StatusIcon.number("6.4", 3)))
        assertTrue(StatusIcon.beside("6.4", 0, 3, 1).sameAs(icon))
        val number = runs(icon, rows = true).single()
        assertTrue("$number", abs((number.first + number.last) / 2 - SIZE / 2) <= 3)
    }

    @Test
    fun nothingTouchesTheEdges() {
        fun edge(icon: Bitmap) = (0 until SIZE).any { i ->
            visible(icon, i, 0) || visible(icon, i, SIZE - 1) || visible(icon, 0, i) || visible(icon, SIZE - 1, i)
        }
        for (step in 0..3) {
            for (value in listOf("2.2", "6.4", "10.4", "22.2", "22,2", "45", "120", "400")) {
                assertTrue("number $value, $step", !edge(StatusIcon.number(value, step)))
                for (trend in listOf(1, 3, 5)) {
                    assertTrue("$value over $trend, $step", !edge(StatusIcon.above(value, trend, 3, step)))
                    assertTrue("$value beside $trend, $step", !edge(StatusIcon.beside(value, trend, 3, step)))
                }
            }
            for (trend in 1..5) assertTrue("arrow $trend, $step", !edge(StatusIcon.arrow(trend, step)!!))
        }
    }
}
