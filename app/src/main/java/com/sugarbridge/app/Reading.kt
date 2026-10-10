package com.sugarbridge.app

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Один замер: время в UTC, сахар в мг/дл и стрелка тренда (1 — быстро вниз … 5 — быстро вверх, 0 — нет). */
data class Reading(val time: Instant, val mgDl: Double, val trend: Int) {
    val mmol: Double get() = Math.round(mgDl / 18.016 * 10) / 10.0
}

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

private val ARROWS = listOf("", "↓", "↘", "→", "↗", "↑")

/** "6,4 ммоль/л ↗ в 20:00" — для экрана и уведомления. */
fun Reading.text(zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val value = String.format(locale, "%.1f ммоль/л", mmol)   // в русской локали будет запятая: 6,4
    val arrow = ARROWS.getOrElse(trend) { "" }
    val at = "в " + time.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    return listOf(value, arrow, at).filter { it.isNotEmpty() }.joinToString(" ")
}
