package com.sugarbridge.app.libreview

import com.sugarbridge.app.glucose.GlucoseLevel
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.glucose.levelOf
import com.sugarbridge.app.sync.minuteKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Один день истории: сколько замеров, разброс, доля в целевом диапазоне и сколько уже есть в Health Connect. */
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

/** Дни по порядку; existing — минуты, на которые в Health Connect уже есть запись SugarBridge. */
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

/** Начало дня и начало следующего дня в часовом поясе телефона. */
fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
    date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()

/** Подряд идущие дни склеиваются в один промежуток: удалять из Health Connect удобнее кусками. */
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
