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
 * The result for a period. coverage is the share of the period covered by readings (see [GlucoseBins]). By the
 * international consensus the estimate can be trusted with at least 70% of the data over 14 days or more.
 * inRange is the time in the target range, 0 to 1, each 15 minutes weighing the same.
 */
data class GmiResult(val days: Int, val meanMgDl: Double, val coverage: Double, val inRange: Double = 0.0) {
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
 *
 * The coverage is counted by time, not by slots. Libre keeps a reading every 15 minutes, but a minute or two
 * off, so with slots two readings sometimes fell into one and the next stayed empty: full data showed as 99%.
 * Now a reading covers the time until the next one if that comes within [MAX_GAP]; after a longer gap, a
 * missing sensor or no connection, it covers only one [INTERVAL].
 */
class GlucoseBins(
    private val from: Instant,
    private val to: Instant,
    private val lowMgDl: Double = 70.0,
    private val highMgDl: Double = 180.0,
) {
    private val count = ((Duration.between(from, to).seconds + BIN_SECONDS - 1) / BIN_SECONDS).toInt().coerceAtLeast(1)
    private val sums = DoubleArray(count)
    private val hits = IntArray(count)
    private val inside = IntArray(count)   // readings in the target range
    private var times = LongArray(1024)   // seconds since [from], for the coverage
    private var size = 0

    fun add(time: Instant, mgDl: Double) {
        if (time.isBefore(from) || !time.isBefore(to)) return
        val since = Duration.between(from, time).seconds
        val bin = (since / BIN_SECONDS).toInt()
        sums[bin] += mgDl
        hits[bin]++
        if (mgDl in lowMgDl..highMgDl) inside[bin]++
        if (size == times.size) times = times.copyOf(size * 2)
        times[size++] = since
    }

    /** The share of the period covered by readings, 0 to 1. */
    private fun coverage(): Double {
        val period = Duration.between(from, to).seconds
        val t = times.copyOf(size).apply { sort() }
        var covered = if (t[0] <= MAX_GAP) t[0] else 0L   // a reading just after the start covers it too
        for (i in t.indices) {
            val gap = (if (i + 1 < t.size) t[i + 1] else period) - t[i]
            covered += if (gap <= MAX_GAP) gap else INTERVAL
        }
        return (covered.toDouble() / period).coerceAtMost(1.0)
    }

    /** null when there is no data at all. */
    fun result(): GmiResult? {
        var filled = 0
        var total = 0.0
        var inRange = 0.0
        for (i in 0 until count) {
            if (hits[i] > 0) {
                filled++
                total += sums[i] / hits[i]
                inRange += inside[i].toDouble() / hits[i]
            }
        }
        if (filled == 0) return null
        val days = Duration.between(from, to).toDays().toInt().coerceAtLeast(1)
        return GmiResult(days, total / filled, coverage(), inRange / filled)
    }

    companion object {
        private const val BIN_SECONDS = 15 * 60L
        private const val INTERVAL = 15 * 60L   // Libre's history keeps a reading this often
        private const val MAX_GAP = 20 * 60L    // the interval with its usual unevenness
    }
}

/** GMI for a period from a list of readings (e.g. from a LibreView export). */
fun estimateGmi(readings: List<Reading>, from: Instant, to: Instant): GmiResult? =
    GlucoseBins(from, to).apply { readings.forEach { add(it.time, it.mgDl) } }.result()
