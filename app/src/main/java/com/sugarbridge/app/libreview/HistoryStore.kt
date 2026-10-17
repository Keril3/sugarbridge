package com.sugarbridge.app.libreview

import android.content.Context
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.Reading
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneId

/** The imported history SugarBridge keeps: every reading from the LibreView exports, merged, until it is deleted. */
class SavedHistory(
    val readings: List<Reading>,   // sorted by time
    val unit: GlucoseUnit,         // the unit of the last export, shown with the history
    val source: String,            // where the last export came from: "LibreView" or a file name
    val updatedAt: Instant,
)

/**
 * The history in a file of its own: a header line, then "epochSecond,mgdl,scan" per reading. Since an update
 * can merge several exports, the file is no longer one of them. Version 1.6 kept the raw export instead; such a
 * file is read once and rewritten in this format.
 */
object HistoryStore {
    private const val FILE = "history.txt"
    private const val LEGACY_FILE = "history.csv"
    private const val LEGACY_PREFS = "history"
    private const val HEADER = "sugarbridge-history 1"

    /** Readings added by syncs are this far apart at least: one every 15 minutes like LibreView's, a minute off allowed. */
    internal const val APPEND_GAP_SECONDS = 14 * 60L

    /** Every change of the file goes through it: a sync may add readings while an export is being saved. */
    private val lock = Any()

    fun encode(h: SavedHistory): String = buildString {
        append(HEADER).append('\t').append(h.unit.name).append('\t').append(h.updatedAt.epochSecond).append('\t')
        append(h.source.replace('\t', ' ').replace('\n', ' ')).append('\n')
        h.readings.forEach { append(line(it)) }
    }

    private fun line(r: Reading) = "${r.time.epochSecond},${r.mgDl},${if (r.scan) 1 else 0}\n"

    /**
     * The readings to add after [last] (epoch seconds): automatic ones only, newer and at least
     * [APPEND_GAP_SECONDS] apart, the way LibreView keeps them, not every minute of minute mode.
     */
    internal fun thin(last: Long, readings: List<Reading>): List<Reading> {
        var kept = last
        return readings.filter { !it.scan }.sortedBy { it.time }.filter { r ->
            (r.time.epochSecond >= kept + APPEND_GAP_SECONDS).also { if (it) kept = r.time.epochSecond }
        }
    }

    /**
     * Adds the new readings to the kept history (see [thin]); without a history they begin one, in [unit] and
     * from [source]. Returns how many were added. Only the end of the file is read and written, so a sync every
     * minute costs next to nothing. A file whose end can't be read is left as it is.
     */
    fun append(context: Context, readings: List<Reading>, unit: GlucoseUnit, source: String): Int = synchronized(lock) {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) {
            // version 1.6's file is turned into the history when the app starts; it must not be replaced here
            if (File(context.filesDir, LEGACY_FILE).exists()) return 0
            val first = thin(Long.MIN_VALUE / 2, readings)
            if (first.isNotEmpty()) write(context, SavedHistory(first, unit, source, Instant.now()))
            return first.size
        }
        val last = lastReading(file) ?: return 0
        val added = thin(last, readings)
        if (added.isNotEmpty()) file.appendText(added.joinToString("") { line(it) })
        added.size
    }

    /** The epoch second of the file's last reading, from its last line; null for a file without readings. */
    private fun lastReading(file: File): Long? = RandomAccessFile(file, "r").use { f ->
        val size = f.length()
        val tail = ByteArray(minOf(size, 256L).toInt())
        f.seek(size - tail.size)
        f.readFully(tail)
        String(tail).trimEnd().substringAfterLast('\n').split(',').takeIf { it.size == 3 }?.get(0)?.toLongOrNull()
    }

    /** null for anything that isn't a history written by [encode]. */
    fun decode(text: String): SavedHistory? {
        val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) return null
        val head = lines.next().split('\t')
        if (head.size < 4 || head[0] != HEADER) return null
        val unit = GlucoseUnit.entries.firstOrNull { it.name == head[1] } ?: return null
        val updatedAt = head[2].toLongOrNull()?.let(Instant::ofEpochSecond) ?: return null
        val readings = mutableListOf<Reading>()
        for (line in lines) {
            val p = line.split(',')
            if (p.size != 3) return null
            val time = p[0].toLongOrNull() ?: return null
            val mgDl = p[1].toDoubleOrNull() ?: return null
            readings += Reading(Instant.ofEpochSecond(time), mgDl, 0, scan = p[2] == "1")
        }
        return SavedHistory(readings.sortedBy { it.time }, unit, head[3], updatedAt)
    }

    /** Written next to the old file and then renamed, so a crash never leaves half a history. */
    fun save(context: Context, h: SavedHistory) = synchronized(lock) { write(context, h) }

    private fun write(context: Context, h: SavedHistory) {
        val file = File(context.filesDir, FILE)
        val temp = File(context.filesDir, "$FILE.tmp")
        temp.writeText(encode(h))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        File(context.filesDir, LEGACY_FILE).delete()
    }

    /** The kept history, or null. A file that can't be read is deleted, so it doesn't fail on every start. */
    fun load(context: Context, zone: ZoneId): SavedHistory? {
        val file = File(context.filesDir, FILE)
        if (file.exists()) {
            val h = runCatching { decode(file.readText()) }.getOrNull()
            if (h == null || h.readings.isEmpty()) file.delete()
            return h?.takeIf { it.readings.isNotEmpty() }
        }
        val legacy = File(context.filesDir, LEGACY_FILE)
        if (!legacy.exists()) return null
        val parsed = runCatching { LibreViewCsv.parse(LibreViewCsv.decode(legacy.readBytes()), zone) }.getOrNull()
        val source = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).getString("source", null) ?: "CSV"
        val savedAt = Instant.ofEpochMilli(legacy.lastModified())
        legacy.delete()
        if (parsed == null || parsed.readings.isEmpty()) return null
        return SavedHistory(parsed.readings, parsed.unit, source, savedAt).also { save(context, it) }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            File(context.filesDir, FILE).delete()
            File(context.filesDir, LEGACY_FILE).delete()
        }
    }

    /** When the kept history last changed, to notice readings added by a sync. */
    fun changedAt(context: Context): Long = File(context.filesDir, FILE).lastModified()

    fun exists(context: Context) = File(context.filesDir, FILE).exists() || File(context.filesDir, LEGACY_FILE).exists()

    /** For tests: where version 1.6 kept the raw export. */
    internal fun legacyFile(context: Context) = File(context.filesDir, LEGACY_FILE)
}
