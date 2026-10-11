package com.sugarbridge.app.glucose

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Estimated HbA1c: GMI (Glucose Management Indicator, Bergenstal et al., Diabetes Care, 2018):
 * GMI (%) = 3.31 + 0.02392 × mean glucose (mg/dL). It is an estimate from sensor data, not a blood test:
 * a lab HbA1c may differ from it by 0.5% or more.
 */
fun gmiPercent(meanMgDl: Double): Double = 3.31 + 0.02392 * meanMgDl

/** HbA1c in mmol/mol (IFCC units) from percent (NGSP). */
fun hba1cMmolPerMol(percent: Double): Int = ((percent - 2.15) * 10.929).roundToInt()

/**
 * The result for a period. coverage is the share of 15-minute slots that had data. By the international
 * consensus the estimate can be trusted with at least 70% of the data over 14 days or more.
 */
data class GmiResult(val days: Int, val meanMgDl: Double, val coverage: Double) {
    val percent: Double get() = gmiPercent(meanMgDl)
    val mmolPerMol: Int get() = hba1cMmolPerMol(percent)
    val enough: Boolean get() = coverage >= MIN_COVERAGE

    companion object {
        const val MIN_COVERAGE = 0.7
    }
}

/**
 * Mean glucose over 15-minute slots: first the mean within a slot, then over the slots.
 * This way readings from minute mode weigh as much as points every 15 minutes.
 */
class GlucoseBins(private val from: Instant, private val to: Instant) {
    private val count = ((Duration.between(from, to).seconds + BIN_SECONDS - 1) / BIN_SECONDS).toInt().coerceAtLeast(1)
    private val sums = DoubleArray(count)
    private val hits = IntArray(count)

    fun add(time: Instant, mgDl: Double) {
        if (time.isBefore(from) || !time.isBefore(to)) return
        val bin = (Duration.between(from, time).seconds / BIN_SECONDS).toInt()
        sums[bin] += mgDl
        hits[bin]++
    }

    /** null when there is no data at all. */
    fun result(): GmiResult? {
        var filled = 0
        var total = 0.0
        for (i in 0 until count) {
            if (hits[i] > 0) {
                filled++
                total += sums[i] / hits[i]
            }
        }
        if (filled == 0) return null
        val days = Duration.between(from, to).toDays().toInt().coerceAtLeast(1)
        return GmiResult(days, total / filled, filled.toDouble() / count)
    }

    companion object {
        private const val BIN_SECONDS = 15 * 60L
    }
}

/** GMI for a period from a list of readings (e.g. from a LibreView export). */
fun estimateGmi(readings: List<Reading>, from: Instant, to: Instant): GmiResult? =
    GlucoseBins(from, to).apply { readings.forEach { add(it.time, it.mgDl) } }.result()
