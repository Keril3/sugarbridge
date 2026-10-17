package com.sugarbridge.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sugarbridge.app.MainActivity
import com.sugarbridge.app.MainViewModel
import com.sugarbridge.app.R
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.glucose.Reading
import com.sugarbridge.app.libre.Account
import com.sugarbridge.app.libre.AccountStore
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.libre.Session
import com.sugarbridge.app.libre.sha256
import com.sugarbridge.app.settings.AppSettings
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.sync.SyncStore
import com.sugarbridge.app.withUpdateOffers
import com.sugarbridge.app.withoutUpdateOffers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * The developer tools have a switch of their own on the App tab, and the event log is one window: what is older
 * than the days it keeps is gone, a filter shows one kind of event, each part of an entry has a line of its own.
 */
@RunWith(AndroidJUnit4::class)
class DeveloperLogTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = 24 * 3_600_000L
    private val entry = "minute: newest 18:05:00, 64 s old; 48 points, wrote 1 (18:05); LibreLinkUp 610 ms, Health Connect 85 ms"

    @Before
    fun setUp() {
        withoutUpdateOffers(context)
        // no background sync and no sync on opening: the demo account would only add errors to the log
        SettingsStore(context).save(AppSettings(backgroundMinutes = 0, liveEnabled = false, autoUpdateCheck = false, logDays = 3))
        SettingsStore(context).languageChosen = true
        AccountStore(context).save(Account("tenshi@example.com", "demo", Session("ru", "demo-token", sha256("demo"))))
        SyncStore(context).saveSuccess(listOf(Reading(Instant.now().minusSeconds(40), 110.0, 4)), 0)
        DebugLog.clear(context)
        val now = System.currentTimeMillis()
        File(context.filesDir, "events.log").writeText(
            listOf(
                "${now - 8 * day}\tsync\tlast week",
                "${now - 240_000}\tsync\t$entry",
                "${now - 180_000}\tphone\tscreen off",
                "${now - 120_000}\terror\tminute: NETWORK timeout",
            ).joinToString("\n", postfix = "\n")
        )
    }

    @After
    fun cleanUp() {
        SettingsStore(context).save(AppSettings())
        DebugLog.clear(context)
        Libre.signOut(context)
        SyncStore(context).clearDisplay()
        withUpdateOffers(context)
    }

    private fun shown() = compose.onAllNodesWithTag("log-entry").fetchSemanticsNodes().size

    /** A choice of the log window, not the same words on an entry. */
    private fun choose(label: String) =
        compose.onAllNodesWithText(label).filterToOne(hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

    @Test
    fun theLogKeepsItsDaysAndFiltersByKind() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: MainViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
            compose.waitUntil(10_000) { vm.ui.signedIn }
            compose.onNodeWithText(context.getString(R.string.tab_settings)).performClick()
            compose.onNodeWithText(context.getString(R.string.settings_tab_app)).performClick()

            // the switch on the App tab turns the tools on; then the button opens them
            compose.onNodeWithText(context.getString(R.string.settings_developer_open)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.settings_developer_switch)).performClick()
            compose.waitUntil(5_000) { vm.ui.settings.developer }
            compose.onNodeWithText(context.getString(R.string.settings_developer_open)).performSemanticsAction(SemanticsActions.OnClick)
            compose.onNodeWithTag("developer").performScrollToNode(hasTestTag("log"))
            compose.waitUntil(15_000) { shown() > 0 }

            // last week is beyond the three days kept: gone from the screen and from the file
            compose.onNodeWithText("last week").assertDoesNotExist()
            assertFalse(File(context.filesDir, "events.log").readText().contains("last week"))
            // each part after "; " on a line of its own
            compose.onNodeWithText(DebugLog.readable(entry)).assertExists()
            val all = shown()

            choose(context.getString(R.string.dev_kind_error))
            compose.waitUntil(5_000) { shown() == DebugLog.read(context).count { it.kind == "error" } }
            compose.onNodeWithText("minute: NETWORK timeout").assertExists()
            compose.onNodeWithText(DebugLog.readable(entry)).assertDoesNotExist()
            choose(context.getString(R.string.dev_kind_all))
            compose.waitUntil(5_000) { shown() >= all }

            // how long it is kept
            choose(context.resources.getQuantityString(R.plurals.days_count, 1, 1))
            compose.waitUntil(5_000) { vm.ui.settings.logDays == 1 }
            assertEquals(1, SettingsStore(context).load().logDays)
        }
    }
}
