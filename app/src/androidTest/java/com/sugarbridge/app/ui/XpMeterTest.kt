package com.sugarbridge.app.ui

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.ui.components.METER_CATCH_UP_MS
import com.sugarbridge.app.ui.components.XpMeter
import com.sugarbridge.app.ui.theme.SugarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The XP bar shows the real progress: the first value at once, and it is never far behind, up to a full bar. */
@RunWith(AndroidJUnit4::class)
class XpMeterTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shown() = compose.onNodeWithTag("meter").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    @Test
    fun theBarFollowsTheRealProgressToTheEnd() {
        var fraction by mutableFloatStateOf(0.3f)
        compose.setContent {
            SugarTheme(AppSettings()) { XpMeter(fraction, Modifier.width(300.dp).testTag("meter")) }
        }
        compose.waitForIdle()
        assertEquals(0.3f, shown(), 0.03f)   // the first value at once, not grown from nothing

        compose.mainClock.autoAdvance = false
        fraction = 1f   // the page has loaded
        compose.mainClock.advanceTimeByFrame()
        assertTrue("the new blocks come one after another", shown() < 1f)
        compose.mainClock.advanceTimeBy(METER_CATCH_UP_MS + 100)
        assertEquals(1f, shown(), 0f)   // a full bar, no block missing

        fraction = 0.1f   // a new page starts: shown at once
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0.1f, shown(), 0.03f)
    }
}
