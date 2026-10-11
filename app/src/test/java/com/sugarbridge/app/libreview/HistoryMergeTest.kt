package com.sugarbridge.app.libreview

import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HistoryMergeTest {

    private val t0 = Instant.parse("2026-10-01T00:00:00Z")
    private fun r(minute: Long, mgDl: Double, scan: Boolean = false) = Reading(t0.plusSeconds(minute * 60), mgDl, 0, scan)

    // the loaded history: minutes 0, 15, 30 and a scan at 15
    private val old = listOf(r(0, 100.0), r(15, 110.0), r(15, 120.0, scan = true), r(30, 130.0))
    // a newer export: 15 the same (rounded differently), 30 changed, 45 and 60 new; minute 0 is no longer in it
    private val new = listOf(r(15, 110.4), r(30, 140.0), r(45, 150.0), r(60, 160.0))

    @Test
    fun thePlanCountsWhatIsNewWhatIsTheSameAndWhatDiffers() {
        val plan = planMerge(old, new)
        assertEquals(4, plan.incoming)
        assertEquals(2, plan.added)
        assertEquals(1, plan.same)
        assertEquals(1, plan.changed)
        assertEquals(2, plan.onlyOld)   // minute 0 and the scan at 15
        assertEquals(t0.plusSeconds(15 * 60), plan.from)
        assertEquals(t0.plusSeconds(60 * 60), plan.to)
    }

    @Test
    fun addKeepsTheLoadedValues() {
        val merged = merge(old, new, MergeMode.ADD)
        assertEquals(listOf(0L, 15L, 15L, 30L, 45L, 60L), merged.map { (it.time.epochSecond - t0.epochSecond) / 60 })
        assertEquals(130.0, merged.single { it.time == t0.plusSeconds(30 * 60) }.mgDl, 0.0)
        assertEquals(110.0, merged.first { it.time == t0.plusSeconds(15 * 60) && !it.scan }.mgDl, 0.0)
    }

    @Test
    fun overwriteTakesTheNewValues() {
        val merged = merge(old, new, MergeMode.OVERWRITE)
        assertEquals(6, merged.size)
        assertEquals(140.0, merged.single { it.time == t0.plusSeconds(30 * 60) }.mgDl, 0.0)
        assertEquals(120.0, merged.single { it.scan }.mgDl, 0.0)   // nothing new for the scan: it stays
    }

    @Test
    fun replaceKeepsOnlyTheNewExport() {
        assertEquals(new, merge(old, new, MergeMode.REPLACE))
    }

    @Test
    fun theStoredHistoryReadsBackTheSame() {
        val h = SavedHistory(old, GlucoseUnit.MGDL, "glucose\texport.csv", Instant.ofEpochSecond(1_791_817_200))
        val back = HistoryStore.decode(HistoryStore.encode(h))!!
        assertEquals(old, back.readings)
        assertEquals(GlucoseUnit.MGDL, back.unit)
        assertEquals("glucose export.csv", back.source)
        assertEquals(h.updatedAt, back.updatedAt)
    }

    @Test
    fun anythingElseIsNotAHistory() {
        assertNull(HistoryStore.decode(""))
        assertNull(HistoryStore.decode("Glucose Data,Generated on,08-10-2026\n1,2,3"))
        assertNull(HistoryStore.decode("sugarbridge-history 1\tMMOL\t1\tx\nnot,a,number"))
    }
}
