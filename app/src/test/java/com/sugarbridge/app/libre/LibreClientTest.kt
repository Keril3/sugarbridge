package com.sugarbridge.app.libre

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class LibreClientTest {

    private fun client(fake: HttpTransport, password: String = "secret", session: Session? = null) =
        LibreClient("me@example.com", password, http = fake, session = session)

    // --- шаг 3: вход ---

    @Test
    fun loginCreatesSessionWithTokenAndHashedUserId() {
        val c = client(FakeLibreView())
        c.login()
        assertEquals(Session("ru", "T0KEN-1", sha256("user-42")), c.session)
    }

    @Test
    fun sha256IsLowercaseHex() {
        // это значение можно сверить с любым онлайн-калькулятором SHA-256
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", sha256("hello"))
    }

    @Test
    fun loginFollowsRegionRedirect() {
        val fake = FakeLibreView(home = "api-eu.libreview.io")
        val c = client(fake)
        c.login()
        assertEquals("https://api-eu.libreview.io", c.base)
        assertEquals("eu", c.session?.region)
        assertEquals(2, fake.logins)   // первый вход — в ru, второй — уже в eu
    }

    @Test
    fun wrongPasswordGivesLoginFailed() {
        val e = assertThrows(LoginFailed::class.java) { client(FakeLibreView(), password = "wrong").login() }
        assertTrue(e.message!!.contains("notAuthenticated"))
    }

    @Test
    fun newSessionIsReportedToApp() {
        val saved = mutableListOf<Session>()
        val c = LibreClient("me@example.com", "secret", http = FakeLibreView(), onLogin = { saved.add(it) })
        c.login()
        assertEquals(listOf(c.session), saved)
    }

    // --- шаг 4: замеры ---

    @Test
    fun readingsAreParsedAndSorted() {
        val readings = client(FakeLibreView()).readings()
        assertEquals(listOf(110.0, 112.0, 115.0), readings.map { it.mgDl })
        assertEquals(Instant.parse("2026-10-08T18:00:00Z"), readings.last().time)
        assertEquals(4, readings.last().trend)
        assertEquals(6.4, readings.last().mmol, 0.0)
    }

    @Test
    fun savedSessionIsUsedWithoutLogin() {
        val fake = FakeLibreView()
        val c = client(fake, session = Session("ru", "T0KEN-1", sha256("user-42")))
        c.readings()
        assertEquals(0, fake.logins)
    }

    @Test
    fun patientIdIsAskedOnlyOnce() {
        val fake = FakeLibreView()
        val c = client(fake)
        c.readings()
        c.readings()
        assertEquals(1, fake.requests.count { it.endsWith("/llu/connections") })
    }

    @Test
    fun expiredTokenLeadsToOneNewLogin() {
        val fake = FakeLibreView()
        val saved = mutableListOf<Session>()
        val c = LibreClient("me@example.com", "secret", http = fake, onLogin = { saved.add(it) })
        c.readings()
        fake.expireNext = true
        fake.values.add(118.0)   // пришёл новый замер
        val readings = c.readings()
        assertEquals(118.0, readings.last().mgDl, 0.0)
        assertEquals(2, fake.logins)
        assertEquals("T0KEN-2", saved.last().token)   // новую сессию приложение сохранит
    }

    @Test
    fun rateLimitReportsRetryAfter() {
        val fake = FakeLibreView()
        val c = client(fake)
        c.login()
        fake.limitNext = true
        val e = assertThrows(RateLimited::class.java) { c.readings() }
        assertEquals(120, e.retryAfter)
    }

    @Test
    fun noConnectionsGivesClearError() {
        val fake = FakeLibreView()
        fake.connections = JSONArray()
        val e = assertThrows(LibreError::class.java) { client(fake).readings() }
        assertTrue(e.message!!.contains("нет подключений"))
    }

    // «Попробуйте сами» из шага 4: один ответ 500 → IOException
    @Test
    fun serverErrorGivesIOException() {
        val fake = FakeLibreView()
        var failed = false
        val flaky = HttpTransport { m, u, h, b ->
            if (!failed) {
                failed = true
                HttpResponse(500, "")
            } else {
                fake.send(m, u, h, b)
            }
        }
        val c = client(flaky, session = Session("ru", "T0KEN-1", sha256("user-42")))
        assertThrows(IOException::class.java) { c.readings() }
        assertEquals(115.0, c.readings().last().mgDl, 0.0)   // следующий запрос уже проходит
    }

    // --- Abbott поднял минимальную версию приложения ---

    @Test
    fun loginUpgradesVersionWhenServerAsks() {
        val fake = FakeLibreView()
        fake.minVersion = "4.17.0"
        val remembered = mutableListOf<String>()
        val c = LibreClient("me@example.com", "secret", http = fake, onVersion = { remembered.add(it) })
        c.login()
        assertEquals("4.17.0", c.version)
        assertEquals(listOf("4.17.0"), remembered)   // приложение запомнит и в следующий раз начнёт с неё
        assertEquals(Session("ru", "T0KEN-1", sha256("user-42")), c.session)
    }

    @Test
    fun readingsSurviveVersionBumpWithSavedSession() {
        val fake = FakeLibreView()
        val c = client(fake, session = Session("ru", "T0KEN-1", sha256("user-42")))
        fake.minVersion = "5.0.0"
        assertEquals(115.0, c.readings().last().mgDl, 0.0)
        assertEquals("5.0.0", c.version)
        assertEquals(0, fake.logins)
    }

    @Test
    fun brokenVersionCheckIsNotTreatedAsWrongPassword() {
        // если сервер просит ту же версию снова и снова, это не «неверный пароль»:
        // фоновая задача не должна выкидывать из аккаунта
        val fake = FakeLibreView()
        fake.minVersion = DEFAULT_LLU_VERSION
        fake.alwaysAskVersion = true
        val e = assertThrows(LibreError::class.java) { client(fake).login() }
        assertFalse(e is LoginFailed)
    }

    @Test
    fun versionsAreComparedNumerically() {
        assertTrue(compareVersions("4.16.0", "4.9.9") > 0)
        assertTrue(compareVersions("4.16", "4.16.1") < 0)
        assertEquals(0, compareVersions("4.16.0", "4.16"))
    }
}
