package com.sugarbridge.app.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import java.time.Instant
import kotlinx.coroutines.runBlocking

/** Настоящий Health Connect на телефоне или эмуляторе: запись, чтение своих записей, повтор и удаление. */
@RunWith(AndroidJUnit4::class)
class HealthConnectTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val base = Instant.parse("2025-03-01T10:00:00Z")   // больше 30 дней назад — проверим и это
    private val from = base.minusSeconds(3600)
    private val to = base.plusSeconds(3 * 3600)

    @Before
    fun setUp() {
        assumeTrue("Health Connect недоступен", healthConnectState(context) == HealthConnectState.AVAILABLE)
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
        // у SugarBridge нет разрешения на чтение, и данные старше 30 дней — свои записи всё равно видны
        val readings = (0 until 4).map { Reading(base.plusSeconds(it * 900L + 17), 100.0 + it, 0) }
        insertReadings(context, readings)
        assertEquals(readings.map { minuteKey(it.time) }.toSet(), ownMinuteKeys(context, from, to))
    }

    @Test
    fun writingTheSameReadingsAgainMakesNoDuplicates() = runBlocking {
        val readings = (0 until 4).map { Reading(base.plusSeconds(it * 900L + 17), 100.0 + it, 0) }
        insertReadings(context, readings)
        insertReadings(context, readings)
        // тот же замер из CSV LibreView: та же минута, но без секунд
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
}
