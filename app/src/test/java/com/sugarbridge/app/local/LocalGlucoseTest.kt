package com.sugarbridge.app.local

import com.sugarbridge.app.glucose.MGDL_PER_MMOL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class LocalGlucoseTest {

    private val now = Instant.parse("2026-10-17T10:00:00Z")

    @Test
    fun aBroadcastReadingIsCheckedBeforeItIsTaken() {
        val r = LocalGlucose.reading(115.0, now.minusSeconds(30).toEpochMilli(), 4, now)!!
        assertEquals(115.0, r.mgDl, 0.0)
        assertEquals(4, r.trend)
        assertNull("not a value", LocalGlucose.reading(Double.NaN, now.toEpochMilli(), 3, now))
        assertNull("too low to be measured", LocalGlucose.reading(5.0, now.toEpochMilli(), 3, now))
        assertNull("no time", LocalGlucose.reading(115.0, 0, 3, now))
        assertNull("from the future", LocalGlucose.reading(115.0, now.plusSeconds(600).toEpochMilli(), 3, now))
        assertNull("a day old", LocalGlucose.reading(115.0, now.minusSeconds(86_400).toEpochMilli(), 3, now))
    }

    @Test
    fun ratesAndNamesBecomeLibresArrows() {
        assertEquals(listOf(1, 2, 3, 3, 4, 5, 0), listOf(-2.5f, -1.2f, -0.5f, 0.9f, 1.5f, 2f, Float.NaN).map(LocalGlucose::trendOfRate))
        assertEquals(listOf(1, 1, 2, 3, 4, 5, 5, 0, 0), listOf("DoubleDown", "SingleDown", "FortyFiveDown", "Flat", "FortyFiveUp", "SingleUp", "DoubleUp", "NONE", null).map(LocalGlucose::trendOfName))
    }

    @Test
    fun aValueWithItsUnitIsFoundInAnyLanguage() {
        assertEquals(6.4 * MGDL_PER_MMOL, LocalGlucose.inTexts(listOf("FreeStyle LibreLink", "6.4 mmol/L ↗"))!!.first, 1e-9)
        assertEquals(4, LocalGlucose.inTexts(listOf("6.4 mmol/L ↗"))!!.second)
        assertEquals(6.4 * MGDL_PER_MMOL, LocalGlucose.inTexts(listOf("Глюкоза: 6,4 ммоль/л"))!!.first, 1e-9)
        assertEquals(115.0, LocalGlucose.inTexts(listOf("115 mg/dL"))!!.first, 0.0)
    }

    @Test
    fun aTextThatIsOnlyANumberCountsByItsForm() {
        assertEquals(6.4 * MGDL_PER_MMOL, LocalGlucose.inTexts(listOf("Sensor", "6,4"))!!.first, 1e-9)
        assertEquals(115.0, LocalGlucose.inTexts(listOf("115 →"))!!.first, 0.0)
        assertEquals(3, LocalGlucose.inTexts(listOf("115 →"))!!.second)
    }

    @Test
    fun notificationsWithoutAReadingGiveNothing() {
        assertNull(LocalGlucose.inTexts(listOf("FreeStyle LibreLink", "Sensor connected")))
        assertNull("numbers in a sentence", LocalGlucose.inTexts(listOf("Sensor ends in 3 days, 14 hours")))
        assertNull("two different values", LocalGlucose.inTexts(listOf("6.4 mmol/L", "8.1 mmol/L")))
        assertNull("out of range", LocalGlucose.inTexts(listOf("900 mg/dL")))
    }
}
