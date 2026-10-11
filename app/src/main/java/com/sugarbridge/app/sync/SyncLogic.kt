package com.sugarbridge.app.sync

import com.sugarbridge.app.glucose.Reading
import java.time.Duration
import java.time.Instant

/**
 * The number of the reading's minute. The same reading comes from LibreLinkUp with seconds ("12:07:28")
 * and from the LibreView CSV without them ("12:07"). Their minutes match, so there are no duplicates.
 */
fun minuteKey(time: Instant): Long = Math.floorDiv(time.epochSecond, 60L)

/** The Health Connect record id: writing again with the same id updates the record instead of duplicating it. */
fun recordIdFor(time: Instant): String = "libre-m${minuteKey(time)}"

/** At most one reading per minute; on a clash an automatic reading wins over a scan. */
fun onePerMinute(readings: List<Reading>): List<Reading> =
    readings.groupBy { minuteKey(it.time) }
        .map { (_, same) -> same.firstOrNull { !it.scan } ?: same.first() }
        .sortedBy { it.time }

/**
 * What is already written to Health Connect: the minutes of the last ~13 hours and a "floor":
 * readings not newer than it count as done (this is how the old last_synced carries over).
 */
data class SyncState(val written: Set<Long> = emptySet(), val floor: Long = 0) {

    /**
     * Readings not yet in Health Connect. Readings "from the future" wait for their time:
     * Health Connect rejects them together with the whole batch, and a reading's time comes from the sensor's
     * clock, which may run ahead of the phone.
     */
    fun fresh(readings: List<Reading>, now: Instant): List<Reading> =
        onePerMinute(readings).filter {
            it.time.epochSecond > floor && minuteKey(it.time) !in written && !it.time.isAfter(now)
        }

    /** The state after writing: add the minutes, forget whatever is older than the LibreLinkUp history window. */
    fun after(newlyWritten: List<Reading>, now: Instant, keep: Duration = KEEP): SyncState {
        val horizon = now.minus(keep).epochSecond
        val keys = (written + newlyWritten.map { minuteKey(it.time) }).filterTo(HashSet()) { it * 60 >= horizon }
        return SyncState(keys, maxOf(floor, horizon))
    }

    /** After records are deleted from Health Connect, these minutes can be written again. */
    fun forget(from: Instant, to: Instant): SyncState {
        val a = minuteKey(from)
        val b = minuteKey(to)
        return copy(written = written.filterTo(HashSet()) { it < a || it >= b })
    }

    companion object {
        val KEEP: Duration = Duration.ofHours(13)   // LibreLinkUp returns 12 hours of history
    }
}

fun encodeKeys(keys: Set<Long>): String = keys.sorted().joinToString(",")

fun decodeKeys(text: String?): Set<Long> =
    text.orEmpty().split(',').mapNotNullTo(HashSet()) { it.trim().toLongOrNull() }

/** A compact form of readings for the preferences: "epochSecond:mgdl:trend;...". */
fun encodeReadings(readings: List<Reading>): String =
    readings.joinToString(";") { "${it.time.epochSecond}:${it.mgDl}:${it.trend}" }

fun decodeReadings(text: String?): List<Reading> =
    text.orEmpty().split(';').mapNotNull { part ->
        val f = part.split(':')
        if (f.size != 3) return@mapNotNull null
        val time = f[0].toLongOrNull() ?: return@mapNotNull null
        val mgDl = f[1].toDoubleOrNull() ?: return@mapNotNull null
        Reading(Instant.ofEpochSecond(time), mgDl, f[2].toIntOrNull() ?: 0)
    }

/**
 * How often LibreLinkUp actually delivers a new current value: the median interval in minutes.
 * Gaps longer than 20 minutes (the phone slept, no network) are ignored. null while there is too little data.
 */
fun typicalUpdateMinutes(seen: List<Instant>): Double? {
    val gaps = seen.distinct().sorted().zipWithNext { a, b -> Duration.between(a, b).seconds / 60.0 }
        .filter { it in 0.25..20.0 }
        .sorted()
    if (gaps.size < 2) return null
    val mid = gaps.size / 2
    return if (gaps.size % 2 == 1) gaps[mid] else (gaps[mid - 1] + gaps[mid]) / 2
}

/** The median of delays in seconds, in minutes; null until there are at least two. */
fun medianMinutes(seconds: List<Long>): Double? {
    if (seconds.size < 2) return null
    val sorted = seconds.sorted()
    val mid = sorted.size / 2
    val median = if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    return median / 60.0
}
