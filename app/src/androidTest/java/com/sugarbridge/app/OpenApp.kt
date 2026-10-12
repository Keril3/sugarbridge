package com.sugarbridge.app

import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream

/**
 * Opens the app's main screen the way the launcher does. A test's own startActivity counts as a start from the
 * background, which Android allows only shortly after the app was on screen; after a long test it is silently
 * dropped and the screen never comes up. The shell may always open it. "-W" waits until it is on screen.
 */
fun openApp() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val app = instrumentation.targetContext.packageName
    instrumentation.uiAutomation.executeShellCommand("am start -W -n $app/.MainActivity").use { output ->
        FileInputStream(output.fileDescriptor).use { it.readBytes() }   // wait for "am start" to finish
    }
}
