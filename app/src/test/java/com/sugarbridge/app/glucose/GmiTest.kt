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
        // the table from Bergenstal 2018: 100 mg/dL → 5.7%, 150 → 6.9%, 200 → 8.1%
        assertEquals(5.7, gmiPercent(100.0), 0.05)
        assertEquals(6.9, gmiPercent(150.0), 0.05)
        assertEquals(8.1, gmiPercent(200.0), 0.05)
    }

    @Test
    fun percentToMmolPerMol() {
        assertEquals(53, hba1cMmolPerMol(7.0))   // 7% ≈ 53 mmol/mol
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

    /** Libre's 15 minutes are a minute or two off: two readings may fall into one slot, the data is still full. */
    @Test
    fun unevenFifteenMinutesAreFullData() {
        val offsets = listOf(0L, 75L, -60L, 110L, -100L, 30L)   // seconds off the regular time
        val readings = (0 until 14 * 96).map { Reading(from.plusSeconds(400 + it * 900L + offsets[it % offsets.size]), 150.0, 0) }
        val gmi = estimateGmi(readings, from, to)!!
        assertEquals(1.0, gmi.coverage, 1e-9)
        assertEquals(100, Math.round(gmi.coverage * 100).toInt())
    }

    @Test
    fun gapsCountAsMissing() {
        // a reading every 30 minutes covers half the time; an hour without readings is an hour missing
        val sparse = (0 until 14 * 48).map { Reading(from.plusSeconds(it * 1800L), 150.0, 0) }
        assertEquals(0.5, estimateGmi(sparse, from, to)!!.coverage, 1e-9)
        val day = Duration.ofDays(1)
        val withGap = (0 until 96).map { from.plusSeconds(it * 900L) }.filterNot { it >= from.plusSeconds(36_000) && it < from.plusSeconds(39_600) }
        val gmi = estimateGmi(withGap.map { Reading(it, 120.0, 0) }, from, from.plus(day))!!
        assertEquals(1 - 3600.0 / day.seconds, gmi.coverage, 1e-9)   // 4 readings missing: the hour is missing
    }

    /** The time in range weighs every 15 minutes the same, however many readings they have. */
    @Test
    fun timeInRangeWeighsSlotsNotReadings() {
        // first hour: minute mode, 60 readings of 200 (high); second hour: 4 points of 100 (in range)
        val minute = (0 until 60).map { Reading(from.plusSeconds(it * 60L), 200.0, 0) }
        val quarter = (0 until 4).map { Reading(from.plusSeconds(3600 + it * 900L), 100.0, 0) }
        val bins = GlucoseBins(from, from.plusSeconds(7200), lowMgDl = 70.0, highMgDl = 180.0)
        (minute + quarter).forEach { bins.add(it.time, it.mgDl) }
        assertEquals(0.5, bins.result()!!.inRange, 1e-9)   // a plain share of readings would say 4 of 64
        val wide = GlucoseBins(from, from.plusSeconds(7200), lowMgDl = 70.0, highMgDl = 250.0)
        (minute + quarter).forEach { wide.add(it.time, it.mgDl) }
        assertEquals(1.0, wide.result()!!.inRange, 1e-9)   // the target range decides
    }

    @Test
    fun minuteDataDoesNotOutweighFifteenMinuteData() {
        // first hour: minute mode, 60 readings of 200; second hour: 4 points of 100.
        // Over the slots the mean is (4×200 + 4×100) / 8 = 150, a plain mean would be ≈ 194
        val minute = (0 until 60).map { Reading(from.plusSeconds(it * 60L), 200.0, 0) }
        val quarter = (0 until 4).map { Reading(from.plusSeconds(3600 + it * 900L), 100.0, 0) }
        val gmi = estimateGmi(minute + quarter, from, from.plusSeconds(7200))!!
        assertEquals(150.0, gmi.meanMgDl, 1e-9)
    }

    @Test
    fun tooLittleDataIsFlagged() {
        val readings = (0 until 4 * 96).map { Reading(from.plusSeconds(it * 900L), 120.0, 0) }   // 4 days of 14
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
