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
import kotlinx.coroutines.runBlocking

/** The real Health Connect on a phone or emulator: writing, reading our own records, rewriting and deleting. */
@RunWith(AndroidJUnit4::class)
class HealthConnectTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val base = Instant.parse("2025-03-01T10:00:00Z")   // more than 30 days ago: checks that too
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

    private suspend fun countOwn(): Int = HealthConnectClient.getOrCreate(context).readRecords(
        ReadRecordsRequest(BloodGlucoseRecord::class, TimeRangeFilter.between(from, to), setOf(DataOrigin(context.packageName)))
    ).records.size

    @Test
    fun permissionIsGranted() = runBlocking {
        assertTrue(hasGlucosePermission(context))
    }

    @Test
    fun ownRecordsAreReadableWithoutReadPermission() = runBlocking {
        // SugarBridge has no read permission and the data is older than 30 days: our own records are visible anyway
        val readings = (0 until 4).map { Reading(base.plusSeconds(it * 900L + 17), 100.0 + it, 0) }
        insertReadings(context, readings)
        assertEquals(readings.map { minuteKey(it.time) }.toSet(), ownMinuteKeys(context, from, to))
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
        val record = HealthConnectClient.getOrCreate(context).readRecords(
            ReadRecordsRequest(BloodGlucoseRecord::class, TimeRangeFilter.between(from, to), setOf(DataOrigin(context.packageName)))
        ).records.single()
        assertEquals(recordIdFor(reading.time), record.metadata.clientRecordId)
        assertEquals("Abbott", record.metadata.device?.manufacturer)
        assertEquals(115.3, record.level.inMilligramsPerDeciliter, 0.01)
        assertEquals(BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID, record.specimenSource)
    }

    @Test
    fun gmiIsCalculatedFromOwnRecords() = runBlocking {
        // two weeks of points every 15 minutes at 150 mg/dL, far in the past so other tests are not affected
        val start = Instant.parse("2025-01-01T00:00:00Z")
        val end = start.plus(Duration.ofDays(14))
        try {
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
