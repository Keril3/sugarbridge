package com.sugarbridge.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.libreview.LibreViewActivity
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.update.Updater
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The main screen is open to every app, and the package installer's answer holds an Intent to launch. Another app
 * must not be able to pass one off as that answer and launch anything in SugarBridge's name: the answer is taken
 * only through the door no other app can open.
 */
@RunWith(AndroidJUnit4::class)
class IntentSafetyTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var monitor: Instrumentation.ActivityMonitor

    @Before
    fun setUp() {
        withoutUpdateOffers(context)
        Libre.signOut(context)
        SettingsStore(context).languageChosen = true
        monitor = instrumentation.addMonitor(LibreViewActivity::class.java.name, null, false)
    }

    @After
    fun tearDown() {
        instrumentation.removeMonitor(monitor)
        withUpdateOffers(context)
    }

    private fun Intent.withHiddenLaunch() = apply {
        putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_PENDING_USER_ACTION)
        putExtra(Intent.EXTRA_INTENT, Intent(context, LibreViewActivity::class.java))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun Activity.close() = instrumentation.runOnMainSync { finish() }

    @Test
    fun anotherAppCantPassOffAnIntentAsTheInstallersAnswer() {
        val foreign = Intent(context, MainActivity::class.java).setAction(Updater.ACTION_INSTALL_STATUS).withHiddenLaunch()
        val screen = instrumentation.startActivitySync(foreign)
        try {
            assertNull("another app launched a screen of SugarBridge's", monitor.waitForActivityWithTimeout(3_000))
        } finally {
            screen.close()
        }
    }

    /** The other side of it: through its own door the answer still works, or updates would stop installing. */
    @Test
    fun theInstallersAnswerStillComesThroughItsOwnDoor() {
        val own = MainActivity.internalIntent(context, Updater.ACTION_INSTALL_STATUS).withHiddenLaunch()
        val screen = instrumentation.startActivitySync(own)
        try {
            val opened = monitor.waitForActivityWithTimeout(10_000)
            assertNotNull("the answer didn't reach the screen", opened)
            opened.close()
        } finally {
            screen.close()
        }
    }

    /** All the way: Android's real installer refuses a broken file, and its answer gets through the closed door. */
    @Test
    fun androidsRealInstallerGetsItsAnswerThrough() {
        val shell = instrumentation.uiAutomation
        shell.executeShellCommand("appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow").close()
        DebugLog.clear(context)
        val notAnApk = File(context.cacheDir, "not-an-apk.apk").apply { writeText("not an apk") }
        ActivityScenario.launch(MainActivity::class.java).use {
            Updater.install(context, notAnApk)
            val deadline = SystemClock.uptimeMillis() + 20_000
            while (DebugLog.read(context).none { it.text.startsWith("installer:") } && SystemClock.uptimeMillis() < deadline) Thread.sleep(200)
            assertTrue("no answer from the installer", DebugLog.read(context).any { it.text.startsWith("installer:") })
            assertEquals("error", DebugLog.read(context).first { it.text.startsWith("installer:") }.kind)
        }
        notAnApk.delete()
    }
}
