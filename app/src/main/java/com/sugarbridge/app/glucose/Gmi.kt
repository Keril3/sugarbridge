package com.sugarbridge.app.glucose

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Расчётный HbA1c — GMI (Glucose Management Indicator, Bergenstal и др., Diabetes Care, 2018):
 * GMI (%) = 3,31 + 0,02392 × средняя глюкоза (мг/дл). Это оценка по данным датчика, а не анализ крови:
 * лабораторный HbA1c может отличаться от неё на 0,5 % и больше.
 */
fun gmiPercent(meanMgDl: Double): Double = 3.31 + 0.02392 * meanMgDl

/** HbA1c в ммоль/моль (единицы IFCC) из процентов (NGSP). */
fun hba1cMmolPerMol(percent: Double): Int = ((percent - 2.15) * 10.929).roundToInt()

/**
 * Итог за период. coverage — доля 15-минутных отрезков, где были данные. По международному
 * консенсусу оценке можно доверять, если данных хотя бы 70 % за 14 дней и больше.
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
 * Средняя глюкоза по 15-минутным отрезкам: сначала среднее внутри отрезка, потом по отрезкам.
 * Так минутные замеры из минутного режима весят столько же, сколько точки раз в 15 минут.
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

    /** null — данных нет совсем. */
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

/** GMI за промежуток по готовому списку замеров (например, из выгрузки LibreView). */
fun estimateGmi(readings: List<Reading>, from: Instant, to: Instant): GmiResult? =
    GlucoseBins(from, to).apply { readings.forEach { add(it.time, it.mgDl) } }.result()
