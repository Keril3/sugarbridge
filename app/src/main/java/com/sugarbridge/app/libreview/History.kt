package com.sugarbridge.app.libreview

import com.sugarbridge.app.glucose.GlucoseLevel
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.levelOf
import com.sugarbridge.app.sync.minuteKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** One day of history: how many readings, the spread, the share in range and how many are already in Health Connect. */
data class DaySummary(
    val date: LocalDate,
    val count: Int,
    val minMgDl: Double,
    val maxMgDl: Double,
    val avgMgDl: Double,
    val inRangePercent: Int,
    val alreadyInHealthConnect: Int,
) {
    val complete: Boolean get() = alreadyInHealthConnect >= count
}

fun Reading.localDate(zone: ZoneId): LocalDate = time.atZone(zone).toLocalDate()

/** Days in order; existing holds the minutes that already have a SugarBridge record in Health Connect. */
fun summarizeDays(
    readings: List<Reading>,
    zone: ZoneId,
    lowMgDl: Double,
    highMgDl: Double,
    existing: Set<Long> = emptySet(),
): List<DaySummary> =
    readings.groupBy { it.localDate(zone) }.toSortedMap().map { (date, day) ->
        val values = day.map { it.mgDl }
        DaySummary(
            date = date,
            count = day.size,
            minMgDl = values.min(),
            maxMgDl = values.max(),
            avgMgDl = values.average(),
            inRangePercent = (100.0 * day.count { levelOf(it.mgDl, lowMgDl, highMgDl) == GlucoseLevel.IN_RANGE } / day.size).roundToInt(),
            alreadyInHealthConnect = day.count { minuteKey(it.time) in existing },
        )
    }

fun readingsOnDays(readings: List<Reading>, days: Set<LocalDate>, zone: ZoneId): List<Reading> =
    readings.filter { it.localDate(zone) in days }

/** The start of the day and of the next day in the phone's time zone. */
fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
    date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

/** Consecutive days are merged into one range: deleting from Health Connect is easier in chunks. */
fun contiguousRanges(days: Collection<LocalDate>): List<ClosedRange<LocalDate>> {
    val sorted = days.toSortedSet()
    val out = ArrayList<ClosedRange<LocalDate>>()
    var start: LocalDate? = null
    var end: LocalDate? = null
    for (d in sorted) {
        if (start != null && end != null && d == end.plusDays(1)) {
            end = d
        } else {
            if (start != null && end != null) out += start..end
            start = d
            end = d
        }
    }
    if (start != null && end != null) out += start..end
    return out
}
