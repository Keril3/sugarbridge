package com.sugarbridge.app.glucose

import java.time.Instant
import java.time.ZoneId
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
