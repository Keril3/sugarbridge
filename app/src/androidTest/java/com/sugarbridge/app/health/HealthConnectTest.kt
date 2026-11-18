package com.sugarbridge.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sugarbridge.app.glucose.GlucoseBins
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.sync.minuteKey
import com.sugarbridge.app.sync.recordIdFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking

/** The real Health Connect on a phone or emulator: writing, reading our own records, rewriting and deleting. */
@RunWith(AndroidJUnit4::class)
class HealthConnectTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    // a few days back: every Health Connect lets an app read its own records of the last 30 days, older ones see below
    private val base = Instant.now().minus(Duration.ofDays(5)).truncatedTo(ChronoUnit.HOURS)
    private val from = base.minusSeconds(3600)
    private val to = base.plusSeconds(3 * 3600)

    @Before
    fun setUp() {
        assumeTrue("Health Connect is not available", healthConnectState(context) == HealthConnectState.AVAILABLE)
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, WRITE_GLUCOSE)
        runBlocking { deleteOwnGlucose(context, from, to) }
    }

    @After
    fun tearDown() = runBlocking { deleteOwnGlucose(context, from, to) }

    /**
     * Our records from [from] up to, not including, [to], as SugarBridge counts a period. Health Connect counts the
     * end in, and a reading another test left at that very minute isn't one of these.
     */
    private suspend fun ownRecords(): List<BloodGlucoseRecord> = HealthConnectClient.getOrCreate(context).readRecords(
        ReadRecordsRequest(BloodGlucoseRecord::class, TimeRangeFilter.between(from, to), setOf(DataOrigin(context.packageName)))
    ).records.filter { it.time.isBefore(to) }

    private suspend fun countOwn(): Int = ownRecords().size

    @Test
    fun permissionIsGranted() = runBlocking {
        assertTrue(hasGlucosePermission(context))
    }

    @Test
    fun ownRecordsAreReadableWithoutReadPermission() = runBlocking {
        // SugarBridge has no read permission: our own records are visible anyway
        val readings = (0 until 4).map { Reading(base.plusSeconds(it * 900L + 17), 100.0 + it, 0) }
        insertReadings(context, readings)
        assertEquals(readings.map { minuteKey(it.time) }.toSet(), ownMinuteKeys(context, from, to))
    }

    @Test
    fun ownRecordsOlderThan30DaysWhereHealthConnectReadsThem() = runBlocking {
        val old = Instant.parse("2025-03-01T10:00:00Z")
        val readings = (0 until 4).map { Reading(old.plusSeconds(it * 900L + 17), 100.0 + it, 0) }
        try {
            insertReadings(context, readings)
            val keys = ownMinuteKeys(context, old.minusSeconds(3600), old.plusSeconds(3 * 3600))
            // Health Connect without its updates (the Android 14 emulator) gives an app's own records of the last
            // 30 days only, and recent ones for an older period, which ownMinuteKeys leaves out
            assumeTrue("this Health Connect reads an app's own records of the last 30 days only", keys.isNotEmpty())
            assertEquals(readings.map { minuteKey(it.time) }.toSet(), keys)
        } finally {
            deleteOwnGlucose(context, old.minusSeconds(3600), old.plusSeconds(3 * 3600))
        }
    }

    @Test
    fun writingTheSameReadingsAgainMakesNoDuplicates() = runBlocking {
        val readings = (0 until 4).map { Reading(base.plusSeconds(it * 900L + 17), 100.0 + it, 0) }
        insertReadings(context, readings)
        insertReadings(context, readings)
        // the same reading from a LibreView CSV: the same minute, but without seconds
        insertReadings(context, readings.map { it.copy(time = Instant.ofEpochSecond(minuteKey(it.time) * 60)) })
        assertEquals(4, countOwn())
    }

    @Test
    fun deleteRemovesOnlyChosenPeriod() = runBlocking {
        val readings = (0 until 8).map { Reading(base.plusSeconds(it * 900L), 120.0, 0) }   // 10:00 … 11:45
        insertReadings(context, readings)
        deleteOwnGlucose(context, base, base.plusSeconds(3600))   // 10:00 … 11:00
        assertEquals(4, countOwn())
    }

    @Test
    fun recordsCarryDeviceAndMinuteKey() = runBlocking {
        val reading = Reading(base.plusSeconds(28), 115.3, 4)
        insertReadings(context, listOf(reading))
        val record = ownRecords().single()
        assertEquals(recordIdFor(reading.time), record.metadata.clientRecordId)
        assertEquals("Abbott", record.metadata.device?.manufacturer)
        assertEquals(115.3, record.level.inMilligramsPerDeciliter, 0.01)
        assertEquals(BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID, record.specimenSource)
    }

    @Test
    fun gmiIsCalculatedFromOwnRecords() = runBlocking {
        // two weeks of points every 15 minutes at 150 mg/dL, ending before the other tests' hours
        val end = base.minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS)
        val start = end.minus(Duration.ofDays(14))
        try {
            deleteOwnGlucose(context, start, end)   // whatever earlier runs left in these days
            insertReadings(context, (0 until 14 * 96).map { Reading(start.plusSeconds(it * 900L), 150.0, 0) })
            val bins = GlucoseBins(start, end)
            forEachOwnGlucose(context, start, end) { time, mgDl -> bins.add(time, mgDl) }
            val gmi = bins.result()!!
            assertEquals(1.0, gmi.coverage, 1e-9)
            assertEquals(150.0, gmi.meanMgDl, 0.01)
            assertEquals(6.9, gmi.percent, 0.05)
        } finally {
            deleteOwnGlucose(context, start, end)
        }
    }
}
