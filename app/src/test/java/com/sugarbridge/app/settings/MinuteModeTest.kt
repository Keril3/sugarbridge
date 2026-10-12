package com.sugarbridge.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinuteModeTest {

    @Test
    fun minuteModeSwitchesBackgroundSyncOffAndTellsAboutIt() {
        val on = AppSettings(backgroundMinutes = 30).minuteModeOn()
        assertTrue(on.liveEnabled)
        assertEquals(0, on.backgroundMinutes)
        assertEquals(30, on.backgroundBeforeLive)
        assertTrue(on.backgroundNotice)
    }

    @Test
    fun stoppingMinuteModeGivesBackgroundSyncBack() {
        val off = AppSettings(backgroundMinutes = 30).minuteModeOn().copy(backgroundNotice = false).minuteModeOff()
        assertFalse(off.liveEnabled)
        assertEquals(30, off.backgroundMinutes)
        assertEquals(0, off.backgroundBeforeLive)
        assertFalse(off.backgroundNotice)
    }

    @Test
    fun aChoiceMadeDuringMinuteModeStays() {
        // background sync turned on again by hand while minute mode runs: stopping minute mode keeps that choice
        val off = AppSettings(backgroundMinutes = 15).minuteModeOn().copy(backgroundMinutes = 60).minuteModeOff()
        assertEquals(60, off.backgroundMinutes)
    }

    @Test
    fun withoutBackgroundSyncThereIsNothingToSay() {
        val on = AppSettings(backgroundMinutes = 0).minuteModeOn()
        assertTrue(on.liveEnabled)
        assertFalse(on.backgroundNotice)
        assertEquals(0, on.minuteModeOff().backgroundMinutes)
    }

    @Test
    fun switchingTwiceChangesNothing() {
        val on = AppSettings(backgroundMinutes = 15).minuteModeOn()
        assertEquals(on, on.minuteModeOn())
        val off = on.minuteModeOff()
        assertEquals(off, off.minuteModeOff())
    }
}
