package com.sugarbridge.app.sync

import com.sugarbridge.app.glucose.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SyncLogicTest {

    private val base = Instant.parse("2026-10-08T12:00:00Z")
    private fun at(minutes: Long, seconds: Long = 0) = base.plusSeconds(minutes * 60 + seconds)
    private fun r(minutes: Long, seconds: Long = 0, mgDl: Double = 100.0, scan: Boolean = false) =
        Reading(at(minutes, seconds), mgDl, 0, scan)

    @Test
    fun sameMinuteFromLibreLinkUpAndCsvGivesSameKey() {
        // LibreLinkUp writes "12:07:28", the LibreView CSV "12:07": it is one reading
        assertEquals(minuteKey(at(7)), minuteKey(at(7, 28)))
        assertEquals(recordIdFor(at(7)), recordIdFor(at(7, 59)))
        assertTrue(minuteKey(at(8)) != minuteKey(at(7, 59)))
        assertEquals("libre-m${base.epochSecond / 60 + 7}", recordIdFor(at(7)))
    }

    @Test
    fun onePerMinuteKeepsAutomaticReading() {
        val result = onePerMinute(listOf(r(7, 5, 120.0, scan = true), r(7, 40, 110.0), r(3)))
        assertEquals(listOf(100.0, 110.0), result.map { it.mgDl })
    }

    @Test
    fun freshSkipsWrittenMinutesAndEverythingUpToFloor() {
        val state = SyncState(written = setOf(minuteKey(at(30))), floor = at(10).epochSecond)
        val fresh = state.fresh(listOf(r(5), r(10), r(15), r(30, 20), r(45)), now = at(60))
        assertEquals(listOf(at(15), at(45)), fresh.map { it.time })
    }

    @Test
    fun afterRemembersWrittenMinutesAndForgetsOldOnes() {
        val now = at(13 * 60 + 30)   // 13.5 hours after base
        val state = SyncState(written = setOf(minuteKey(at(0)), minuteKey(at(60))))
            .after(listOf(r(13 * 60 + 29)), now)
        // the minute at(0) is older than 13 hours and forgotten, at(60) is still needed
        assertEquals(setOf(minuteKey(at(60)), minuteKey(at(13 * 60 + 29))), state.written)
        assertEquals(now.minus(Duration.ofHours(13)).epochSecond, state.floor)
    }

    @Test
    fun floorNeverMovesBackwards() {
        val legacy = at(600).epochSecond   // last_synced from the old version
        val state = SyncState(floor = legacy).after(emptyList(), at(60))
        assertEquals(legacy, state.floor)
    }

    @Test
    fun legacyLastSyncedPreventsRewritingOldReadings() {
        // before the update readings were written with other ids; writing them again would duplicate them
        val state = SyncState(floor = at(20).epochSecond)
        assertEquals(listOf(at(25)), state.fresh(listOf(r(10), r(20), r(25)), now = at(60)).map { it.time })
    }

    @Test
    fun readingsFromTheFutureWaitForTheirTime() {
        // the sensor clock runs 30 seconds ahead: Health Connect won't take such a reading, it is written next time
        val readings = listOf(r(5), r(10, 30))
        assertEquals(listOf(at(5)), SyncState().fresh(readings, now = at(10)).map { it.time })
        assertEquals(listOf(at(5), at(10, 30)), SyncState().fresh(readings, now = at(11)).map { it.time })
    }

    @Test
    fun forgetAllowsRewritingDeletedMinutes() {
        val state = SyncState(written = setOf(minuteKey(at(1)), minuteKey(at(5)), minuteKey(at(9))))
            .forget(at(4), at(9))
        assertEquals(setOf(minuteKey(at(1)), minuteKey(at(9))), state.written)
    }

    @Test
    fun keysAndReadingsSurviveSaving() {
        val keys = setOf(5L, 1L, 300L)
        assertEquals(keys, decodeKeys(encodeKeys(keys)))
        assertEquals(emptySet<Long>(), decodeKeys(null))
        assertEquals(setOf(1L, 2L), decodeKeys("1, 2,junk,"))

        val readings = listOf(Reading(at(1), 115.3, 4), Reading(at(2), 98.0, 0))
        assertEquals(readings, decodeReadings(encodeReadings(readings)))
        assertEquals(emptyList<Reading>(), decodeReadings(""))
        assertEquals(1, decodeReadings("${at(1).epochSecond}:100.0:3;broken:record").size)
    }

    @Test
    fun typicalUpdateIntervalIsMedianOfGaps() {
        assertEquals(1.0, typicalUpdateMinutes(listOf(at(0), at(1), at(2), at(3)))!!, 1e-9)
        // a 45-minute gap (the phone slept) doesn't count
        assertEquals(1.0, typicalUpdateMinutes(listOf(at(0), at(1), at(46), at(47)))!!, 1e-9)
        assertEquals(15.0, typicalUpdateMinutes(listOf(at(0), at(15), at(30)))!!, 1e-9)
        assertNull(typicalUpdateMinutes(listOf(at(0), at(1))))
    }
}
