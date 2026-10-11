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

/** The /graph answer → ~12 hours of history plus the current value, without repeats, by time. */
fun parseGraph(data: JSONObject): List<Reading> {
    val byTime = sortedMapOf<Instant, Reading>()   // a map kept sorted by time
    val history = data.optJSONArray("graphData")
    if (history != null) {
        for (i in 0 until history.length()) {
            val r = parseReading(history.getJSONObject(i))
            byTime[r.time] = r
        }
    }
    val current = data.optJSONObject("connection")?.optJSONObject("glucoseMeasurement")
    if (current != null) {
        val r = parseReading(current)
        byTime[r.time] = r   // the current value has a trend, so it wins over the history entry
    }
    return byTime.values.toList()
}
