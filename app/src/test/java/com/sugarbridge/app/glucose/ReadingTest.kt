package com.sugarbridge.app.glucose

import com.sugarbridge.app.libre.parseReading
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ReadingTest {

    private fun item(stamp: String, mgDl: Int, trend: Int? = null): JSONObject {
        val o = JSONObject().put("FactoryTimestamp", stamp).put("ValueInMgPerDl", mgDl)
        if (trend != null) o.put("TrendArrow", trend)
        return o
    }

    @Test
    fun mmolIsRoundedToOneDecimal() {
        assertEquals(6.4, parseReading(item("10/8/2026 6:01:00 PM", 115)).mmol, 0.0)
        assertEquals(10.0, parseReading(item("10/8/2026 6:01:00 PM", 180)).mmol, 0.0)
    }

    // --- text for messages and the notification ---

    @Test
    fun textShowsValueUnitAndArrowWithLocalDecimalSeparator() {
        val r = parseReading(item("10/8/2026 6:00:00 PM", 115, trend = 4))
        assertEquals("6,4 ммоль/л ↗", r.text("ммоль/л", GlucoseUnit.MMOL, Locale.forLanguageTag("ru")))
        assertEquals("6.4 mmol/L ↗", r.text("mmol/L", GlucoseUnit.MMOL, Locale.forLanguageTag("en")))
    }

    @Test
    fun textWithoutTrendHasNoExtraSpaces() {
        val r = parseReading(item("10/8/2026 6:00:00 PM", 115))
        assertEquals("6,4 mmol/L", r.text("mmol/L", GlucoseUnit.MMOL, Locale.forLanguageTag("de")))
    }

    // --- units and the target range ---

    @Test
    fun glucoseIsFormattedInChosenUnit() {
        val ru = Locale.forLanguageTag("ru")
        assertEquals("6,4", formatGlucose(115.0, GlucoseUnit.MMOL, ru))
        assertEquals("115", formatGlucose(115.3, GlucoseUnit.MGDL, ru))
        assertEquals("10,0", formatGlucose(180.0, GlucoseUnit.MMOL, ru))
    }

    @Test
    fun textInMgDl() {
        val r = parseReading(item("10/8/2026 6:00:00 PM", 115, trend = 4))
        assertEquals("115 mg/dL ↗", r.text("mg/dL", GlucoseUnit.MGDL, Locale.forLanguageTag("fr")))
    }

    @Test
    fun rangeBoundsBelongToTarget() {
        assertEquals(GlucoseLevel.LOW, levelOf(69.9, 70.0, 180.0))
        assertEquals(GlucoseLevel.IN_RANGE, levelOf(70.0, 70.0, 180.0))
        assertEquals(GlucoseLevel.IN_RANGE, levelOf(180.0, 70.0, 180.0))
        assertEquals(GlucoseLevel.HIGH, levelOf(180.1, 70.0, 180.0))
    }

    /** LibreLinkUp's TrendArrow means what LibreLink shows: 1 falling fast … 3 steady … 5 rising fast. */
    @Test
    fun trendArrowsMatchLibreLink() {
        val arrows = (0..6).map { parseReading(item("10/8/2026 6:00:00 PM", 115, trend = it)).arrow }
        assertEquals(listOf("", "↓", "↘", "→", "↗", "↑", ""), arrows)
        // the drawn arrow turns from pointing right: down, down-right, right, up-right, up
        assertEquals(listOf(null, 90f, 45f, 0f, -45f, -90f, null), (0..6).map { trendAngle(it) })
    }
}
