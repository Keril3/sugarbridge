package com.sugarbridge.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The XP bar shows whole blocks only, like Windows XP: 100% is a full bar. */
class XpMeterTest {

    @Test
    fun onlyWholeBlocksFitTheTrough() {
        // 2 + 10 × (7 + 2) − 2 + 2 = 92: ten blocks with the gaps between them and the inset on both sides
        assertEquals(10, meterCapacity(92f, block = 7f, gap = 2f, inset = 2f))
        assertEquals(9, meterCapacity(91f, block = 7f, gap = 2f, inset = 2f))
        assertEquals(0, meterCapacity(10f, block = 7f, gap = 2f, inset = 2f))
    }

    /** The blocks fill the trough from end to end: no strip of empty trough is left after the last one. */
    @Test
    fun theBlocksFillTheWholeBar() {
        for (width in listOf(92f, 95.5f, 140f, 333f)) {
            val capacity = meterCapacity(width, block = 7f, gap = 2f, inset = 2f)
            val block = meterBlockWidth(width, capacity, gap = 2f, inset = 2f)
            assertEquals(width, 2 * 2f + capacity * block + (capacity - 1) * 2f, 0.001f)
            assertTrue("$width: $block", block >= 7f && block < 7f + 9f / capacity + 0.001f)   // only a little wider
        }
        assertEquals(0f, meterBlockWidth(5f, 0, gap = 2f, inset = 2f), 0f)
    }

    @Test
    fun theNearestNumberOfBlocksIsShown() {
        assertEquals(0, meterBlocks(0.04f, 10))
        assertEquals(1, meterBlocks(0.1f, 10))
        assertEquals(9, meterBlocks(0.93f, 10))
        assertEquals(10, meterBlocks(0.99f, 10))   // 99% isn't shown as a bar with a gap at the end
        assertEquals(10, meterBlocks(1f, 10))
        assertEquals(10, meterBlocks(1.4f, 10))
        assertEquals(0, meterBlocks(-0.2f, 10))
    }
}
