package com.sugarbridge.app.local

import com.sugarbridge.app.glucose.MGDL_PER_MMOL
import com.sugarbridge.app.glucose.Reading
import java.time.Duration
import java.time.Instant

/**
 * Readings from apps on this phone, for when the cloud can't be reached: what Juggluco and xDrip+ send to other
 * apps and what LibreLink shows in its notification. Here they are read and checked; [LocalReadings] writes them.
 */
object LocalGlucose {
    /** What a sensor can measure; anything else is a misread number. */
    private val PLAUSIBLE = 20.0..600.0

    /** A reading may be this much ahead of the phone's clock (the sensor's runs on its own) or this old. */
    private val AHEAD: Duration = Duration.ofMinutes(2)
    private val OLDEST: Duration = Duration.ofHours(12)

    /** Juggluco's "glucodata.Minute" broadcast: mg/dL, the time in milliseconds and the rate in mg/dL a minute. */
    const val JUGGLUCO_ACTION = "glucodata.Minute"
    const val JUGGLUCO_MGDL = "glucodata.Minute.mgdl"
    const val JUGGLUCO_TIME = "glucodata.Minute.Time"
    const val JUGGLUCO_RATE = "glucodata.Minute.Rate"

    /** xDrip+'s local broadcast: mg/dL as a double, the time in milliseconds and the trend's name. */
    const val XDRIP_ACTION = "com.eveningoutpost.dexdrip.BgEstimate"
    const val XDRIP_MGDL = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
    const val XDRIP_TIME = "com.eveningoutpost.dexdrip.Extras.Time"
    const val XDRIP_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"

    /** A reading, or null when the value or the time can't be right. */
    fun reading(mgDl: Double, timeMillis: Long, trend: Int, now: Instant = Instant.now()): Reading? {
        if (mgDl.isNaN() || mgDl !in PLAUSIBLE || timeMillis <= 0) return null
        val time = Instant.ofEpochMilli(timeMillis)
        if (time.isAfter(now.plus(AHEAD)) || time.isBefore(now.minus(OLDEST))) return null
        return Reading(time, Math.round(mgDl * 10) / 10.0, trend)
    }

    /** Libre's trend arrow (1 falling fast … 5 rising fast, 0 none) from a rate in mg/dL a minute, as Libre counts it. */
    fun trendOfRate(mgDlPerMinute: Float): Int = when {
        mgDlPerMinute.isNaN() || mgDlPerMinute.isInfinite() -> 0
        mgDlPerMinute <= -2f -> 1
        mgDlPerMinute <= -1f -> 2
        mgDlPerMinute < 1f -> 3
        mgDlPerMinute < 2f -> 4
        else -> 5
    }

    /** Libre's trend arrow from xDrip+'s (Dexcom's) names: "Flat", "FortyFiveUp", "SingleDown" … */
    fun trendOfName(name: String?): Int = when (name) {
        "DoubleDown", "SingleDown" -> 1
        "FortyFiveDown" -> 2
        "Flat" -> 3
        "FortyFiveUp" -> 4
        "SingleUp", "DoubleUp" -> 5
        else -> 0
    }

    // a number with a unit, in the spellings of LibreLink's languages
    private val WITH_UNIT = Regex(
        """(\d{1,3}(?:[.,]\d)?)\s*(mmol\s*/\s*l|ммоль\s*/\s*л|mmol/litre|mg\s*/\s*dl|мг\s*/\s*дл|毫摩尔/升|毫克/分升)""",
        RegexOption.IGNORE_CASE,
    )
    // a text that is just the value, perhaps with an arrow: "6,4", "115 ↗"
    private val ALONE = Regex("""^\s*(\d{1,3}(?:[.,]\d)?)\s*[→↗↑↘↓⇈⇊⬈⬊➚➘]?\s*$""")
    private val ARROWS = mapOf('↓' to 1, '⇊' to 1, '↘' to 2, '⬊' to 2, '➘' to 2, '→' to 3, '↗' to 4, '⬈' to 4, '➚' to 4, '↑' to 5, '⇈' to 5)

    /**
     * The glucose in mg/dL in a notification's texts, and the arrow if one is there: a number with its unit, or a
     * text that is only a number, where one with a decimal is mmol/L and a whole one mg/dL. Null when there is
     * none or the texts disagree, so a notification that merely mentions numbers is never taken for a reading.
     */
    fun inTexts(texts: List<String>): Pair<Double, Int>? {
        val values = texts.flatMap { text ->
            WITH_UNIT.findAll(text).map { m ->
                val number = m.groupValues[1].replace(',', '.').toDouble()
                if (m.groupValues[2].lowercase().let { it.startsWith("mg") || it.startsWith("мг") || it.startsWith("毫克") }) number
                else number * MGDL_PER_MMOL
            }.toList().ifEmpty {
                ALONE.find(text)?.groupValues?.get(1)?.let { n ->
                    if (n.contains('.') || n.contains(',')) n.replace(',', '.').toDouble() * MGDL_PER_MMOL else n.toDouble()
                }?.let(::listOf).orEmpty()
            }
        }.filter { it in PLAUSIBLE }
        val value = values.distinctBy { Math.round(it) }.singleOrNull() ?: return null
        val trend = texts.joinToString(" ").firstNotNullOfOrNull { ARROWS[it] } ?: 0
        return value to trend
    }
}
