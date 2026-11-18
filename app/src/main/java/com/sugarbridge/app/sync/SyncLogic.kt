package com.sugarbridge.app.sync

import com.sugarbridge.app.glucose.Reading
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

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

private const val SLOW_AFTER_FAILURES = 3
private const val STALE_READING_MS = 30 * 60_000L
private const val SLOW_POLL_MS = 5 * 60_000L

/**
 * How long minute mode waits before asking LibreLinkUp again: as set, unless asking would only cost battery. After
 * several failures in a row (no connection), or while the newest reading is half an hour old (the sensor is off or
 * LibreLink's phone is), it asks every 5 minutes at most. LibreLinkUp's history fills such a gap anyway.
 */
fun nextPollMillis(everyMs: Long, failures: Int, newestAgeMs: Long?): Long =
    if (failures >= SLOW_AFTER_FAILURES || (newestAgeMs != null && newestAgeMs > STALE_READING_MS)) maxOf(everyMs, SLOW_POLL_MS)
    else everyMs

/**
 * When minute mode asks LibreLinkUp next, while it answers. LibreLinkUp gets a new reading every minute, some seconds
 * after the sensor took it. Asking at a steady rhythm found each one anywhere up to a minute after it got there, half
 * a minute on average, and as that rhythm slowly drifted against LibreLinkUp's, for a while nearly the whole minute
 * every time. So each ask is aimed at the moment the next reading should be there: [offsetSeconds] after its sensor
 * time, and asks still come every interval on average.
 *
 * The offset is learned. An ask that finds the reading it aimed at moves the next one a second earlier. One that comes
 * too early asks once more [RETRY_MS] later and, if the reading is there then, moves the offset later; if it is still
 * not there twice in a row, the offset moves by a whole retry. All this only while the readings come one after
 * another: when LibreLink uploads several at once, as a sleeping phone lets it now and then, the moment says nothing
 * about the next one, and the asks simply keep their rhythm. Nor does an ask that came late, the phone asleep
 * without "Keep the phone awake": it found the reading, but says nothing about how early it could have.
 */
class AskClock(initialOffsetSeconds: Long = DEFAULT_OFFSET_S) {
    /** Seconds after a reading's sensor time by which LibreLinkUp has it, as far as the asks tell. */
    var offsetSeconds: Long = initialOffsetSeconds.coerceIn(MIN_OFFSET_S, MAX_OFFSET_S)
        private set

    /** Whether the next ask is the second one for a reading that wasn't there yet. */
    var secondTry = false
        private set

    private var aimedAt: Instant? = null   // the sensor time of the reading the next ask is for
    private var askAt: Instant? = null     // when the next ask should come
    private var missed = false             // the reading before wasn't there even at the second ask
    private var newest: Instant? = null
    private var step: Duration? = null     // between the last two newest readings

    /** After a failure or with other settings: the next ask isn't aimed at anything yet, the offset stays. */
    fun reset() {
        aimedAt = null
        askAt = null
        secondTry = false
        missed = false
    }

    /**
     * Milliseconds until the next ask, after one made at [askedAt] in which LibreLinkUp's newest reading had the sensor
     * time [latest], with asks every [everyMs] as set.
     */
    fun next(askedAt: Instant, latest: Instant?, everyMs: Long): Long {
        if (latest == null) {
            reset()
            return everyMs
        }
        newest?.let { if (it != latest) step = Duration.between(it, latest) }
        newest = latest
        val aim = aimedAt
        val punctual = askAt?.let { abs(Duration.between(it, askedAt).toMillis()) <= TOLERANCE_MS } == true
        if (aim != null && punctual) {
            val off = Duration.between(aim, latest).toMillis()
            when {
                abs(off) <= TOLERANCE_MS -> {   // it was there
                    offsetSeconds = if (secondTry) offsetSeconds + LATER_S else offsetSeconds - EARLIER_S
                    missed = false
                }
                abs(off + READING_MS) <= TOLERANCE_MS && oneByOne(everyMs) -> {   // the one before it: too early
                    if (!secondTry) {
                        secondTry = true
                        askAt = askedAt.plusMillis(RETRY_MS)
                        return RETRY_MS
                    }
                    if (missed) offsetSeconds += RETRY_MS / 1000
                    missed = true
                }
                // nothing new for longer, or several at once: no telling when the next one comes
            }
            offsetSeconds = offsetSeconds.coerceIn(MIN_OFFSET_S, MAX_OFFSET_S)
        }
        secondTry = false
        // the first reading expected no sooner than half a minute before the interval is up: the asks keep its pace
        val earliest = askedAt.plusMillis(everyMs - READING_MS / 2)
        val first = latest.plusMillis(READING_MS).plusSeconds(offsetSeconds)
        val behind = Duration.between(first, earliest).toMillis()
        val readings = if (behind > 0) (behind + READING_MS - 1) / READING_MS else 0
        val target = latest.plusMillis(READING_MS * (1 + readings))
        aimedAt = target
        val at = target.plusSeconds(offsetSeconds)
        askAt = at
        return Duration.between(askedAt, at).toMillis()
    }

    /** Whether the last new reading followed the one before by whole minutes, no more than an interval: one by one. */
    private fun oneByOne(everyMs: Long): Boolean {
        val ms = step?.toMillis() ?: return false
        val minutes = (ms + READING_MS / 2) / READING_MS
        return minutes >= 1 && minutes * READING_MS <= everyMs && abs(ms - minutes * READING_MS) <= TOLERANCE_MS
    }

    companion object {
        const val DEFAULT_OFFSET_S = 30L
        const val RETRY_MS = 15_000L
        private const val READING_MS = 60_000L     // a Libre sensor reads every minute
        private const val TOLERANCE_MS = 5_000L    // the sensor's clock and its readings' times aren't exact
        private const val EARLIER_S = 1L
        private const val LATER_S = 8L
        private const val MIN_OFFSET_S = -30L      // the sensor's clock may run ahead of the phone's
        private const val MAX_OFFSET_S = 600L
    }
}

/** The newer of two readings: what is shown never goes back to an older value. */
fun newerOf(a: Reading?, b: Reading?): Reading? = when {
    a == null -> b
    b == null || !a.time.isBefore(b.time) -> a
    else -> b
}

/**
 * What the screens show after a sync: LibreLinkUp's readings, and those an app on the phone brought after its newest,
 * which LibreLinkUp gets a minute or two later. Without them the value went back to an older one on every sync, until
 * the next reading from the phone. With nothing from LibreLinkUp the readings of the last [keep] stay.
 */
fun shownAfterSync(cloud: List<Reading>, shown: List<Reading>, now: Instant, keep: Duration = SyncState.KEEP): List<Reading> {
    val newest = cloud.lastOrNull()?.time
    val horizon = now.minus(keep)
    val ahead = shown.filter { it.time.isAfter(horizon) && (newest == null || it.time.isAfter(newest)) }
    return if (ahead.isEmpty()) cloud else onePerMinute(cloud + ahead)
}

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
