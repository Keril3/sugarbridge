package com.sugarbridge.app.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The update path against the real GitHub Releases: the latest release is found, its APK downloads, and the
 * checks that run before Android is asked to install refuse anything that isn't a newer SugarBridge with our key.
 */
@RunWith(AndroidJUnit4::class)
class UpdaterTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun latest(): Release {
        val release = runCatching { Updater.fetchLatest() }.getOrNull()
        assumeTrue("GitHub is not reachable from this device", release != null)
        return release!!
    }

    @Test
    fun latestReleaseIsFoundOnGitHub() {
        val release = latest()
        assertTrue(release.version, Regex("""\d+\.\d+\.\d+""").matches(release.version))
        assertTrue(release.apkUrl, release.apkUrl.startsWith("https://github.com/$GITHUB_REPO/releases/download/v${release.version}/"))
    }

    @Test
    fun downloadedReleaseIsCheckedBeforeInstalling() {
        val release = latest()
        var progress = 0f
        val file = Updater.download(context, release) { progress = it }
        assertTrue("the APK is a few megabytes", file.length() > 1_000_000)
        assertEquals(1f, progress, 0.001f)
        try {
            Updater.verify(context, file)
            fail("a debug build must not accept the release: other key, and not newer than this version")
        } catch (e: UpdateRejected) {
            // the file is SugarBridge, so the refusal is about the version or the key, never about the package
            assertNotEquals(UpdateRejected.Reason.NOT_SUGARBRIDGE, e.reason)
        }
    }

    @Test
    fun anotherAppIsRefused() {
        val otherApk = File(instrumentation.context.packageCodePath)   // the test APK: another package
        try {
            Updater.verify(context, otherApk)
            fail("another package must be refused")
        } catch (e: UpdateRejected) {
            assertEquals(UpdateRejected.Reason.NOT_SUGARBRIDGE, e.reason)
        }
    }
}
