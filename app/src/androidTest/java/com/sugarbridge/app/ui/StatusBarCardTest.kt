package com.sugarbridge.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sugarbridge.app.R
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.ui.components.SkyBackground
import com.sugarbridge.app.ui.screens.StatusBarCard
import com.sugarbridge.app.ui.theme.SugarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** The Status bar window: its steps change the settings, the preview shows the icon, and it rests without minute mode. */
@RunWith(AndroidJUnit4::class)
class StatusBarCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun show(start: AppSettings): () -> AppSettings {
        var settings by mutableStateOf(start)
        compose.setContent {
            SugarTheme(settings) {
                SkyBackground {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                        StatusBarCard(settings, Reading(Instant.now(), 115.0, 4)) { change -> settings = change(settings) }
                    }
                }
            }
        }
        return { settings }
    }

    @Test
    fun stepsChangeTheSettingsAndThePreviewFollows() {
        val settings = show(AppSettings(liveEnabled = true, liveShowValue = true))
        compose.onNodeWithTag("statusbar-preview").assertExists()
        shot("statusbar_settings")

        compose.onAllNodesWithText("XL")[1].performScrollTo().performClick()   // the arrow's XL
        compose.waitForIdle()
        assertEquals(3, settings().statusArrowSize)
        compose.onNodeWithText(context.getString(R.string.arrow_beside)).performScrollTo().performClick()
        compose.waitForIdle()
        assertFalse(settings().statusArrowAbove)
        compose.onAllNodesWithText("S")[0].performScrollTo().performClick()   // the number's S
        compose.waitForIdle()
        assertEquals(0, settings().statusNumberSize)
        shot("statusbar_settings_beside")
    }

    @Test
    fun withoutMinuteModeTheWindowRests() {
        show(AppSettings(liveEnabled = false))
        compose.onNodeWithText(context.getString(R.string.settings_statusbar_off)).assertExists()
        compose.onNodeWithTag("statusbar-preview").assertDoesNotExist()
        compose.onAllNodesWithText("XL")[0].assertIsNotEnabled()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(500)   // the first frame reaches the screen a moment after the composition is idle
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
