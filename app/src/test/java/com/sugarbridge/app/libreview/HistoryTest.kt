package com.sugarbridge.app.libreview

import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.sync.minuteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryTest {

    private val kaliningrad = ZoneId.of("Europe/Kaliningrad")   // UTC+2
    private fun r(utc: String, mgDl: Double) = Reading(Instant.parse(utc), mgDl, 0)

    private val readings = listOf(
        r("2026-10-08T10:00:00Z", 60.0),    // 8 октября, ниже диапазона
        r("2026-10-08T12:00:00Z", 100.0),
        r("2026-10-08T21:59:00Z", 200.0),   // 23:59 по Калининграду — ещё 8-е
        r("2026-10-08T22:01:00Z", 120.0),   // 00:01 по Калининграду — уже 9-е
    )

    @Test
    fun daysAreGroupedInPhoneTimeZone() {
        val days = summarizeDays(readings, kaliningrad, 70.0, 180.0)
        assertEquals(listOf(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9)), days.map { it.date })
        assertEquals(listOf(3, 1), days.map { it.count })
    }

    @Test
    fun dayStatsAreCalculated() {
        val day = summarizeDays(readings, kaliningrad, 70.0, 180.0).first()
        assertEquals(60.0, day.minMgDl, 0.0)
        assertEquals(200.0, day.maxMgDl, 0.0)
        assertEquals(120.0, day.avgMgDl, 1e-9)
        assertEquals(33, day.inRangePercent)   // из трёх замеров в диапазоне один
    }

    @Test
    fun alreadyWrittenMinutesAreCounted() {
        val existing = readings.take(3).map { minuteKey(it.time) }.toSet()
        val days = summarizeDays(readings, kaliningrad, 70.0, 180.0, existing)
        assertTrue(days[0].complete)
        assertEquals(0, days[1].alreadyInHealthConnect)
        assertFalse(days[1].complete)
    }

    @Test
    fun readingsOnChosenDaysOnly() {
        val chosen = readingsOnDays(readings, setOf(LocalDate.of(2026, 10, 9)), kaliningrad)
        assertEquals(listOf(120.0), chosen.map { it.mgDl })
    }

    @Test
    fun dayBoundsAreLocalMidnights() {
        val (from, to) = dayBounds(LocalDate.of(2026, 10, 9), kaliningrad)
        assertEquals(Instant.parse("2026-10-08T22:00:00Z"), from)
        assertEquals(Instant.parse("2026-10-09T22:00:00Z"), to)
    }

    @Test
    fun consecutiveDaysAreMerged() {
        val d = { day: Int -> LocalDate.of(2026, 10, day) }
        assertEquals(
            listOf(d(1)..d(3), d(5)..d(5), d(7)..d(8)),
            contiguousRanges(listOf(d(8), d(2), d(1), d(5), d(3), d(7))),
        )
        assertEquals(emptyList<ClosedRange<LocalDate>>(), contiguousRanges(emptyList()))
    }
}
