package com.sugarbridge.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** The XP bar shows whole blocks only, like Windows XP. */
class XpMeterTest {

    @Test
    fun onlyWholeBlocksFitTheTrough() {
        // 2 + 10 × (7 + 2) − 2 + 2 = 92: ten blocks with the gaps between them and the inset on both sides
        assertEquals(10, meterCapacity(92f, block = 7f, gap = 2f, inset = 2f))
        assertEquals(9, meterCapacity(91f, block = 7f, gap = 2f, inset = 2f))
        assertEquals(0, meterCapacity(10f, block = 7f, gap = 2f, inset = 2f))
    }

    @Test
    fun aBlockAppearsOnlyWhenItIsWhollyEarned() {
        assertEquals(0, meterBlocks(0.09f, 10))
        assertEquals(1, meterBlocks(0.1f, 10))
        assertEquals(9, meterBlocks(0.99f, 10))
        assertEquals(10, meterBlocks(1f, 10))
        assertEquals(10, meterBlocks(1.4f, 10))
        assertEquals(0, meterBlocks(-0.2f, 10))
    }
}
