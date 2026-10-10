package com.sugarbridge.app

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Один замер: время в UTC, сахар в мг/дл и стрелка тренда (1 — быстро вниз … 5 — быстро вверх, 0 — нет).
 * scan = true — ручное сканирование из истории LibreView, а не автоматический замер.
 */
data class Reading(val time: Instant, val mgDl: Double, val trend: Int, val scan: Boolean = false) {
    val mmol: Double get() = mmolOf(mgDl)
}

const val MGDL_PER_MMOL = 18.016

fun mmolOf(mgDl: Double): Double = Math.round(mgDl / MGDL_PER_MMOL * 10) / 10.0

enum class GlucoseUnit(val label: String) { MMOL("ммоль/л"), MGDL("мг/дл") }

/** Только число: "6,4" в ммоль/л (в русской локали — с запятой) или "115" в мг/дл. */
fun formatGlucose(mgDl: Double, unit: GlucoseUnit, locale: Locale = Locale.getDefault()): String = when (unit) {
    GlucoseUnit.MMOL -> String.format(locale, "%.1f", mmolOf(mgDl))
    GlucoseUnit.MGDL -> mgDl.roundToInt().toString()
}

enum class GlucoseLevel { LOW, IN_RANGE, HIGH }

/** Где значение относительно целевого диапазона (границы включены в норму). */
fun levelOf(mgDl: Double, lowMgDl: Double, highMgDl: Double): GlucoseLevel = when {
    mgDl < lowMgDl -> GlucoseLevel.LOW
    mgDl > highMgDl -> GlucoseLevel.HIGH
    else -> GlucoseLevel.IN_RANGE
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

val Reading.arrow: String get() = ARROWS.getOrElse(trend) { "" }

/** "6,4 ммоль/л ↗ в 20:00" — для экрана и уведомления. */
fun Reading.text(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    unit: GlucoseUnit = GlucoseUnit.MMOL,
): String {
    val value = formatGlucose(mgDl, unit, locale) + " " + unit.label   // в русской локали будет запятая: 6,4
    val at = "в " + time.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    return listOf(value, arrow, at).filter { it.isNotEmpty() }.joinToString(" ")
}
