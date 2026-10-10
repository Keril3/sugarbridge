package com.sugarbridge.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Поддельное облако LibreLinkUp для проверок на устройстве: история за 12 часов каждые 15 минут и текущий замер. */
class FakeCloud(start: Instant = Instant.now()) : HttpTransport {
    private val history = (48 downTo 1).map { start.minusSeconds(it * 900L) to 100.0 + (it % 7) * 5 }.toMutableList()
    private var current = start.minusSeconds(30) to 115.0

    /** Пришёл новый замер: текущий уходит в историю. */
    fun tick(seconds: Long, mgDl: Double) {
        history += current
        current = current.first.plusSeconds(seconds) to mgDl
    }

    override fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResponse {
        val data: Any = when (URI(url).path) {
            "/llu/auth/login" -> JSONObject()
                .put("user", JSONObject().put("id", "e2e-user"))
                .put("authTicket", JSONObject().put("token", "E2E-TOKEN"))
            "/llu/connections" -> JSONArray().put(JSONObject().put("patientId", "p-1"))
            "/llu/connections/p-1/graph" -> JSONObject()
                .put("connection", JSONObject().put("glucoseMeasurement", item(current).put("TrendArrow", 4)))
                .put("graphData", JSONArray().apply { history.forEach { put(item(it)) } })
            else -> return HttpResponse(404, "")
        }
        return HttpResponse(200, JSONObject().put("status", 0).put("data", data).toString())
    }

    private fun item(point: Pair<Instant, Double>): JSONObject = JSONObject()
        .put("FactoryTimestamp", LocalDateTime.ofInstant(point.first, ZoneOffset.UTC).format(LIBRE_TIME))
        .put("ValueInMgPerDl", point.second)
}
