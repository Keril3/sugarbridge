package com.sugarbridge.app.sync

import com.sugarbridge.app.glucose.Reading
import java.time.Duration
import java.time.Instant

/**
 * Номер минуты замера. Один и тот же замер приходит из LibreLinkUp с секундами ("12:07:28"),
 * а из CSV LibreView — без них ("12:07"). По минуте они совпадают, поэтому дублей не будет.
 */
fun minuteKey(time: Instant): Long = Math.floorDiv(time.epochSecond, 60L)

/** Ключ записи в Health Connect: повторная запись с тем же ключом обновляет запись, а не создаёт дубль. */
fun recordIdFor(time: Instant): String = "libre-m${minuteKey(time)}"

/** Не больше одного замера на минуту; при совпадении автоматический замер важнее сканирования. */
fun onePerMinute(readings: List<Reading>): List<Reading> =
    readings.groupBy { minuteKey(it.time) }
        .map { (_, same) -> same.firstOrNull { !it.scan } ?: same.first() }
        .sortedBy { it.time }

/**
 * Что уже записано в Health Connect: минуты за последние ~13 часов и «пол» —
 * замеры не новее него считаются обработанными (так переносится старый last_synced).
 */
data class SyncState(val written: Set<Long> = emptySet(), val floor: Long = 0) {

    /**
     * Замеры, которых ещё нет в Health Connect. Замеры «из будущего» ждут своего времени:
     * Health Connect их не принимает и отбрасывает всю пачку, а время замера идёт от часов датчика,
     * которые могут спешить относительно телефона.
     */
    fun fresh(readings: List<Reading>, now: Instant): List<Reading> =
        onePerMinute(readings).filter {
            it.time.epochSecond > floor && minuteKey(it.time) !in written && !it.time.isAfter(now)
        }

    /** Состояние после записи: добавляем минуты, забываем то, что старше окна истории LibreLinkUp. */
    fun after(newlyWritten: List<Reading>, now: Instant, keep: Duration = KEEP): SyncState {
        val horizon = now.minus(keep).epochSecond
        val keys = (written + newlyWritten.map { minuteKey(it.time) }).filterTo(HashSet()) { it * 60 >= horizon }
        return SyncState(keys, maxOf(floor, horizon))
    }

    /** После удаления записей из Health Connect эти минуты можно записать снова. */
    fun forget(from: Instant, to: Instant): SyncState {
        val a = minuteKey(from)
        val b = minuteKey(to)
        return copy(written = written.filterTo(HashSet()) { it < a || it >= b })
    }

    companion object {
        val KEEP: Duration = Duration.ofHours(13)   // LibreLinkUp отдаёт историю за 12 часов
    }
}

fun encodeKeys(keys: Set<Long>): String = keys.sorted().joinToString(",")

fun decodeKeys(text: String?): Set<Long> =
    text.orEmpty().split(',').mapNotNullTo(HashSet()) { it.trim().toLongOrNull() }

/** Компактная запись замеров в настройки: "epochSecond:мг/дл:тренд;...". */
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
 * Как часто LibreLinkUp на самом деле присылает новое текущее значение: медиана интервалов, мин.
 * Паузы длиннее 20 минут (телефон спал, не было сети) не считаем. null — данных пока мало.
 */
fun typicalUpdateMinutes(seen: List<Instant>): Double? {
    val gaps = seen.distinct().sorted().zipWithNext { a, b -> Duration.between(a, b).seconds / 60.0 }
        .filter { it in 0.25..20.0 }
        .sorted()
    if (gaps.size < 2) return null
    val mid = gaps.size / 2
    return if (gaps.size % 2 == 1) gaps[mid] else (gaps[mid - 1] + gaps[mid]) / 2
}
