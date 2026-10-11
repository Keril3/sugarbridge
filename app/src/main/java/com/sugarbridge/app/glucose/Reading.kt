package com.sugarbridge.app.glucose

import androidx.annotation.StringRes
import com.sugarbridge.app.R
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One reading: time in UTC, glucose in mg/dL and the trend arrow (1 = falling fast … 5 = rising fast, 0 = none).
 * scan = true marks a manual scan from the LibreView history rather than an automatic reading.
 */
data class Reading(val time: Instant, val mgDl: Double, val trend: Int, val scan: Boolean = false) {
    val mmol: Double get() = mmolOf(mgDl)
}

const val MGDL_PER_MMOL = 18.016

fun mmolOf(mgDl: Double): Double = Math.round(mgDl / MGDL_PER_MMOL * 10) / 10.0

enum class GlucoseUnit(@param:StringRes val label: Int) { MMOL(R.string.unit_mmol), MGDL(R.string.unit_mgdl) }

/** Just the number: "6.4" in mmol/L (with a decimal comma where the language uses one) or "115" in mg/dL. */
fun formatGlucose(mgDl: Double, unit: GlucoseUnit, locale: Locale = Locale.getDefault()): String = when (unit) {
    GlucoseUnit.MMOL -> String.format(locale, "%.1f", mmolOf(mgDl))
    GlucoseUnit.MGDL -> mgDl.roundToInt().toString()
}

enum class GlucoseLevel { LOW, IN_RANGE, HIGH }

/** Where a value is relative to the target range (the bounds count as in range). */
fun levelOf(mgDl: Double, lowMgDl: Double, highMgDl: Double): GlucoseLevel = when {
    mgDl < lowMgDl -> GlucoseLevel.LOW
    mgDl > highMgDl -> GlucoseLevel.HIGH
    else -> GlucoseLevel.IN_RANGE
}

private val ARROWS = listOf("", "↓", "↘", "→", "↗", "↑")

val Reading.arrow: String get() = ARROWS.getOrElse(trend) { "" }

/** "6.4 mmol/L ↗": value, unit and trend for messages and the notification. */
fun Reading.text(unitLabel: String, unit: GlucoseUnit = GlucoseUnit.MMOL, locale: Locale = Locale.getDefault()): String =
    listOf(formatGlucose(mgDl, unit, locale), unitLabel, arrow).filter { it.isNotEmpty() }.joinToString(" ")
