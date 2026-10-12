package com.sugarbridge.app.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sugarbridge.app.MainActivity
import com.sugarbridge.app.R
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.resourcesIn
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.useAppLanguage
import com.sugarbridge.app.withUpdateOffers
import com.sugarbridge.app.withoutUpdateOffers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Health Connect links to an app's privacy policy on its permission screen and in the permission settings. For
 * SugarBridge the link opens the privacy window, not just the home screen.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        withoutUpdateOffers(context)
        Libre.signOut(context)
        SettingsStore(context).languageChosen = true
        useAppLanguage(context, "en")
    }

    @After
    fun cleanUp() {
        useAppLanguage(context, null)
        withUpdateOffers(context)
    }

    private fun opens(action: String, shot: String? = null) {
        val intent = Intent(context, MainActivity::class.java).setAction(action)
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("privacy").assertExists() }.isSuccess }
            compose.onNodeWithText(resourcesIn(context, "en").getString(R.string.privacy_password)).assertExists()
            if (shot != null) {
                compose.waitForIdle()
                Thread.sleep(500)
                val dir = File(context.filesDir, "shots/en").apply { mkdirs() }
                File(dir, "$shot.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.onNodeWithText(resourcesIn(context, "en").getString(R.string.close)).performClick()
            compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("privacy").assertDoesNotExist() }.isSuccess }
        }
    }

    @Test
    fun healthConnectsPrivacyLinkShowsThePrivacyWindow() = opens("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE", shot = "privacy")

    @Test
    fun thePermissionSettingsLinkShowsItToo() = opens(Intent.ACTION_VIEW_PERMISSION_USAGE)
}
