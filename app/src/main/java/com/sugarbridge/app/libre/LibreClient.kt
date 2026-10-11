package com.sugarbridge.app.libre

import com.sugarbridge.app.glucose.Reading
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

/** A server response: status code, body and headers (names in lower case). */
class HttpResponse(val code: Int, val body: String, val headers: Map<String, String> = emptyMap())

/** Everything the client needs from the network. Real HTTP in the app, a fake in tests. */
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

// Messages are in English for logs; the app shows its own translated text for each type.
open class LibreError(message: String) : Exception(message)
open class LoginFailed(message: String) : LibreError(message)

/** The server doesn't know this email and password (or the account lives on the other server). */
class WrongCredentials(val serverMessage: String) : LoginFailed("Wrong email or password ($serverMessage)")

/** The account exists, but LibreLinkUp waits for the person to do something: accept the terms, the privacy policy… */
class LoginStepRequired(val step: String) : LoginFailed("Confirm the \"$step\" step in the LibreLinkUp app, then sign in again")
class AuthExpired : LibreError("LibreLinkUp token expired")
class RateLimited(val retryAfter: Int) : LibreError("Too many requests, wait $retryAfter s")
class NoConnections : LibreError("LibreLinkUp has no connections: invite yourself from LibreLink")
class ApiChanged(val minimum: String) : LibreError("LibreLinkUp asks for version $minimum, which is already sent: the API seems to have changed")

/** LibreLinkUp answered with an unexpected HTTP status. An IOException, so it is retried like a network failure. */
class ServerError(val code: Int) : IOException("LibreLinkUp answered with HTTP $code")

/** A saved sign-in: region, token and account-id. With it there is no need to sign in on every start. */
data class Session(val region: String, val token: String, val accountId: String)

// Russia is a separate Abbott system (its own LibreView and LibreLinkUp-RU); the rest of the world is libreview.io with regions
private val SPECIAL_REGIONS = mapOf("ru" to "https://api.libreview.ru", "us" to "https://api.libreview.io")

fun regionUrl(region: String): String = SPECIAL_REGIONS[region] ?: "https://api-$region.libreview.io"

/**
 * Servers to start signing in with while the account's region is unknown. The Russian and the world
 * servers don't know each other's accounts, so both are tried, the likely one by the phone's country first.
 * Within the world server, the server itself points to the region (eu, de, fr…).
 */
fun startRegionsFor(country: String): List<String> = if (country.equals("RU", ignoreCase = true)) {
    listOf("ru", "us")
} else {
    listOf("us", "ru")
}

fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

/** Compares versions like "4.16.0": negative when a is older than b. */
fun compareVersions(a: String, b: String): Int {
    val x = a.split('.').map { it.trim().toIntOrNull() ?: 0 }
    val y = b.split('.').map { it.trim().toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
        if (d != 0) return d
    }
    return 0
}

const val DEFAULT_LLU_VERSION = "4.16.0"

private val BASE_HEADERS = mapOf(
    "product" to "llu.android",   // we present ourselves as the LibreLinkUp Android app
    "content-type" to "application/json",
)

class LibreClient(
    private val email: String,
    private val password: String,
    private val http: HttpTransport = UrlConnectionTransport(),
    var session: Session? = null,
    private val onLogin: (Session) -> Unit = {},     // lets the app save the new session
    version: String = DEFAULT_LLU_VERSION,
    private val onVersion: (String) -> Unit = {},    // Abbott raised the minimum version: the app remembers it
    private val startRegions: List<String> = startRegionsFor(Locale.getDefault().country),
) {
    var region: String = session?.region ?: startRegions.first()
        private set
    var version: String = version
        private set
    private var patientId: String? = null

    val base: String get() = regionUrl(region)

    private fun headers() = BASE_HEADERS + ("version" to version)

    /**
     * Signs in. If the region is known from an earlier sign-in, only there. Otherwise the Russian and the world
     * servers in turn: on the "wrong" one the account looks like a wrong password, and a server may be unreachable.
     */
    @Synchronized
    fun login() {
        val starts = session?.region?.let { listOf(it) } ?: startRegions.distinct()
        var failure: Exception? = null
        for (start in starts) {
            region = start
            try {
                loginHere()
                return
            } catch (e: LoginStepRequired) {
                throw e   // the account was found, another server won't help
            } catch (e: LoginFailed) {
                failure = e   // this server doesn't know the account or the password is wrong: try the next one
            } catch (e: IOException) {
                if (failure == null) failure = e   // server unreachable; a "wrong password" answer matters more
            }
        }
        throw failure ?: LoginFailed("Sign-in failed")
    }

    private fun loginHere() {
        var redirects = 0
        var upgrades = 0
        while (true) {
            val body = JSONObject().put("email", email).put("password", password).toString()
            val resp = http.send("POST", "$base/llu/auth/login", headers(), body)
            if (resp.code == 429) throw RateLimited(retryAfter(resp))
            val json = jsonOrNull(resp.body)
            if (json != null && upgradeIfAsked(json)) {   // the server wants a newer version: retry with it
                if (++upgrades > 2) throw LibreError("LibreLinkUp keeps asking for a newer app version")
                continue
            }
            if (resp.code != 200 || json == null) throw ServerError(resp.code)
            minimumVersionOf(json)?.let { throw ApiChanged(it) }
            val data = json.optJSONObject("data")
            if (data != null && data.optBoolean("redirect")) {   // the account lives in another region
                if (++redirects > 1) throw LoginFailed("The server keeps redirecting in a loop")
                region = data.getString("region")
                continue
            }
            val token = data?.optJSONObject("authTicket")?.optString("token")
            if (data == null || token.isNullOrEmpty()) throw loginError(json)
            val userId = data.getJSONObject("user").getString("id")
            val newSession = Session(region, token, sha256(userId))
            session = newSession
            onLogin(newSession)
            return
        }
    }

    private fun loginError(json: JSONObject): LoginFailed {
        val step = json.optJSONObject("data")?.optJSONObject("step")?.optString("type")
        if (!step.isNullOrEmpty()) return LoginStepRequired(step)
        // both the Russian and the world servers answer like this when there is no such account or the password is wrong
        val message = json.optJSONObject("error")?.optString("message")
        val known = message != null && listOf("incorrect", "notauthenticated").any { it in message.lowercase() }
        return if (known) WrongCredentials(message) else LoginFailed(message ?: json.toString())
    }

    /** If the server requires a newer version than ours, remember it and report that the request is worth repeating. */
    private fun upgradeIfAsked(json: JSONObject): Boolean {
        val minimum = minimumVersionOf(json) ?: return false
        if (compareVersions(minimum, version) <= 0) return false
        version = minimum
        onVersion(minimum)
        return true
    }

    private fun minimumVersionOf(json: JSONObject): String? =
        json.optJSONObject("data")?.optString("minimumVersion")?.takeIf { it.isNotBlank() }

    private fun get(path: String, retried: Boolean = false): JSONObject {
        val s = session ?: run { login(); session!! }
        val headers = headers() + mapOf("authorization" to "Bearer ${s.token}", "account-id" to s.accountId)
        val resp = http.send("GET", base + path, headers, null)
        if (resp.code == 401) throw AuthExpired()
        if (resp.code == 429) throw RateLimited(retryAfter(resp))
        val json = jsonOrNull(resp.body)
        if (json != null && !retried && upgradeIfAsked(json)) return get(path, retried = true)
        if (resp.code != 200 || json == null) throw ServerError(resp.code)
        minimumVersionOf(json)?.let { throw ApiChanged(it) }
        return json
    }

    private fun findPatient(): String {
        patientId?.let { return it }
        val list = get("/llu/connections").optJSONArray("data")
        if (list == null || list.length() == 0) throw NoConnections()
        return list.getJSONObject(0).getString("patientId").also { patientId = it }
    }

    private fun fetchGraph(): List<Reading> =
        parseGraph(get("/llu/connections/${findPatient()}/graph").getJSONObject("data"))

    /** Readings for the last ~12 hours plus the current one, by time. If the token expired, signs in again once. */
    @Synchronized
    fun readings(): List<Reading> = try {
        fetchGraph()
    } catch (e: AuthExpired) {
        login()
        fetchGraph()
    }
}

private fun jsonOrNull(body: String): JSONObject? = try {
    if (body.isBlank()) null else JSONObject(body)
} catch (e: JSONException) {
    null
}

private fun retryAfter(resp: HttpResponse): Int = resp.headers["retry-after"]?.toIntOrNull() ?: 60
