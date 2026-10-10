package com.sugarbridge.app.glucose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class GmiTest {

    private val from = Instant.parse("2026-09-26T00:00:00Z")
    private val to = from.plus(Duration.ofDays(14))

    @Test
    fun formulaMatchesPublishedTable() {
        // таблица из статьи Bergenstal 2018: 100 мг/дл → 5,7 %, 150 → 6,9 %, 200 → 8,1 %
        assertEquals(5.7, gmiPercent(100.0), 0.05)
        assertEquals(6.9, gmiPercent(150.0), 0.05)
        assertEquals(8.1, gmiPercent(200.0), 0.05)
    }

    @Test
    fun percentToMmolPerMol() {
        assertEquals(53, hba1cMmolPerMol(7.0))   // 7 % ≈ 53 ммоль/моль
        assertEquals(42, hba1cMmolPerMol(6.0))
        assertEquals(31, hba1cMmolPerMol(5.0))
    }

    @Test
    fun fullTwoWeeksEveryFifteenMinutes() {
        val readings = (0 until 14 * 96).map { Reading(from.plusSeconds(it * 900L), 150.0, 0) }
        val gmi = estimateGmi(readings, from, to)!!
        assertEquals(14, gmi.days)
        assertEquals(1.0, gmi.coverage, 1e-9)
        assertEquals(150.0, gmi.meanMgDl, 1e-9)
        assertTrue(gmi.enough)
    }

    @Test
    fun minuteDataDoesNotOutweighFifteenMinuteData() {
        // первый час: минутный режим, 60 замеров по 200; второй час: 4 точки по 100.
        // По отрезкам среднее (4×200 + 4×100) / 8 = 150, а простое среднее было бы ≈ 194
        val minute = (0 until 60).map { Reading(from.plusSeconds(it * 60L), 200.0, 0) }
        val quarter = (0 until 4).map { Reading(from.plusSeconds(3600 + it * 900L), 100.0, 0) }
        val gmi = estimateGmi(minute + quarter, from, from.plusSeconds(7200))!!
        assertEquals(150.0, gmi.meanMgDl, 1e-9)
    }

    @Test
    fun tooLittleDataIsFlagged() {
        val readings = (0 until 4 * 96).map { Reading(from.plusSeconds(it * 900L), 120.0, 0) }   // 4 дня из 14
        val gmi = estimateGmi(readings, from, to)!!
        assertEquals(4.0 / 14, gmi.coverage, 1e-9)
        assertFalse(gmi.enough)
    }

    @Test
    fun readingsOutsideThePeriodAreIgnored() {
        val inside = Reading(from.plusSeconds(60), 100.0, 0)
        val before = Reading(from.minusSeconds(60), 300.0, 0)
        val after = Reading(to, 300.0, 0)
        assertEquals(100.0, estimateGmi(listOf(before, inside, after), from, to)!!.meanMgDl, 1e-9)
        assertNull(estimateGmi(listOf(before, after), from, to))
    }
}
