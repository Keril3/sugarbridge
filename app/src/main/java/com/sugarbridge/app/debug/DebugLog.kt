package com.sugarbridge.app.debug

import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One line of the event log. */
class LogEntry(val at: Instant, val kind: String, val text: String)

/**
 * What happened when: every sync with its trigger and result, the minute mode starting and stopping, Doze and
 * the screen. The developer tools show it to find out where a delay comes from. It stays on the phone; a few
 * hundred kilobytes at most, the oldest lines go first.
 */
object DebugLog {
    private const val FILE = "events.log"
    private const val PREFS = "debug"
    private const val MAX_BYTES = 400_000L
    private const val KEEP_LINES = 2_000
    private val lock = Any()

    fun add(context: Context, kind: String, text: String) {
        val line = "${System.currentTimeMillis()}\t$kind\t${text.replace('\n', ' ').replace('\t', ' ')}\n"
        synchronized(lock) {
            runCatching {
                val file = file(context)
                file.appendText(line)
                if (file.length() > MAX_BYTES) file.writeText(file.readLines().takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** Oldest first. */
    fun read(context: Context): List<LogEntry> = synchronized(lock) {
        runCatching { file(context).takeIf { it.exists() }?.readLines().orEmpty() }.getOrDefault(emptyList())
    }.mapNotNull(::parse)

    /**
     * Empties the log. Samsung Health's lines aren't kept in it but read from Android's usage statistics each
     * time, so the moment of clearing is remembered and nothing older is shown again (see [clearedAt]).
     */
    fun clear(context: Context) {
        synchronized(lock) { file(context).delete() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong("cleared_at", System.currentTimeMillis()) }
    }

    /** When the log was last cleared, or the beginning of time. */
    fun clearedAt(context: Context): Instant =
        Instant.ofEpochMilli(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("cleared_at", 0))

    /** The log as plain text for copying or sending, newest first. */
    fun format(entries: List<LogEntry>, zone: ZoneId = ZoneId.systemDefault()): String =
        entries.asReversed().joinToString("\n") { "${it.at.atZone(zone).format(STAMP)}  ${it.kind}  ${it.text}" }

    internal fun parse(line: String): LogEntry? {
        val parts = line.split('\t', limit = 3)
        if (parts.size < 3) return null
        val millis = parts[0].toLongOrNull() ?: return null
        return LogEntry(Instant.ofEpochMilli(millis), parts[1], parts[2])
    }

    private fun file(context: Context) = File(context.filesDir, FILE)

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")
}
