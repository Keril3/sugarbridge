package com.sugarbridge.app.libreview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.sugarbridge.app.R
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
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The LibreView site inside the app. LibreView protects the data export with an "I'm not a robot" captcha,
 * so the history can't be downloaded without a person. But the file the person downloads can be caught:
 * a plain link, data: or blob: (the site often builds the CSV right in the browser).
 */
class LibreViewActivity : ComponentActivity() {

    private lateinit var web: WebView
    @Volatile private var pageHost: String? = null
    private var pageProgress by mutableIntStateOf(0)
    private var status by mutableStateOf<String?>(null)
    private var working by mutableStateOf(false)   // a file is being fetched and checked

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        web = WebView(this).apply {
            // Compose would give the view WRAP_CONTENT, and then WebView lays the page out with zero height:
            // 100vh and "height: 100%" become 0 and LibreView squeezes its whole page into a 50 px box
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // WebView enlarges site text by the phone's font size, and LibreView's fixed-width cards can't take it:
            // words spill out of them. The site gets its own text size; a pinch zooms it all evenly instead
            settings.textZoom = 100
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            // some sites take "; wv" in the User-Agent for an embedded browser and simplify the page
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            addJavascriptInterface(CsvBridge(), "SugarBridgeCsv")
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    pageHost = url.toUri().host
                }

                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(KEEP_BLOBS, null)
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith("blob:") || url.startsWith("data:")) {
                        download(url)
                        return true
                    }
                    return false
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
        val testPage = intent.getStringExtra(EXTRA_TEST_HTML)   // only for the instrumented test
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
                    OutlinedTitle("LibreView", Modifier.weight(1f))
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

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun download(url: String) {
        status = getString(R.string.libreview_fetching)
        working = true
        when {
            url.startsWith("blob:") -> web.evaluateJavascript(readBlob(url), null)
            url.startsWith("data:") -> acceptDataUrl(url)
            url.startsWith("http") -> {
                val userAgent = web.settings.userAgentString
                val cookies = CookieManager.getInstance().getCookie(url)
                lifecycleScope.launch {
                    val bytes = withContext(Dispatchers.IO) { runCatching { fetch(url, userAgent, cookies) } }
                    bytes.onSuccess(::accept).onFailure { fail(getString(R.string.libreview_download_failed, it.message.orEmpty())) }
                }
            }
            else -> fail(getString(R.string.libreview_bad_link))
        }
    }

    private fun fail(message: String) {
        status = message
        working = false
    }

    private fun fetch(url: String, userAgent: String, cookies: String?): ByteArray {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", userAgent)
        if (cookies != null) conn.setRequestProperty("Cookie", cookies)
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** data:[type][;base64],content */
    private fun acceptDataUrl(dataUrl: String) {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) {
            fail(getString(R.string.libreview_bad_data))
            return
        }
        val meta = dataUrl.substring(0, comma)
        val payload = dataUrl.substring(comma + 1)
        val bytes = if (meta.endsWith(";base64")) {
            Base64.decode(payload, Base64.DEFAULT)
        } else {
            URLDecoder.decode(payload, "UTF-8").toByteArray()
        }
        accept(bytes)
    }

    /** Checks that this really is a glucose export and hands the file to the main screen. */
    private fun accept(bytes: ByteArray) {
        lifecycleScope.launch {
            val found = withContext(Dispatchers.Default) {
                runCatching { LibreViewCsv.parse(LibreViewCsv.decode(bytes)).readings.size }.getOrDefault(0)
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
        fun onData(dataUrl: String) {
            if (!trusted(pageHost)) return   // files are accepted only from LibreView pages
            runOnUiThread { acceptDataUrl(dataUrl) }
        }

        @JavascriptInterface
        fun onError(message: String) {
            runOnUiThread { fail(getString(R.string.libreview_read_failed, message)) }
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
        internal const val EXTRA_TEST_HTML = "test_html"

        private fun trusted(host: String?): Boolean =
            host != null && listOf("libreview.com", "libreview.io", "libreview.ru").any { host == it || host.endsWith(".$it") }

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

        private fun readBlob(url: String): String {
            val quoted = "'" + url.replace("\\", "\\\\").replace("'", "\\'") + "'"
            return """
                (function (u) {
                  var kept = window.__sbBlobs && window.__sbBlobs[u];
                  var blob = kept ? Promise.resolve(kept) : fetch(u).then(function (r) { return r.blob(); });
                  blob.then(function (b) {
                    var reader = new FileReader();
                    reader.onloadend = function () { SugarBridgeCsv.onData(reader.result); };
                    reader.onerror = function () { SugarBridgeCsv.onError('read failed'); };
                    reader.readAsDataURL(b);
                  }).catch(function (e) { SugarBridgeCsv.onError(String(e)); });
                })($quoted);
            """
        }
    }
}
