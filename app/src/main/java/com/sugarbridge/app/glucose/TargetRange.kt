package com.sugarbridge.app.glucose

// What a target range may be: Libre measures 40–500 mg/dL, and a range narrower than 0.5 mmol/L makes no sense
const val TARGET_MIN_MGDL = 40.0
const val TARGET_MAX_MGDL = 400.0
const val TARGET_MIN_WIDTH_MGDL = 9.0

/** A typed glucose value ("3,9", "3.9", " 70 ") in the given unit, as mg/dL; null if it isn't a number. */
fun parseGlucose(text: String, unit: GlucoseUnit): Double? {
    val number = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (number.isNaN() || number.isInfinite()) return null
    return when (unit) {
        GlucoseUnit.MMOL -> number * MGDL_PER_MMOL
        GlucoseUnit.MGDL -> number
    }
}

/** true when both bounds are set, the lower one comes first and both lie within what makes sense. */
fun isValidTarget(lowMgDl: Double?, highMgDl: Double?): Boolean =
    lowMgDl != null && highMgDl != null &&
        lowMgDl >= TARGET_MIN_MGDL - 0.5 && highMgDl <= TARGET_MAX_MGDL + 0.5 &&
        highMgDl - lowMgDl >= TARGET_MIN_WIDTH_MGDL - 0.5
