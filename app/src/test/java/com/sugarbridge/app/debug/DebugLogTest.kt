package com.sugarbridge.app.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class DebugLogTest {

    @Test
    fun linesAreReadBack() {
        val entry = DebugLog.parse("1791817200000\tsync\tminute, 61 s after the previous: newest 18:05:00, 64 s old")!!
        assertEquals(Instant.ofEpochMilli(1791817200000), entry.at)
        assertEquals("sync", entry.kind)
        assertEquals("minute, 61 s after the previous: newest 18:05:00, 64 s old", entry.text)
    }

    @Test
    fun brokenLinesAreSkipped() {
        assertNull(DebugLog.parse(""))
        assertNull(DebugLog.parse("not a time\tsync\ttext"))
        assertNull(DebugLog.parse("1791817200000\tsync"))
    }

    @Test
    fun theReportHasTheNewestFirst() {
        val a = LogEntry(Instant.parse("2026-10-11T10:00:00Z"), "sync", "first")
        val b = LogEntry(Instant.parse("2026-10-11T10:01:00Z"), "error", "second")
        assertEquals("11.10 10:01:00  error  second\n11.10 10:00:00  sync  first", DebugLog.format(listOf(a, b), ZoneOffset.UTC))
    }
}
