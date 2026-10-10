package com.sugarbridge.app.libre

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.LocalDateTime

const val PATIENT = "11111111-2222-3333-4444-555555555555"

/** Поддельный LibreLinkUp: отвечает как настоящий, но без интернета и без аккаунта. */
class FakeLibreView(
    private val home: String = "api.libreview.ru",   // в каком регионе «живёт» аккаунт
    private val password: String = "secret",
) : HttpTransport {
    var token = "T0KEN-1"
    var now: LocalDateTime = LocalDateTime.of(2026, 10, 8, 18, 0)   // время последнего замера, UTC
    val values = mutableListOf(110.0, 112.0, 115.0)   // мг/дл: история + текущее значение
    var expireNext = false     // следующий запрос с токеном получит 401
    var limitNext = false      // следующий запрос получит 429
    var connections = JSONArray().put(JSONObject().put("patientId", PATIENT).put("firstName", "Я"))
    var minVersion: String? = null    // сервер требует версию приложения не ниже этой
    var alwaysAskVersion = false      // …и просит её, даже если она уже такая (API сломался)
    var logins = 0
    val requests = mutableListOf<String>()   // журнал запросов, чтобы проверять их в тестах

    override fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse {
        check(headers["product"] == "llu.android") { "Клиент не прислал заголовок product" }
        val uri = URI(url)
        requests.add("$method ${uri.host}${uri.path}")
        val min = minVersion
        if (min != null && (alwaysAskVersion || compareVersions(headers["version"] ?: "0", min) < 0)) {
            // так отвечает LibreLinkUp, когда Abbott поднял минимальную версию приложения
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
        if (host != home) {   // аккаунт в другом регионе — сервер просит перейти туда
            val region = home.removePrefix("api-").substringBefore(".libreview.io")
            return ok(JSONObject().put("redirect", true).put("region", region))
        }
        if (body.getString("password") != password) {
            val error = JSONObject().put("message", "notAuthenticated")
            return HttpResponse(200, JSONObject().put("status", 2).put("error", error).toString())
        }
        return ok(
            JSONObject()
                .put("user", JSONObject().put("id", "user-42"))
                .put("authTicket", JSONObject().put("token", token))
        )
    }

    private fun graph(): JSONObject {
        val n = values.size
        val history = JSONArray()
        for (i in 0 until n - 1) {   // история — каждые 15 минут
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
