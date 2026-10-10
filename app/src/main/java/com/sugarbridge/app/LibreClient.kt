package com.sugarbridge.app

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/** Ответ сервера: код, тело и заголовки (имена в нижнем регистре). */
class HttpResponse(val code: Int, val body: String, val headers: Map<String, String> = emptyMap())

/** Всё, что клиенту нужно от сети. В приложении — настоящий HTTP, в тестах — подделка. */
fun interface HttpTransport {
    fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse
}

class UrlConnectionTransport : HttpTransport {
    override fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            val responseHeaders = conn.headerFields
                .filterKeys { it != null }
                .map { (name, values) -> name.lowercase() to values.first() }
                .toMap()
            return HttpResponse(code, text, responseHeaders)
        } finally {
            conn.disconnect()
        }
    }
}

open class LibreError(message: String) : Exception(message)
class LoginFailed(message: String) : LibreError(message)
class AuthExpired : LibreError("Токен LibreLinkUp устарел")
class RateLimited(val retryAfter: Int) : LibreError("Слишком частые запросы, подождать $retryAfter с")

/** Сохранённый вход: регион, токен и account-id. С ним не нужно входить при каждом запуске. */
data class Session(val region: String, val token: String, val accountId: String)

private val SPECIAL_REGIONS = mapOf("ru" to "https://api.libreview.ru", "us" to "https://api.libreview.io")

fun regionUrl(region: String): String = SPECIAL_REGIONS[region] ?: "https://api-$region.libreview.io"

fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

private val BASE_HEADERS = mapOf(
    "product" to "llu.android",   // представляемся приложением LibreLinkUp для Android
    "version" to "4.16.0",
    "content-type" to "application/json",
)

class LibreClient(
    private val email: String,
    private val password: String,
    private val http: HttpTransport = UrlConnectionTransport(),
    var session: Session? = null,
    private val onLogin: (Session) -> Unit = {},   // чтобы приложение сохранило новую сессию
) {
    var region: String = session?.region ?: "ru"
        private set
    private var patientId: String? = null

    val base: String get() = regionUrl(region)

    @Synchronized
    fun login() {
        for (attempt in 1..2) {   // не больше одного перехода в другой регион
            val body = JSONObject().put("email", email).put("password", password).toString()
            val resp = http.send("POST", "$base/llu/auth/login", BASE_HEADERS, body)
            if (resp.code == 429) throw RateLimited(retryAfter(resp))
            if (resp.code != 200) throw IOException("Вход: LibreLinkUp ответил кодом ${resp.code}")
            val json = JSONObject(resp.body)
            val data = json.optJSONObject("data")
            if (data != null && data.optBoolean("redirect")) {   // аккаунт живёт в другом регионе
                region = data.getString("region")
                continue
            }
            val token = data?.optJSONObject("authTicket")?.optString("token")
            if (data == null || token.isNullOrEmpty()) throw LoginFailed(loginError(json))
            val userId = data.getJSONObject("user").getString("id")
            val newSession = Session(region, token, sha256(userId))
            session = newSession
            onLogin(newSession)
            return
        }
        throw LoginFailed("Сервер перенаправляет по кругу")
    }

    private fun loginError(json: JSONObject): String {
        val step = json.optJSONObject("data")?.optJSONObject("step")?.optString("type")
        if (!step.isNullOrEmpty()) {
            return "Откройте приложение LibreLinkUp и подтвердите там шаг «$step», потом войдите снова"
        }
        val message = json.optJSONObject("error")?.optString("message")
        return "Вход не удался: ${message ?: json.toString()}"
    }

    private fun get(path: String): JSONObject {
        val s = session ?: run { login(); session!! }
        val headers = BASE_HEADERS + mapOf("authorization" to "Bearer ${s.token}", "account-id" to s.accountId)
        val resp = http.send("GET", base + path, headers, null)
        return when (resp.code) {
            200 -> JSONObject(resp.body)
            401 -> throw AuthExpired()
            429 -> throw RateLimited(retryAfter(resp))
            else -> throw IOException("LibreLinkUp ответил кодом ${resp.code}")
        }
    }

    private fun findPatient(): String {
        patientId?.let { return it }
        val list = get("/llu/connections").optJSONArray("data")
        if (list == null || list.length() == 0) {
            throw LibreError("В LibreLinkUp нет подключений: пригласите себя из LibreLink")
        }
        return list.getJSONObject(0).getString("patientId").also { patientId = it }
    }

    private fun fetchGraph(): List<Reading> =
        parseGraph(get("/llu/connections/${findPatient()}/graph").getJSONObject("data"))

    /** Замеры за последние ~12 часов плюс текущий, по времени. Если токен устарел — входит заново один раз. */
    @Synchronized
    fun readings(): List<Reading> = try {
        fetchGraph()
    } catch (e: AuthExpired) {
        login()
        fetchGraph()
    }
}

private fun retryAfter(resp: HttpResponse): Int = resp.headers["retry-after"]?.toIntOrNull() ?: 60
