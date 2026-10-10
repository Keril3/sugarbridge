package com.sugarbridge.app.libre

import com.sugarbridge.app.glucose.Reading
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Так LibreLinkUp пишет время: "10/8/2026 6:01:00 PM" — месяц/день/год, 12-часовой формат
val LIBRE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US)

fun parseReading(item: JSONObject): Reading {
    // FactoryTimestamp — время в UTC, Timestamp — местное время телефона с LibreLink
    val utc = LocalDateTime.parse(item.getString("FactoryTimestamp"), LIBRE_TIME)
    return Reading(
        time = utc.toInstant(ZoneOffset.UTC),
        mgDl = item.getDouble("ValueInMgPerDl"),
        trend = item.optInt("TrendArrow", 0),   // тренд есть только у текущего значения
    )
}

/** Ответ /graph → история за ~12 часов плюс текущее значение, без повторов, по времени. */
fun parseGraph(data: JSONObject): List<Reading> {
    val byTime = sortedMapOf<Instant, Reading>()   // словарь, сразу упорядоченный по времени
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
        byTime[r.time] = r   // у текущего значения есть тренд, оно важнее записи из истории
    }
    return byTime.values.toList()
}
