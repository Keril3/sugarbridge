package com.sugarbridge.app.glucose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SeriesTest {

    private val t0 = Instant.parse("2026-10-01T00:00:00Z")

    /** Every 15 minutes for [days] days, with values going up and down. */
    private fun series(days: Int, skip: (Int) -> Boolean = { false }): List<Reading> =
        (0 until days * 96).filterNot(skip).map { i -> Reading(t0.plus(Duration.ofMinutes(15L * i)), 100.0 + (i % 8) * 10, 0) }

    @Test
    fun betweenFindsExactlyThePeriod() {
        val all = series(3)
        val day2 = all.between(t0.plus(Duration.ofDays(1)), t0.plus(Duration.ofDays(2)))
        assertEquals(96, day2.size)
        assertEquals(t0.plus(Duration.ofDays(1)), day2.first().time)
        assertTrue(day2.last().time < t0.plus(Duration.ofDays(2)))
        assertEquals(0, all.between(t0.minus(Duration.ofDays(5)), t0).size)
        assertEquals(all.size, all.between(t0, t0.plus(Duration.ofDays(30))).size)
    }

    @Test
    fun downsampleKeepsShortListsAndThinsLongOnes() {
        val week = series(1)
        assertEquals(week, downsample(week, 500))
        val year = series(365)   // 35 040 readings
        val thin = downsample(year, 700)
        assertTrue("got ${thin.size}", thin.size in 600..700)
        // averages stay within the data and the time order is kept
        assertTrue(thin.all { it.mgDl in 100.0..170.0 })
        assertEquals(thin.sortedBy { it.time }, thin)
        assertEquals(year.map { it.mgDl }.average(), thin.map { it.mgDl }.average(), 1.0)
    }

    @Test
    fun aGapStaysAGap() {
        // ten days without readings in the middle of a hundred
        val gapped = series(100) { i -> i / 96 in 40 until 50 }
        val thin = downsample(gapped, 400)
        val longest = thin.zipWithNext { a, b -> Duration.between(a.time, b.time) }.max()
        assertTrue("longest step $longest", longest >= Duration.ofDays(9))
    }
}
