package com.sugarbridge.app.libre

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.time.LocalDateTime

const val PATIENT = "11111111-2222-3333-4444-555555555555"

/**
 * A fake LibreLinkUp: answers like the real one, but without the internet or an account.
 * As at Abbott, there are two separate systems: the Russian server doesn't know the accounts of the rest
 * of the world and vice versa, and within the world server the regions redirect to each other.
 */
class FakeLibreView(
    private val home: String = "api.libreview.ru",   // the server the account "lives" on
    private val password: String = "secret",
) : HttpTransport {
    var token = "T0KEN-1"
    var now: LocalDateTime = LocalDateTime.of(2026, 10, 8, 18, 0)   // time of the latest reading, UTC
    val values = mutableListOf(110.0, 112.0, 115.0)   // mg/dL: history + the current value
    var expireNext = false     // the next request with a token gets 401
    var limitNext = false      // the next request gets 429
    var connections = JSONArray().put(JSONObject().put("patientId", PATIENT).put("firstName", "Me"))
    var minVersion: String? = null    // the server requires at least this app version
    var alwaysAskVersion = false      // …and asks for it even when it is already sent (a broken API)
    var step: String? = null          // sign-in waits for the person: "tou" = accept the terms
    var unreachable = emptySet<String>()   // servers that can't be reached
    var logins = 0
    val loginHosts = mutableListOf<String>()   // where the client tried to sign in, in order
    val requests = mutableListOf<String>()   // a request log for the tests to check

    override fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse {
        check(headers["product"] == "llu.android") { "The client didn't send the product header" }
        val uri = URI(url)
        if (uri.host in unreachable) throw IOException("no route to ${uri.host}")
        requests.add("$method ${uri.host}${uri.path}")
        val min = minVersion
        if (min != null && (alwaysAskVersion || compareVersions(headers["version"] ?: "0", min) < 0)) {
            // this is how LibreLinkUp answers when Abbott raised the minimum app version
            val data = JSONObject().put("minimumVersion", min)
            return HttpResponse(200, JSONObject().put("status", 920).put("data", data).toString())
        }
        if (uri.path == "/llu/auth/login") return login(uri.host, JSONObject(body!!))
        if (headers["authorization"] != "Bearer $token") return HttpResponse(401, """{"status":2}""")
        if (limitNext) {
            limitNext = false
            return HttpResponse(429, "", mapOf("retry-after" to "120"))
        }
        if (expireNext) {
            expireNext = false
            token = "T0KEN-2"
            return HttpResponse(401, """{"status":2}""")
        }
        return when (uri.path) {
            "/llu/connections" -> ok(connections)
            "/llu/connections/$PATIENT/graph" -> ok(graph())
            else -> HttpResponse(404, "")
        }
    }

    private fun ok(data: Any) = HttpResponse(200, JSONObject().put("status", 0).put("data", data).toString())

    private fun login(host: String, body: JSONObject): HttpResponse {
        logins++
        loginHosts += host
        // the Russian and the world servers are separate systems: an unknown account looks like a wrong password
        if (host.endsWith(".ru") != home.endsWith(".ru")) return notAuthenticated()
        if (host != home) {   // the account is in another region of the world server: the server redirects there
            return ok(JSONObject().put("redirect", true).put("region", regionOf(home)))
        }
        if (body.getString("password") != password) return notAuthenticated()
        step?.let {
            val data = JSONObject().put("step", JSONObject().put("type", it))
            return HttpResponse(200, JSONObject().put("status", 4).put("data", data).toString())
        }
        return ok(
            JSONObject()
                .put("user", JSONObject().put("id", "user-42"))
                .put("authTicket", JSONObject().put("token", token))
        )
    }

    // word for word how the real Abbott servers answer an unknown account or a wrong password
    private fun notAuthenticated(): HttpResponse {
        val error = JSONObject().put("message", "incorrect username/password")
        return HttpResponse(200, JSONObject().put("status", 2).put("error", error).toString())
    }

    private fun regionOf(host: String): String = when (host) {
        "api.libreview.ru" -> "ru"
        "api.libreview.io" -> "us"
        else -> host.removePrefix("api-").substringBefore(".libreview.io")
    }

    private fun graph(): JSONObject {
        val n = values.size
        val history = JSONArray()
        for (i in 0 until n - 1) {   // the history: every 15 minutes
            val t = now.minusMinutes(15L * (n - 1 - i))
            history.put(item(t, values[i]))
        }
        val current = item(now, values.last()).put("TrendArrow", 4)
        return JSONObject()
            .put("connection", JSONObject().put("glucoseMeasurement", current))
            .put("graphData", history)
    }

    private fun item(t: LocalDateTime, mgDl: Double): JSONObject {
        val stamp = t.format(LIBRE_TIME)
        return JSONObject().put("FactoryTimestamp", stamp).put("Timestamp", stamp).put("ValueInMgPerDl", mgDl)
    }
}
