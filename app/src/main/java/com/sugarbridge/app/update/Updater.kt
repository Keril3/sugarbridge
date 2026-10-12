package com.sugarbridge.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.sugarbridge.app.MainActivity
import com.sugarbridge.app.libre.HttpTransport
import com.sugarbridge.app.libre.UrlConnectionTransport
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.time.Instant

/** GitHub answered with an error: the message names GitHub, not LibreLinkUp. */
class GitHubError(val code: Int) : IOException("GitHub answered with HTTP $code")

/** Why a downloaded file can't be installed. */
class UpdateRejected(val reason: Reason) : Exception("Update rejected: $reason") {
    enum class Reason { NOT_SUGARBRIDGE, NOT_NEWER, OTHER_KEY }
}

/**
 * Updates from GitHub Releases: checks the latest release, downloads its APK and hands it to Android's
 * package installer. Android shows its own confirmation and installs only an APK signed with the same key
 * as the installed app; SugarBridge checks that before asking, so a wrong file never gets that far.
 */
object Updater {
    private const val PREFS = "update"
    private val CHECK_EVERY: Duration = Duration.ofHours(6)

    /** Network stand-in for tests. */
    @Volatile
    internal var transportForTests: HttpTransport? = null

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** The latest release from GitHub. Blocking: call it off the main thread. */
    fun fetchLatest(): Release? {
        val http = transportForTests ?: UrlConnectionTransport()
        val resp = http.send(
            "GET", LATEST_RELEASE_API,
            mapOf("accept" to "application/vnd.github+json", "user-agent" to "SugarBridge"),
            null,
        )
        if (resp.code == 404) return null   // no releases yet
        if (resp.code != 200) throw GitHubError(resp.code)
        return parseLatestRelease(resp.body)
    }

    /** Asks GitHub at most every 6 hours unless [force]; remembers a newer release for the screens. */
    fun check(context: Context, force: Boolean = false): Release? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong("checked_at", 0)
        if (!force && Instant.ofEpochMilli(last).plus(CHECK_EVERY).isAfter(Instant.now())) return known(context)
        val latest = fetchLatest()
        prefs.edit {
            putLong("checked_at", System.currentTimeMillis())
            if (latest != null && isNewer(latest, installedVersion(context))) {
                putString("version", latest.version).putString("apk", latest.apkUrl).putString("page", latest.pageUrl)
            } else {
                remove("version").remove("apk").remove("page")
            }
        }
        return known(context)
    }

    /** The newer release found by the last check, if it is still newer than what is installed. */
    fun known(context: Context): Release? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = prefs.getString("version", null) ?: return null
        val release = Release(version, prefs.getString("apk", null) ?: return null, prefs.getString("page", GITHUB_PAGE)!!, "")
        return release.takeIf { isNewer(it, installedVersion(context)) }
    }

    /** Downloads the APK into the cache, reporting progress from 0 to 1. Blocking. */
    fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // only one update file at a time
        val file = File(dir, "SugarBridge-${release.version}.apk")
        val conn = URI(release.apkUrl).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "SugarBridge")
        try {
            if (conn.responseCode != 200) throw GitHubError(conn.responseCode)
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (file.length() == 0L) throw IOException("empty download")
        return file
    }

    /** The file must be SugarBridge, newer than the installed version and signed with the same key. */
    fun verify(context: Context, file: File) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw UpdateRejected(UpdateRejected.Reason.NOT_SUGARBRIDGE)
        if (archive.packageName != context.packageName) throw UpdateRejected(UpdateRejected.Reason.NOT_SUGARBRIDGE)
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.longVersionCode <= installed.longVersionCode) throw UpdateRejected(UpdateRejected.Reason.NOT_NEWER)
        if (signers(archive) != signers(installed)) throw UpdateRejected(UpdateRejected.Reason.OTHER_KEY)
    }

    private fun signers(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()

    /** Can SugarBridge ask Android to install apps? The person allows it once in the system settings. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Hands the verified APK to the package installer. Android answers through [MainActivity] with
     * [ACTION_INSTALL_STATUS]: first with the confirmation screen to show, then with the result.
     */
    fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)   // still asked when Android insists
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("SugarBridge.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val status = Intent(context, MainActivity::class.java)
                .setAction(ACTION_INSTALL_STATUS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(context, id, status, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }

    const val ACTION_INSTALL_STATUS = "com.sugarbridge.app.INSTALL_STATUS"
}
