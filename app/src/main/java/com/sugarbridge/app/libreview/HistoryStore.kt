package com.sugarbridge.app.libreview

import android.content.Context
import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.Reading
import java.io.File
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

    fun encode(h: SavedHistory): String = buildString {
        append(HEADER).append('\t').append(h.unit.name).append('\t').append(h.updatedAt.epochSecond).append('\t')
        append(h.source.replace('\t', ' ').replace('\n', ' ')).append('\n')
        h.readings.forEach { append(it.time.epochSecond).append(',').append(it.mgDl).append(',').append(if (it.scan) 1 else 0).append('\n') }
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
    fun save(context: Context, h: SavedHistory) {
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
        File(context.filesDir, FILE).delete()
        File(context.filesDir, LEGACY_FILE).delete()
    }

    fun exists(context: Context) = File(context.filesDir, FILE).exists() || File(context.filesDir, LEGACY_FILE).exists()

    /** For tests: where version 1.6 kept the raw export. */
    internal fun legacyFile(context: Context) = File(context.filesDir, LEGACY_FILE)
}
