package com.sugarbridge.app.libre

import com.sugarbridge.app.glucose.Reading
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// How LibreLinkUp writes time: "10/8/2026 6:01:00 PM", month/day/year in the 12-hour format
val LIBRE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

fun parseReading(item: JSONObject): Reading {
    // FactoryTimestamp is UTC, Timestamp is the local time of the phone with LibreLink
    val utc = LocalDateTime.parse(item.getString("FactoryTimestamp"), LIBRE_TIME)
    return Reading(
        time = utc.toInstant(ZoneOffset.UTC),
        mgDl = item.getDouble("ValueInMgPerDl"),
        trend = item.optInt("TrendArrow", 0),   // only the current value has a trend
    )
}

/** What Health Connect takes (up to 50 mmol/L), without the 0 of a point with no value: it refuses a whole batch for one such. */
private val WRITABLE_MGDL = 1.0..900.0

/** A point of the answer, or null for one that can't be read or written: the others mustn't be lost with it. */
private fun readingOrNull(item: JSONObject?): Reading? =
    item?.let { runCatching { parseReading(it) }.getOrNull() }?.takeIf { it.mgDl in WRITABLE_MGDL }

/** The /graph answer → ~12 hours of history plus the current value, without repeats, by time. */
fun parseGraph(data: JSONObject): List<Reading> {
    val byTime = sortedMapOf<Instant, Reading>()   // a map kept sorted by time
    val history = data.optJSONArray("graphData")
    if (history != null) {
        for (i in 0 until history.length()) {
            readingOrNull(history.optJSONObject(i))?.let { byTime[it.time] = it }
        }
    }
    // the current value has a trend, so it wins over the history entry
    readingOrNull(data.optJSONObject("connection")?.optJSONObject("glucoseMeasurement"))?.let { byTime[it.time] = it }
    return byTime.values.toList()
}
