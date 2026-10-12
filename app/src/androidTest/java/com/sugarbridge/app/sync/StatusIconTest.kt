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

/** The status bar icons are measured by their pixels: how large the number and the arrow are and how they stand. */
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
            val heights = (0..3).map { digitHeight(StatusIcon.number(value, it), false) }
            assertTrue("$value: $heights", (0..2).all { heights[it] < heights[it + 1] })
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

    /** Over the number, at the same step the arrow is as tall as the digits. */
    @Test
    fun atTheSameStepTheArrowIsAsTallAsTheDigits() {
        for (step in listOf(1, 3)) {
            val icon = StatusIcon.above("3,7", 5, step, step)   // 5: straight up, as tall as its square
            val digit = digitHeight(icon, true)
            val arrow = size(runs(icon, rows = true).first())
            assertTrue("step $step: arrow $arrow, digits $digit", abs(arrow - digit) <= digit * 0.08)
        }
    }

    /** Over the number, while both fit, the step of one doesn't change the other. */
    @Test
    fun stepsAreIndependentWhileTheyFit() {
        val digits = (0..3).map { digitHeight(StatusIcon.above("3,7", 5, 1, it), true) }
        assertTrue("number at ×0.9: $digits", digits.max() - digits.min() <= 1)
        val arrows = (0..2).map { size(runs(StatusIcon.above("3,7", 5, it, 1), rows = true).first()) }
        assertTrue("arrow at ×0.9: $arrows", arrows.max() - arrows.min() <= 1)
    }

    /** The digits are never narrowed: under the arrow they have the same proportions as alone. */
    @Test
    fun theDigitsKeepTheirProportions() {
        for (value in listOf("3,7", "6.4", "10.4", "22,2", "250")) {
            val (c, r) = firstDigit(StatusIcon.number(value, 3), false)
            val alone = size(c).toFloat() / size(r)
            for ((n, a) in listOf(3 to 3, 3 to 0, 0 to 3, 1 to 1)) {
                val (columns, rows) = firstDigit(StatusIcon.above(value, 4, n, a), true)
                val ratio = size(columns).toFloat() / size(rows)
                assertTrue("$value, $n/$a: $ratio vs $alone", abs(ratio - alone) <= 0.07f)
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
                for (trend in listOf(1, 3, 5)) assertTrue("$value over $trend, $step", !edge(StatusIcon.above(value, trend, 3, step)))
            }
            for (trend in 1..5) assertTrue("arrow $trend, $step", !edge(StatusIcon.arrow(trend, step)!!))
        }
    }
}
