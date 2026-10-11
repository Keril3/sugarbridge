package com.sugarbridge.app.glucose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetRangeTest {

    @Test
    fun typedValuesAreReadInTheChosenUnit() {
        assertEquals(3.9 * MGDL_PER_MMOL, parseGlucose("3,9", GlucoseUnit.MMOL)!!, 1e-9)   // decimal comma
        assertEquals(3.9 * MGDL_PER_MMOL, parseGlucose(" 3.9 ", GlucoseUnit.MMOL)!!, 1e-9)
        assertEquals(180.0, parseGlucose("180", GlucoseUnit.MGDL)!!, 1e-9)
        assertNull(parseGlucose("", GlucoseUnit.MMOL))
        assertNull(parseGlucose("abc", GlucoseUnit.MGDL))
        assertNull(parseGlucose("NaN", GlucoseUnit.MGDL))
    }

    @Test
    fun theDefaultRangeIsValid() {
        assertTrue(isValidTarget(parseGlucose("3,9", GlucoseUnit.MMOL), parseGlucose("10,0", GlucoseUnit.MMOL)))
        assertTrue(isValidTarget(70.0, 180.0))
    }

    @Test
    fun badRangesAreRefused() {
        assertFalse(isValidTarget(180.0, 70.0))      // the lower one must come first
        assertFalse(isValidTarget(100.0, 104.0))     // narrower than 0.5 mmol/L
        assertFalse(isValidTarget(20.0, 180.0))      // below what a sensor measures
        assertFalse(isValidTarget(70.0, 600.0))
        assertFalse(isValidTarget(null, 180.0))
    }

    @Test
    fun boundsShownInMmolAreAccepted() {
        // 2.2 and 22.2 mmol/L are what the screen shows for 40 and 400 mg/dL
        assertTrue(isValidTarget(parseGlucose("2,2", GlucoseUnit.MMOL), parseGlucose("22,2", GlucoseUnit.MMOL)))
    }
}
