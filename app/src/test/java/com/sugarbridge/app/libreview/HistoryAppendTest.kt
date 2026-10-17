package com.sugarbridge.app.libreview

import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.Reading
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** What a sync adds to the kept history: LibreView's pace, one reading per 15 minutes, never a scan or an old one. */
class HistoryAppendTest {

    private val end = Instant.parse("2026-10-12T10:00:00Z")   // the history's last reading
    private fun at(minutes: Long, scan: Boolean = false) = Reading(end.plusSeconds(minutes * 60), 100.0, 0, scan = scan)
    private fun minutesOf(readings: List<Reading>) = readings.map { (it.time.epochSecond - end.epochSecond) / 60 }

    @Test
    fun minuteReadingsAreThinnedToLibreViewsPace() {
        val added = HistoryStore.thin(end.epochSecond, (1L..60L).map { at(it) })
        assertEquals(listOf(14L, 28L, 42L, 56L), minutesOf(added))
    }

    @Test
    fun onlyNewerAutomaticReadingsAreAdded() {
        val added = HistoryStore.thin(end.epochSecond, listOf(at(-30), at(0), at(15), at(20, scan = true), at(30)))
        assertEquals(listOf(15L, 30L), minutesOf(added))
    }

    @Test
    fun theOrderTheyCameInDoesNotMatter() {
        assertEquals(listOf(15L, 30L), minutesOf(HistoryStore.thin(end.epochSecond, listOf(at(30), at(15)))))
    }

    @Test
    fun addedLinesAreReadBackWithTheRest() {
        val history = SavedHistory(listOf(at(0)), GlucoseUnit.MMOL, "LibreView", end)
        val added = HistoryStore.thin(end.epochSecond, listOf(at(15), at(30)))
        // the file is the encoded history with the added lines after it, exactly what append writes
        val text = HistoryStore.encode(history) + HistoryStore.encode(SavedHistory(added, history.unit, "", end)).substringAfter('\n')
        assertEquals(listOf(0L, 15L, 30L), minutesOf(HistoryStore.decode(text)!!.readings))
    }
}
