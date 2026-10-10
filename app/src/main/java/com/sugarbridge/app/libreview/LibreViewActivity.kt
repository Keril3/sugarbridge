package com.sugarbridge.app.libreview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.sugarbridge.app.ui.components.AngelBackground
import com.sugarbridge.app.ui.components.Hint
import com.sugarbridge.app.ui.components.TenshiCard
import com.sugarbridge.app.ui.theme.SugarTheme
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Сайт LibreView внутри приложения. Выгрузку данных LibreView защищает капчей «Я не робот»,
 * поэтому скачать историю без человека нельзя. Зато можно поймать файл, который человек скачал:
 * обычную ссылку, data: или blob: — сайт часто собирает CSV прямо в браузере.
 */
class LibreViewActivity : ComponentActivity() {

    private lateinit var web: WebView
    @Volatile private var pageHost: String? = null
    private var pageProgress by mutableIntStateOf(0)
    private var status by mutableStateOf<String?>(null)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // «; wv» в User-Agent некоторые сайты принимают за встроенный браузер и упрощают страницу
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)   // капча живёт на другом домене
        val testPage = intent.getStringExtra(EXTRA_TEST_HTML)   // только для инструментального теста
        if (testPage != null) web.loadDataWithBaseURL(START_URL, testPage, "text/html", "utf-8", null) else web.loadUrl(START_URL)
        onBackPressedDispatcher.addCallback(this) { if (web.canGoBack()) web.goBack() else finish() }

        setContent {
            SugarTheme {
                AngelBackground(halo = false) {   // нимб в углу мешал бы кнопке «Закрыть»
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("LibreView", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            TextButton(onClick = ::finish) { Text("Закрыть") }
                        }
                        TenshiCard(Modifier.padding(horizontal = 12.dp)) {
                            Hint(
                                "Войдите аккаунтом LibreLink (не LibreLinkUp) → «История глюкозы» → «Загрузить данные о глюкозе» → " +
                                    "«Я не робот» → «Загрузить». Файл SugarBridge заберёт сам."
                            )
                            status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                        }
                        if (pageProgress in 1..99) {
                            LinearProgressIndicator(progress = { pageProgress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                        AndroidView(factory = { web }, modifier = Modifier.fillMaxSize().padding(top = 8.dp))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun download(url: String) {
        status = "Забираю файл…"
        when {
            url.startsWith("blob:") -> web.evaluateJavascript(readBlob(url), null)
            url.startsWith("data:") -> acceptDataUrl(url)
            url.startsWith("http") -> {
                val userAgent = web.settings.userAgentString
                val cookies = CookieManager.getInstance().getCookie(url)
                lifecycleScope.launch {
                    val bytes = withContext(Dispatchers.IO) { runCatching { fetch(url, userAgent, cookies) } }
                    bytes.onSuccess(::accept).onFailure { status = "Не удалось скачать: ${it.message}" }
                }
            }
            else -> status = "Непонятная ссылка на файл"
        }
    }

    private fun fetch(url: String, userAgent: String, cookies: String?): ByteArray {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", userAgent)
        if (cookies != null) conn.setRequestProperty("Cookie", cookies)
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode != 200) error("сервер ответил кодом ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** data:[тип][;base64],содержимое */
    private fun acceptDataUrl(dataUrl: String) {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) {
            status = "Файл пришёл в непонятном виде"
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

    /** Проверяем, что это и правда выгрузка глюкозы, и отдаём файл главному экрану. */
    private fun accept(bytes: ByteArray) {
        lifecycleScope.launch {
            val found = withContext(Dispatchers.Default) {
                runCatching { LibreViewCsv.parse(LibreViewCsv.decode(bytes)).readings.size }.getOrDefault(0)
            }
            if (found == 0) {
                status = "Файл получен, но замеров глюкозы в нём нет. Нужна выгрузка «Данные о глюкозе»"
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
            if (!trusted(pageHost)) return   // файлы принимаем только со страниц LibreView
            runOnUiThread { acceptDataUrl(dataUrl) }
        }

        @JavascriptInterface
        fun onError(message: String) {
            runOnUiThread { status = "Не удалось забрать файл: $message" }
        }
    }

    companion object {
        const val EXTRA_PATH = "path"
        internal const val EXTRA_TEST_HTML = "test_html"
        private const val START_URL = "https://www.libreview.com/"

        private fun trusted(host: String?): Boolean =
            host != null && listOf("libreview.com", "libreview.io", "libreview.ru").any { host == it || host.endsWith(".$it") }

        /** Сайт может сразу «отозвать» blob после клика — держим ссылку на него ещё минуту. */
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
                    reader.onerror = function () { SugarBridgeCsv.onError('не прочитался'); };
                    reader.readAsDataURL(b);
                  }).catch(function (e) { SugarBridgeCsv.onError(String(e)); });
                })($quoted);
            """
        }
    }
}
