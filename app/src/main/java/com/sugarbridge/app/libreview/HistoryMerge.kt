package com.sugarbridge.app.libreview

import com.sugarbridge.app.glucose.Reading
import java.time.Instant
import kotlin.math.abs

/** What to do with a new export when a history is already loaded. */
enum class MergeMode {
    ADD,         // keep what is loaded, add only readings it doesn't have
    OVERWRITE,   // add the new ones and take the new value where the two differ
    REPLACE,     // forget the loaded history, keep only the new export
}

/**
 * How a new export compares with the loaded history. Readings are matched by their minute and their kind:
 * an automatic reading and a scan in the same minute are two different readings.
 */
class MergePlan(
    val incoming: Int,     // readings in the new export
    val added: Int,        // not in the loaded history
    val same: Int,         // there already, with the same value
    val changed: Int,      // there already, with another value
    val onlyOld: Int,      // in the loaded history but not in the new export
    val from: Instant?,    // the new export's first and last reading
    val to: Instant?,
)

private fun key(r: Reading): Long = r.time.epochSecond / 60 * 2 + if (r.scan) 1 else 0

/** Values closer than this are the same reading written with a different rounding (mmol/L vs mg/dL). */
private const val SAME_MGDL = 1.0

fun planMerge(old: List<Reading>, new: List<Reading>): MergePlan {
    val oldByKey = old.associateBy(::key)
    val newKeys = HashSet<Long>()
    var added = 0
    var same = 0
    var changed = 0
    for (r in new) {
        if (!newKeys.add(key(r))) continue
        val before = oldByKey[key(r)]
        when {
            before == null -> added++
            abs(before.mgDl - r.mgDl) < SAME_MGDL -> same++
            else -> changed++
        }
    }
    return MergePlan(
        incoming = newKeys.size,
        added = added,
        same = same,
        changed = changed,
        onlyOld = oldByKey.keys.count { it !in newKeys },
        from = new.minOfOrNull { it.time },
        to = new.maxOfOrNull { it.time },
    )
}

/** The history after an update, sorted by time, one reading per minute and kind. */
fun merge(old: List<Reading>, new: List<Reading>, mode: MergeMode): List<Reading> {
    val result = LinkedHashMap<Long, Reading>()
    when (mode) {
        MergeMode.REPLACE -> new.forEach { result.putIfAbsent(key(it), it) }
        MergeMode.ADD -> {
            old.forEach { result.putIfAbsent(key(it), it) }
            new.forEach { result.putIfAbsent(key(it), it) }
        }
        MergeMode.OVERWRITE -> {
            old.forEach { result.putIfAbsent(key(it), it) }
            new.forEach { result[key(it)] = it }
        }
    }
    return result.values.sortedBy { it.time }
}
