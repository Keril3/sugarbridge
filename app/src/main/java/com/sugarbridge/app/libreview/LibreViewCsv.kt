package com.sugarbridge.app.libreview

import com.sugarbridge.app.glucose.GlucoseUnit
import com.sugarbridge.app.glucose.MGDL_PER_MMOL
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.sync.onePerMinute
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** The result of parsing a LibreView export. */
class CsvImport(
    val readings: List<Reading>,   // by time, at most one reading per minute
    val unit: GlucoseUnit,         // the unit of the file
    val glucoseRows: Int,          // glucose rows in the file
    val skippedRows: Int,          // of them, rows that couldn't be parsed (time or value)
)

/**
 * The "Download glucose data" export from the LibreView site. Usually a row with the name and date,
 * a header row and the data: device, serial number, device timestamp, record type
 * (0 = automatic reading, 1 = scan), glucose, … The header language, the separator, the date format
 * and the unit depend on the account settings, so all of them are detected from the file itself.
 */
object LibreViewCsv {

    /** File bytes → text. LibreView sends UTF-8; a file re-saved by Excel on a Russian Windows is windows-1251. */
    fun decode(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if ('\uFFFD' in utf8) String(bytes, Charset.forName("windows-1251")) else utf8
    }

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): CsvImport {
        val clean = text.removePrefix("\uFEFF")
        val rows = records(clean, detectSeparator(clean))
        val headerIndex = rows.take(15).withIndex().maxByOrNull { headerScore(it.value) }
            ?.takeIf { headerScore(it.value) >= 2 }?.index
        val columns = Columns.from(if (headerIndex != null) rows[headerIndex] else emptyList())
        val body = if (headerIndex != null) rows.drop(headerIndex + 1) else rows

        class Raw(val time: String, val value: String, val scan: Boolean)
        val raw = body.mapNotNull { row ->
            val type = row.getOrNull(columns.type)?.trim()?.toIntOrNull()
            val value = when (type) {
                0 -> row.getOrNull(columns.historic)
                1 -> row.getOrNull(columns.scan)
                null -> row.getOrNull(columns.historic)?.takeIf { it.isNotBlank() } ?: row.getOrNull(columns.scan)
                else -> null   // insulin, food, notes and the like are not glucose
            }
            if (value.isNullOrBlank()) null else Raw(row.getOrNull(columns.time).orEmpty(), value, type == 1)
        }

        val numbers = raw.map { parseNumber(it.value) }
        val unit = columns.unit ?: guessUnit(numbers.filterNotNull())
        val formatter = pickFormatter(raw.map { normalizeTime(it.time) })

        var skipped = 0
        val readings = ArrayList<Reading>(raw.size)
        raw.forEachIndexed { i, r ->
            val number = numbers[i]
            val time = formatter?.let { parseTime(normalizeTime(r.time), it) }
            val mgDl = number?.let { if (unit == GlucoseUnit.MMOL) it * MGDL_PER_MMOL else it }
            if (time == null || mgDl == null || mgDl !in 10.0..600.0) {   // Libre measures 40–500 mg/dL
                skipped++
            } else {
                readings += Reading(time.atZone(zone).toInstant(), Math.round(mgDl * 10) / 10.0, 0, r.scan)
            }
        }
        return CsvImport(onePerMinute(readings), unit, raw.size, skipped)
    }

    /** The separator is whichever of ",", ";" and tab occurs most often outside quotes at the start of the file. */
    internal fun detectSeparator(text: String): Char {
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        var quoted = false
        for (c in text.take(4000)) {
            if (c == '"') quoted = !quoted
            else if (!quoted && c in counts) counts[c] = counts.getValue(c) + 1
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    /** CSV rows with quotes handled: quoted cells may hold separators, "" and line breaks (in notes). */
    internal fun records(text: String, separator: Char): List<List<String>> {
        val out = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"' && i + 1 < text.length && text[i + 1] == '"') {
                    cell.append('"')
                    i++
                } else if (c == '"') {
                    quoted = false
                } else {
                    cell.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    separator -> {
                        row.add(cell.toString())
                        cell.clear()
                    }
                    '\r' -> {}
                    '\n' -> {
                        row.add(cell.toString())
                        cell.clear()
                        out.add(row)
                        row = ArrayList()
                    }
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            out.add(row)
        }
        return out.filter { r -> r.any { it.isNotBlank() } }
    }

    /** "6,4", "6.4", " 115 " → a number; "HI", "LO" and empty → null. */
    internal fun parseNumber(text: String): Double? =
        text.trim().replace("\u00A0", "").replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    /** If the header doesn't name the unit: mmol/L is rarely above 35, mg/dL almost always is. */
    private fun guessUnit(values: List<Double>): GlucoseUnit {
        if (values.isEmpty()) return GlucoseUnit.MMOL
        val median = values.sorted()[values.size / 2]
        return if (median > 35) GlucoseUnit.MGDL else GlucoseUnit.MMOL
    }

    // Word stems rather than whole words, in the languages LibreView exports in: the Russian header for the device
    // timestamp, for example, contains the words for "label" and "time" only in inflected forms
    private val TIME_WORDS = listOf("timestamp", "time", "zeit", "horodat", "tiempo", "hora", "метк", "врем", "час", "時刻", "时间", "시간")
    private val GLUCOSE_WORDS = listOf("glucose", "glukose", "glucosa", "glicemia", "glicose", "glyc", "glukoz", "глюкоз", "グルコース", "葡萄糖", "혈당")
    private val TYPE_WORDS = listOf("record type", "type", "typ", "tipo", "tür", "тип", "種類", "类型", "유형")
    private val DEVICE_WORDS = listOf("device", "serial", "gerät", "appareil", "dispositivo", "cihaz", "устройств", "серийн", "пристр", "デバイス", "设备", "장치")

    /** How much a row looks like the table header. The row with the export name and date scores lower. */
    private fun headerScore(row: List<String>): Int {
        if (row.size < 4) return 0
        val cells = row.map { it.lowercase(Locale.ROOT) }
        return listOf(TIME_WORDS, GLUCOSE_WORDS, TYPE_WORDS, DEVICE_WORDS).count { words ->
            cells.any { cell -> words.any { it in cell } }
        }
    }

    private class Columns(val time: Int, val type: Int, val historic: Int, val scan: Int, val unit: GlucoseUnit?) {
        companion object {
            fun from(header: List<String>): Columns {
                val cells = header.map { it.lowercase(Locale.ROOT).trim() }
                fun find(fallback: Int, vararg words: String) =
                    cells.indexOfFirst { cell -> words.any { it in cell } }.takeIf { it >= 0 } ?: fallback
                val time = find(2, *TIME_WORDS.toTypedArray())
                val type = find(3, *TYPE_WORDS.toTypedArray())
                val historic = find(4, "histor", "histór", "verlauf", "storico", "geçmiş", "истор", "хронолог", "автомат", "履歴", "历史", "기록")
                val scan = find(5, "scan", "tarama", "скан", "スキャン", "扫描", "스캔")
                val unitText = listOfNotNull(cells.getOrNull(historic), cells.getOrNull(scan)).joinToString(" ")
                val unit = when {
                    "mg/dl" in unitText || "мг/дл" in unitText -> GlucoseUnit.MGDL
                    "mmol" in unitText || "ммоль" in unitText -> GlucoseUnit.MMOL
                    else -> null
                }
                return Columns(time, type, historic, scan, unit)
            }
        }
    }

    // The order matters: with ambiguous dates (all days ≤ 12) the earlier format wins.
    private val TWENTY_FOUR_HOUR = listOf(
        "d-M-uuuu H:mm[:ss]", "d.M.uuuu H:mm[:ss]", "d/M/uuuu H:mm[:ss]",
        "uuuu-M-d H:mm[:ss]", "uuuu-M-d'T'H:mm[:ss]", "uuuu/M/d H:mm[:ss]", "uuuu.M.d H:mm[:ss]",
        "M-d-uuuu H:mm[:ss]", "M/d/uuuu H:mm[:ss]",
    ).map(::formatter)

    private val TWELVE_HOUR = listOf(
        "M-d-uuuu h:mm[:ss] a", "M/d/uuuu h:mm[:ss] a",
        "d-M-uuuu h:mm[:ss] a", "d/M/uuuu h:mm[:ss] a", "d.M.uuuu h:mm[:ss] a", "uuuu-M-d h:mm[:ss] a",
    ).map(::formatter)

    private fun formatter(pattern: String): DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern(pattern)
        .toFormatter(Locale.US)
        .withResolverStyle(ResolverStyle.STRICT)

    private fun normalizeTime(text: String) = text.trim().replace('\u00A0', ' ').replace(Regex("\\s+"), " ")

    /** The format that fits the most rows (checked on a sample of up to 400 rows). */
    private fun pickFormatter(times: List<String>): DateTimeFormatter? {
        val nonEmpty = times.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return null
        val sample = if (nonEmpty.size <= 400) nonEmpty else List(400) { nonEmpty[it * nonEmpty.size / 400] }
        val twelveHour = sample.any { it.endsWith("M", ignoreCase = true) }
        val candidates = if (twelveHour) TWELVE_HOUR else TWENTY_FOUR_HOUR
        return candidates.withIndex()
            .maxWithOrNull(compareBy<IndexedValue<DateTimeFormatter>> { c -> sample.count { parseTime(it, c.value) != null } }
                .thenByDescending { it.index })
            ?.takeIf { c -> sample.any { parseTime(it, c.value) != null } }
            ?.value
    }

    private fun parseTime(text: String, formatter: DateTimeFormatter): LocalDateTime? = try {
        LocalDateTime.parse(text, formatter)
    } catch (e: DateTimeParseException) {
        null
    }
}
