package com.sugarbridge.app.glucose

import android.content.Context
import androidx.core.content.edit
import java.time.Duration
import java.time.Instant

/**
 * The estimated HbA1c, kept between starts. It takes all of SugarBridge's records for 90 days from Health Connect,
 * over a hundred thousand with minute mode, and barely changes in hours: it is counted again only when old enough.
 */
class GmiCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("gmi", Context.MODE_PRIVATE)

    class Saved(val days14: GmiResult?, val days90: GmiResult?, val at: Instant, val lowMgDl: Double, val highMgDl: Double) {
        fun isFresh(now: Instant, low: Double, high: Double) =
            days90 != null && low == lowMgDl && high == highMgDl && Duration.between(at, now) < MAX_AGE
    }

    fun load(): Saved? {
        val at = prefs.getLong("at", 0).takeIf { it > 0 } ?: return null
        return Saved(
            decodeGmi(prefs.getString("days14", null)),
            decodeGmi(prefs.getString("days90", null)),
            Instant.ofEpochMilli(at),
            prefs.getFloat("low", 0f).toDouble(),
            prefs.getFloat("high", 0f).toDouble(),
        )
    }

    fun save(days14: GmiResult?, days90: GmiResult?, at: Instant, lowMgDl: Double, highMgDl: Double) = prefs.edit {
        putLong("at", at.toEpochMilli())
        putFloat("low", lowMgDl.toFloat())
        putFloat("high", highMgDl.toFloat())
        putString("days14", encodeGmi(days14))
        putString("days90", encodeGmi(days90))
    }

    companion object {
        val MAX_AGE: Duration = Duration.ofHours(6)
    }
}

/** "days:mean:coverage:inRange", or null for no result. */
fun encodeGmi(g: GmiResult?): String? = g?.let { "${it.days}:${it.meanMgDl}:${it.coverage}:${it.inRange}" }

fun decodeGmi(text: String?): GmiResult? {
    val p = text?.split(':')?.takeIf { it.size == 4 } ?: return null
    return GmiResult(
        days = p[0].toIntOrNull() ?: return null,
        meanMgDl = p[1].toDoubleOrNull() ?: return null,
        coverage = p[2].toDoubleOrNull() ?: return null,
        inRange = p[3].toDoubleOrNull() ?: return null,
    )
}
