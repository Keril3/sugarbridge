package com.sugarbridge.app.libre

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LibreJsonTest {

    private fun item(stamp: String, mgDl: Int, trend: Int? = null): JSONObject {
        val o = JSONObject().put("FactoryTimestamp", stamp).put("ValueInMgPerDl", mgDl)
        if (trend != null) o.put("TrendArrow", trend)
        return o
    }

    @Test
    fun timeIsReadAsUtc() {
        val r = parseReading(item("10/8/2026 6:01:00 PM", 115))
        assertEquals(Instant.parse("2026-10-08T18:01:00Z"), r.time)
    }

    @Test
    fun zeroPaddedTimeGivesSameMoment() {
        val a = parseReading(item("10/8/2026 6:01:00 PM", 115))
        val b = parseReading(item("10/08/2026 06:01:00 PM", 115))
        assertEquals(a.time, b.time)
    }

    @Test
    fun midnightAndNoonAreNotMixedUp() {
        assertEquals(Instant.parse("2026-10-08T00:05:00Z"), parseReading(item("10/8/2026 12:05:00 AM", 100)).time)
        assertEquals(Instant.parse("2026-10-08T12:05:00Z"), parseReading(item("10/8/2026 12:05:00 PM", 100)).time)
    }

    @Test
    fun missingTrendMeansZero() {
        assertEquals(0, parseReading(item("10/8/2026 6:01:00 PM", 115)).trend)
        assertEquals(4, parseReading(item("10/8/2026 6:01:00 PM", 115, trend = 4)).trend)
    }

    @Test
    fun graphIsSortedAndCurrentValueWinsOverHistory() {
        val data = JSONObject()
            .put("graphData", JSONArray()
                .put(item("10/8/2026 6:00:00 PM", 120))          // the same moment as the current value
                .put(item("10/8/2026 5:30:00 PM", 100))
                .put(item("10/8/2026 5:45:00 PM", 110)))
            .put("connection", JSONObject().put("glucoseMeasurement", item("10/8/2026 6:00:00 PM", 121, trend = 3)))
        val readings = parseGraph(data)
        assertEquals(listOf(100.0, 110.0, 121.0), readings.map { it.mgDl })
        assertEquals(3, readings.last().trend)
    }
}
