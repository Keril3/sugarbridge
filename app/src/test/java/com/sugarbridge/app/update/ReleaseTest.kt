package com.sugarbridge.app.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTest {

    private fun answer(tag: String, vararg assets: Pair<String, String>) = JSONObject()
        .put("tag_name", tag)
        .put("html_url", "https://github.com/$GITHUB_REPO/releases/tag/$tag")
        .put("body", "- notes")
        .put("assets", JSONArray().apply { assets.forEach { (name, url) -> put(JSONObject().put("name", name).put("browser_download_url", url)) } })
        .toString()

    private val apkUrl = "https://github.com/$GITHUB_REPO/releases/download/v1.4.0/SugarBridge.apk"

    @Test
    fun latestReleaseIsReadFromGitHubAnswer() {
        val release = parseLatestRelease(answer("v1.4.0", "SugarBridge.apk.sha256" to "$apkUrl.sha256", "SugarBridge.apk" to apkUrl))!!
        assertEquals("1.4.0", release.version)
        assertEquals(apkUrl, release.apkUrl)
        assertEquals("- notes", release.notes)
    }

    @Test
    fun releaseWithoutTheApkIsIgnored() {
        assertNull(parseLatestRelease(answer("v1.4.0", "notes.txt" to "https://example.com/notes.txt")))
    }

    @Test
    fun apkFromAnotherPlaceIsIgnored() {
        // only files attached to this repository's releases are trusted for downloading
        assertNull(parseLatestRelease(answer("v1.4.0", "SugarBridge.apk" to "https://evil.example.com/SugarBridge.apk")))
        assertNull(parseLatestRelease(answer("v1.4.0", "SugarBridge.apk" to "https://github.com/someone/else/releases/download/v1.4.0/SugarBridge.apk")))
    }

    @Test
    fun newerVersionsAreComparedNumerically() {
        val release = Release("1.10.0", apkUrl, "", "")
        assertTrue(isNewer(release, "1.9.3"))
        assertTrue(isNewer(release, "1.9.3-debug"))
        assertFalse(isNewer(release, "1.10.0"))
        assertFalse(isNewer(release, "2.0.0"))
    }
}
