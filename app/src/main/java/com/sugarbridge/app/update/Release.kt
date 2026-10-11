package com.sugarbridge.app.update

import com.sugarbridge.app.libre.compareVersions
import org.json.JSONObject

const val GITHUB_REPO = "Keril3/sugarbridge"
const val GITHUB_PAGE = "https://github.com/$GITHUB_REPO"
const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
const val APK_NAME = "SugarBridge.apk"

/** A published version: "1.4.0", where its APK is, its page and its notes from the CHANGELOG. */
data class Release(val version: String, val apkUrl: String, val pageUrl: String, val notes: String)

/**
 * The answer of GitHub's "latest release" API. Only an APK attached to a release of this repository counts:
 * drafts and pre-releases never come from this endpoint.
 */
fun parseLatestRelease(json: String): Release? {
    val o = JSONObject(json)
    val version = o.optString("tag_name").removePrefix("v").takeIf { it.isNotBlank() } ?: return null
    val assets = o.optJSONArray("assets") ?: return null
    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == APK_NAME } ?: return null
    val url = apk.optString("browser_download_url")
    if (!url.startsWith("https://github.com/$GITHUB_REPO/releases/download/")) return null
    return Release(version, url, o.optString("html_url", GITHUB_PAGE), o.optString("body"))
}

/** true when [release] is newer than the installed [current] version ("1.3.0", "1.4.0-debug" …). */
fun isNewer(release: Release, current: String): Boolean =
    compareVersions(release.version, current.substringBefore('-')) > 0
