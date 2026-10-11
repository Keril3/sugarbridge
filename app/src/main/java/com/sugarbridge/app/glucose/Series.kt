package com.sugarbridge.app.glucose

import java.time.Instant

/** The readings with from <= time < to, out of readings sorted by time; found by halving, not by a full pass. */
fun List<Reading>.between(from: Instant, to: Instant): List<Reading> {
    val start = firstIndexAtOrAfter(from)
    val end = firstIndexAtOrAfter(to)
    return if (start < end) subList(start, end) else emptyList()
}

private fun List<Reading>.firstIndexAtOrAfter(t: Instant): Int {
    var low = 0
    var high = size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (this[mid].time < t) low = mid + 1 else high = mid
    }
    return low
}

/**
 * At most [points] readings for a chart: the time is cut into equal slices and each slice becomes its average.
 * A slice without readings stays empty, so a gap in the data stays a gap. Shorter lists come back as they are.
 */
fun downsample(readings: List<Reading>, points: Int): List<Reading> {
    if (readings.size <= points || points < 2) return readings
    val start = readings.first().time.epochSecond
    val span = (readings.last().time.epochSecond - start).coerceAtLeast(1)
    val slices = LinkedHashMap<Int, MutableList<Reading>>()
    for (r in readings) {
        val slice = ((r.time.epochSecond - start) * points / (span + 1)).toInt()
        slices.getOrPut(slice) { ArrayList() } += r
    }
    return slices.values.map { slice ->
        Reading(
            time = Instant.ofEpochSecond(slice.sumOf { it.time.epochSecond } / slice.size),
            mgDl = slice.sumOf { it.mgDl } / slice.size,
            trend = 0,
        )
    }
}
