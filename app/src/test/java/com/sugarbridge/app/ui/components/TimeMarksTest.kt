package com.sugarbridge.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeMarksTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private val start = ZonedDateTime.of(2026, 10, 1, 6, 20, 0, 0, zone)

    @Test
    fun twelveHoursGetAMarkEveryThreeHours() {
        val marks = timeMarks(start, start.plusHours(12))
        assertTrue(marks.all { it.second == MarkKind.HOUR && it.first.hour % 3 == 0 })
        assertEquals(listOf(9, 12, 15, 18), marks.map { it.first.hour })
    }

    @Test
    fun aWeekIsMarkedByDaysAMonthByWeeks() {
        val week = timeMarks(start, start.plusDays(7))
        assertEquals(7, week.size)
        assertTrue(week.all { it.second == MarkKind.DAY && it.first.hour == 0 && it.first.minute == 0 })
        val month = timeMarks(start, start.plusDays(30))
        assertTrue(month.size in 4..5)
        assertTrue(month.zipWithNext().all { (a, b) -> b.first == a.first.plusDays(7) })
    }

    @Test
    fun yearsAreMarkedByMonthsAtMostAboutSixTimes() {
        val marks = timeMarks(start, start.plusYears(5))
        assertTrue("${marks.size} marks", marks.size in 5..7)
        assertTrue(marks.all { it.second == MarkKind.MONTH && it.first.dayOfMonth == 1 })
    }
}
