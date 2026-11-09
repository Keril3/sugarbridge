package com.sugarbridge.app.libreview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.sugarbridge.app.R
import com.sugarbridge.app.debug.DebugLog
import com.sugarbridge.app.libre.Libre
import com.sugarbridge.app.settings.SettingsStore
import com.sugarbridge.app.ui.components.METER_FULL_HOLD_MS
import com.sugarbridge.app.ui.components.OutlinedTitle
import com.sugarbridge.app.ui.components.SecondaryButton
import com.sugarbridge.app.ui.components.SkyBackground
import com.sugarbridge.app.ui.components.XpMarquee
import com.sugarbridge.app.ui.components.XpMeter
import com.sugarbridge.app.ui.theme.LocalXp
import com.sugarbridge.app.ui.theme.SugarTheme
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The LibreView site inside the app. LibreView protects the data export with an "I'm not a robot" captcha,
 * so the history can't be downloaded without a person. But the file the person downloads can be caught:
 * a plain link, data: or blob: (the site often builds the CSV right in the browser).
 *
 * Other sites can open here too, through a link on LibreView's pages. They get no file through: files are taken
 * only from LibreView's pages, the sign-in cookies go only to LibreView, and the title names any other site.
 */
class LibreViewActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var pageHost by mutableStateOf<String?>(null)
    private val bridgeToken = UUID.randomUUID().toString()   // frames of other sites see the bridge, not this
    private var pageProgress by mutableIntStateOf(0)
    private var status by mutableStateOf<String?>(null)
    private var working by mutableStateOf(false)   // a file is being fetched and checked

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val view = try {
            WebView(this)
        } catch (e: RuntimeException) {   // Android's WebView is being updated or is turned off
            DebugLog.add(this, "error", "LibreView window: ${e.javaClass.simpleName}")
            Toast.makeText(this, getString(R.string.libreview_download_failed, getString(R.string.error_other)), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        web = view.apply {
            // Compose would give the view WRAP_CONTENT, and then WebView lays the page out with zero height:
            // 100vh and "height: 100%" become 0 and LibreView squeezes its whole page into a 50 px box
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false      // pages have no business with the phone's or the app's files
            settings.allowContentAccess = false
            // WebView enlarges site text by the phone's font size, and LibreView's fixed-width cards can't take it:
            // words spill out of them. The site gets its own text size; a pinch zooms it all evenly instead
            settings.textZoom = 100
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // some sites take "; wv" in the User-Agent for an embedded browser and simplify the page
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            addJavascriptInterface(CsvBridge(), BRIDGE)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    pageHost = url.toUri().host
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (isLibreViewHost(pageHost)) view.evaluateJavascript(KEEP_BLOBS, null)
                }

                /** Secure pages open here; a file is caught; plain http and links to other apps go nowhere. */
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    when (request.url.scheme) {
                        "https", "about" -> false
                        "blob", "data" -> {
                            download(request.url.toString())
                            true
                        }
                        else -> true
                    }

                /** Without this, a page's process dying (out of memory, say) would take the whole app with it. */
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    DebugLog.add(this@LibreViewActivity, "error", "LibreView window: the page's process stopped, crash: ${detail.didCrash()}")
                    finish()
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    pageProgress = newProgress
                }
            }
            setDownloadListener { url, _, _, _, _ -> download(url) }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)   // the captcha lives on another domain
        val testPage = pageForTests
        val site = libreViewSite(Libre.client(this)?.region, Locale.getDefault().country)
        if (testPage != null) web.loadDataWithBaseURL(site, testPage, "text/html", "utf-8", null) else web.loadUrl(site)
        onBackPressedDispatcher.addCallback(this) { if (web.canGoBack()) web.goBack() else finish() }

        setContent {
            SugarTheme(SettingsStore(this).load()) {
                LibreViewScreen()
            }
        }
    }

    /**
     * The site gets the whole screen below one compact row: the title, "?" for the steps and "Close".
     * The steps are hidden while the keyboard is up, so signing in keeps the page usable.
     */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun LibreViewScreen() {
        var help by rememberSaveable { mutableStateOf(false) }
        val xp = LocalXp.current
        SkyBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // there is no address bar: a page of another site says so here, before anyone types a password into it
                    OutlinedTitle(pageHost?.takeUnless(::isLibreViewHost) ?: "LibreView", Modifier.weight(1f).testTag("libreview-title"))
                    SecondaryButton("?", onClick = { help = !help })
                    SecondaryButton(stringResource(R.string.close), onClick = ::finish)
                }
                if (help && !WindowInsets.isImeVisible) {
                    Text(
                        stringResource(R.string.libreview_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = xp.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .background(xp.windowBody, MaterialTheme.shapes.medium)
                            .padding(10.dp),
                    )
                }
                status?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = xp.accent,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                // the page's real progress; when it is loaded the bar stays full for a moment, then goes
                var loadingBar by remember { mutableStateOf(false) }
                LaunchedEffect(pageProgress) {
                    if (pageProgress in 1..99) {
                        loadingBar = true
                    } else if (loadingBar) {
                        delay(METER_FULL_HOLD_MS)
                        loadingBar = false
                    }
                }
                if (loadingBar) XpMeter(pageProgress / 100f, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp))
                if (working) XpMarquee(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp))
                AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp))
            }
        }
    }

    /** A page in the background has no need to run: its scripts and timers would only use the battery. */
    override fun onPause() {
        super.onPause()
        if (::web.isInitialized) web.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)   // WebView asks to be out of the window before it is destroyed
            web.removeJavascriptInterface(BRIDGE)
            web.destroy()
        }
        super.onDestroy()
    }

    private fun download(url: String) {
        if (!isLibreViewHost(pageHost)) {   // a page of another site can't offer a file for the history
            fail(getString(R.string.libreview_bad_link))
            return
        }
        status = getString(R.string.libreview_fetching)
        working = true
        when {
            url.startsWith("blob:") -> web.evaluateJavascript(readBlob(url), null)
            url.startsWith("data:") -> acceptDataUrl(url)
            url.startsWith("https://") -> {
                val userAgent = web.settings.userAgentString
                lifecycleScope.launch {
                    val bytes = withContext(Dispatchers.IO) { runCatching { fetch(url, userAgent) } }
                    bytes.onSuccess(::accept).onFailure {
                        DebugLog.add(this@LibreViewActivity, "error", "LibreView file: ${it.message ?: it.javaClass.simpleName}")
                        fail(getString(R.string.libreview_download_failed, getString(R.string.error_other)))
                    }
                }
            }
            else -> fail(getString(R.string.libreview_bad_link))
        }
    }

    private fun fail(message: String) {
        status = message
        working = false
    }

    /**
     * The file behind a link. Redirects are followed here rather than by the connection, which would send the
     * sign-in cookies along to wherever a link leads: they go only to LibreView, and only over https.
     */
    private fun fetch(start: String, userAgent: String): ByteArray {
        var url = start
        repeat(MAX_REDIRECTS + 1) {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", userAgent)
            if (isLibreViewHost(conn.url.host)) CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            try {
                when (val code = conn.responseCode) {
                    200 -> return conn.inputStream.use { LibreViewCsv.readAtMost(it) } ?: throw IOException("the file is too large")
                    in 300..399 -> {
                        url = URI(url).resolve(conn.getHeaderField("Location") ?: throw IOException("HTTP $code without an address")).toString()
                        if (!url.startsWith("https://")) throw IOException("a redirect away from https")
                    }
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("too many redirects")
    }

    /** A file in a data: link, unpacked away from the main thread: it may be megabytes. */
    private fun acceptDataUrl(dataUrl: String) {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.Default) { decodeDataUrl(dataUrl) }
            if (bytes == null) fail(getString(R.string.libreview_bad_data)) else accept(bytes)
        }
    }

    /** Checks that this really is a glucose export and hands the file to the main screen. */
    private fun accept(bytes: ByteArray) {
        lifecycleScope.launch {
            val found = withContext(Dispatchers.Default) {
                try {
                    LibreViewCsv.parse(LibreViewCsv.decode(bytes)).readings.size
                } catch (e: Exception) {
                    0
                } catch (e: OutOfMemoryError) {
                    0
                }
            }
            if (found == 0) {
                fail(getString(R.string.libreview_no_glucose))
                return@launch
            }
            val file = File(cacheDir, "libreview.csv")
            withContext(Dispatchers.IO) { file.writeBytes(bytes) }
            setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, file.absolutePath))
            finish()
        }
    }

    private inner class CsvBridge {
        @JavascriptInterface
        fun onData(token: String, dataUrl: String) {
            if (token != bridgeToken) return
            runOnUiThread { if (isLibreViewHost(pageHost)) acceptDataUrl(dataUrl) }   // files are accepted only from LibreView pages
        }

        @JavascriptInterface
        fun onError(token: String, message: String) {
            if (token != bridgeToken) return
            DebugLog.add(this@LibreViewActivity, "error", "LibreView file: ${message.take(200)}")
            runOnUiThread { fail(getString(R.string.libreview_read_failed, getString(R.string.error_other))) }
        }
    }

    private fun readBlob(url: String): String {
        val quoted = "'" + url.replace("\\", "\\\\").replace("'", "\\'") + "'"
        return """
            (function (u) {
              var kept = window.__sbBlobs && window.__sbBlobs[u];
              var blob = kept ? Promise.resolve(kept) : fetch(u).then(function (r) { return r.blob(); });
              blob.then(function (b) {
                var reader = new FileReader();
                reader.onloadend = function () { $BRIDGE.onData('$bridgeToken', reader.result); };
                reader.onerror = function () { $BRIDGE.onError('$bridgeToken', 'read failed'); };
                reader.readAsDataURL(b);
              }).catch(function (e) { $BRIDGE.onError('$bridgeToken', String(e)); });
            })($quoted);
        """
    }

    companion object {
        const val EXTRA_PATH = "path"
        private const val BRIDGE = "SugarBridgeCsv"
        private const val MAX_REDIRECTS = 5

        /** A page instead of the site, for instrumented tests. Not an extra of the Intent, or an Intent could bring one. */
        @Volatile
        internal var pageForTests: String? = null

        /**
         * Signs out of LibreView in this window too: without it the next person to open it would be signed in.
         * Only if it was ever opened, since loading WebView just for this would hold up the screen. Main thread.
         */
        fun forgetSession(context: Context) {
            if (!File(context.dataDir, "app_webview").exists()) return
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
            }
        }

        /** The site may revoke the blob right after the click: keep a reference to it for another minute. */
        private const val KEEP_BLOBS = """
            (function () {
              if (window.__sbBlobs) return;
              window.__sbBlobs = {};
              var create = URL.createObjectURL, revoke = URL.revokeObjectURL;
              URL.createObjectURL = function (o) {
                var u = create.call(URL, o);
                try { if (o instanceof Blob) window.__sbBlobs[u] = o; } catch (e) {}
                return u;
              };
              URL.revokeObjectURL = function (u) {
                setTimeout(function () { delete window.__sbBlobs[u]; revoke.call(URL, u); }, 60000);
              };
            })();
        """
    }
}
