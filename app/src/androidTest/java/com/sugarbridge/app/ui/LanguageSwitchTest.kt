package com.sugarbridge.app.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Switching the language must not restart the screen: a restart is what made the whole screen flash black.
 * The same Activity stays and only redraws its texts in the new language.
 */
@RunWith(AndroidJUnit4::class)
class LanguageSwitchTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = withoutUpdateOffers(context)

    @After
    fun cleanUp() {
        useAppLanguage(context, null)
        withUpdateOffers(context)
    }

    @Test
    fun languageChangesWithoutRestartingTheScreen() {
        Libre.signOut(context)
        SettingsStore(context).languageChosen = true   // the sign-in screen
        useAppLanguage(context, "en")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText(resourcesIn(context, "en").getString(R.string.login_title)).assertExists()
        lateinit var before: MainActivity
        scenario.onActivity { before = it }

        useAppLanguage(context, "de")
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText(resourcesIn(context, "de").getString(R.string.login_title)).assertExists() }.isSuccess
        }
        scenario.onActivity { assertSame("the screen was restarted", before, it) }
        scenario.close()
    }
}
