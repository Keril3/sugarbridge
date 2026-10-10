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

/** Итог разбора выгрузки LibreView. */
class CsvImport(
    val readings: List<Reading>,   // по времени, не больше одного замера на минуту
    val unit: GlucoseUnit,         // в каких единицах был файл
    val glucoseRows: Int,          // строк с глюкозой в файле
    val skippedRows: Int,          // из них не удалось разобрать (время или значение)
)

/**
 * Выгрузка «Загрузить данные о глюкозе» с сайта LibreView. Обычно это строка с именем и датой,
 * строка заголовков и данные: устройство, серийный номер, время устройства, тип записи
 * (0 — автоматический замер, 1 — сканирование), уровень глюкозы, … Язык заголовков, разделитель,
 * формат даты и единицы зависят от настроек аккаунта, поэтому всё это определяется по самому файлу.
 */
object LibreViewCsv {

    /** Байты файла → текст. LibreView отдаёт UTF-8; если файл пересохранили в Excel на русской Windows — это windows-1251. */
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
                else -> null   // инсулин, еда, заметки и прочее — не глюкоза
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
            if (time == null || mgDl == null || mgDl !in 10.0..600.0) {   // Libre измеряет 40–500 мг/дл
                skipped++
            } else {
                readings += Reading(time.atZone(zone).toInstant(), Math.round(mgDl * 10) / 10.0, 0, r.scan)
            }
        }
        return CsvImport(onePerMinute(readings), unit, raw.size, skipped)
    }

    /** Разделитель — тот из ",", ";" и табуляции, что чаще встречается вне кавычек в начале файла. */
    internal fun detectSeparator(text: String): Char {
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        var quoted = false
        for (c in text.take(4000)) {
            if (c == '"') quoted = !quoted
            else if (!quoted && c in counts) counts[c] = counts.getValue(c) + 1
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    /** Строки CSV с учётом кавычек: внутри них бывают разделители, "" и переносы строк (в заметках). */
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

    /** "6,4", "6.4", " 115 " → число; "HI", "LO" и пустое → null. */
    internal fun parseNumber(text: String): Double? =
        text.trim().replace("\u00A0", "").replace(" ", "").replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    /** Если единицы не написаны в заголовке: ммоль/л редко бывают больше 35, мг/дл — почти всегда больше. */
    private fun guessUnit(values: List<Double>): GlucoseUnit {
        if (values.isEmpty()) return GlucoseUnit.MMOL
        val median = values.sorted()[values.size / 2]
        return if (median > 35) GlucoseUnit.MGDL else GlucoseUnit.MMOL
    }

    // Корни слов, а не слова целиком: «Отметка времени устройства» не содержит ни «метка», ни «время»
    private val TIME_WORDS = listOf("timestamp", "time", "метк", "врем")
    private val GLUCOSE_WORDS = listOf("glucose", "глюкоз")
    private val TYPE_WORDS = listOf("record type", "type", "тип")
    private val DEVICE_WORDS = listOf("device", "serial", "устройств", "серийн")

    /** Насколько строка похожа на заголовок таблицы. У строки с именем и датой выгрузки балл ниже. */
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
                val historic = find(4, "historic", "истор", "хронолог", "автомат")
                val scan = find(5, "scan", "скан")
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

    // Порядок важен: при неоднозначных датах (все дни ≤ 12) выигрывает формат, стоящий раньше.
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

    /** Формат, который подходит к наибольшему числу строк (проверяем на выборке до 400 строк). */
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
