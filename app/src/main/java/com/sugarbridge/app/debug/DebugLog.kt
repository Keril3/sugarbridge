package com.sugarbridge.app.debug

import android.content.Context
import androidx.core.content.edit
import com.sugarbridge.app.settings.SettingsStore
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One line of the event log. */
class LogEntry(val at: Instant, val kind: String, val text: String)

/**
 * What happened when: every sync with its trigger and result, the minute mode starting and stopping, Doze and
 * the screen. The developer tools show it to find out where a delay comes from. It stays on the phone, for as
 * many days as the developer tools keep it (see AppSettings.logDays), and never more than a few hundred
 * kilobytes.
 */
object DebugLog {
    private const val FILE = "events.log"
    private const val PREFS = "debug"
    private const val MAX_BYTES = 400_000L
    private const val KEEP_LINES = 2_000
    private const val DAY_MS = 24 * 3_600_000L
    private const val PRUNE_EVERY_MS = 3_600_000L
    private val lock = Any()
    @Volatile private var prunedAt = 0L

    fun add(context: Context, kind: String, text: String) {
        val line = "${System.currentTimeMillis()}\t$kind\t${text.replace('\n', ' ').replace('\t', ' ')}\n"
        synchronized(lock) {
            runCatching {
                val file = file(context)
                file.appendText(line)
                if (file.length() > MAX_BYTES) file.writeText(file.readLines().takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
            }
        }
        // lines older than the days kept go, checked at most once an hour
        val now = System.currentTimeMillis()
        if (now - prunedAt > PRUNE_EVERY_MS) {
            prunedAt = now
            prune(context)
        }
    }

    /** Drops the lines older than [days], by default the days the developer tools keep. */
    fun prune(context: Context, days: Int = SettingsStore(context).load().logDays) {
        synchronized(lock) {
            runCatching {
                val file = file(context)
                if (!file.exists()) return
                val lines = file.readLines()
                val kept = keepSince(lines, System.currentTimeMillis() - days * DAY_MS)
                if (kept.size != lines.size) file.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = "\n"))
            }
        }
    }

    /** The lines written at [cutoff] (epoch milliseconds) or later; a line that can't be read goes too. */
    internal fun keepSince(lines: List<String>, cutoff: Long): List<String> =
        lines.filter { line -> line.substringBefore('\t').toLongOrNull()?.let { it >= cutoff } ?: false }

    /** Where the kept period starts, for the lines read from elsewhere (Samsung Health's). */
    fun keptSince(context: Context): Instant =
        maxOf(Instant.now().minusMillis(SettingsStore(context).load().logDays * DAY_MS), clearedAt(context))

    /** An entry's text for reading: each part after "; " on a line of its own. */
    fun readable(text: String): String = text.split("; ").joinToString("\n")

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
