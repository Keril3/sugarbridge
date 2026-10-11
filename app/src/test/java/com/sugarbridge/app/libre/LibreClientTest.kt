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

    // the server order as on a phone in Russia; nothing depends on the country of the computer running the tests
    private val ruFirst = listOf("ru", "us")
    private val worldFirst = listOf("us", "ru")

    private fun client(
        fake: HttpTransport,
        password: String = "secret",
        session: Session? = null,
        startRegions: List<String> = ruFirst,
    ) = LibreClient("me@example.com", password, http = fake, session = session, startRegions = startRegions)

    // --- signing in ---

    @Test
    fun loginCreatesSessionWithTokenAndHashedUserId() {
        val c = client(FakeLibreView())
        c.login()
        assertEquals(Session("ru", "T0KEN-1", sha256("user-42")), c.session)
    }

    @Test
    fun sha256IsLowercaseHex() {
        // this value can be checked with any online SHA-256 calculator
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", sha256("hello"))
    }

    @Test
    fun loginFollowsRegionRedirect() {
        // a phone outside Russia: the world server itself tells which region the account is in
        val fake = FakeLibreView(home = "api-eu.libreview.io")
        val c = client(fake, startRegions = worldFirst)
        c.login()
        assertEquals("https://api-eu.libreview.io", c.base)
        assertEquals("eu", c.session?.region)
        assertEquals(listOf("api.libreview.io", "api-eu.libreview.io"), fake.loginHosts)
    }

    @Test
    fun wrongPasswordGivesWrongCredentials() {
        val e = assertThrows(WrongCredentials::class.java) { client(FakeLibreView(), password = "wrong").login() }
        assertEquals("incorrect username/password", e.serverMessage)
    }

    @Test
    fun newSessionIsReportedToApp() {
        val saved = mutableListOf<Session>()
        val c = LibreClient("me@example.com", "secret", http = FakeLibreView(), onLogin = { saved.add(it) }, startRegions = ruFirst)
        c.login()
        assertEquals(listOf(c.session), saved)
    }

    // --- readings ---

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
        val c = LibreClient("me@example.com", "secret", http = fake, onLogin = { saved.add(it) }, startRegions = ruFirst)
        c.readings()
        fake.expireNext = true
        fake.values.add(118.0)   // a new reading arrived
        val readings = c.readings()
        assertEquals(118.0, readings.last().mgDl, 0.0)
        assertEquals(2, fake.logins)
        assertEquals("T0KEN-2", saved.last().token)   // the app will save the new session
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
        assertThrows(NoConnections::class.java) { client(fake).readings() }
    }

    // a single 500 answer → ServerError, an IOException
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
        val e = assertThrows(IOException::class.java) { c.readings() }
        assertEquals(500, (e as ServerError).code)
        assertEquals(115.0, c.readings().last().mgDl, 0.0)   // the next request goes through
    }

    // --- Abbott raised the minimum app version ---

    @Test
    fun loginUpgradesVersionWhenServerAsks() {
        val fake = FakeLibreView()
        fake.minVersion = "4.17.0"
        val remembered = mutableListOf<String>()
        val c = LibreClient("me@example.com", "secret", http = fake, onVersion = { remembered.add(it) }, startRegions = ruFirst)
        c.login()
        assertEquals("4.17.0", c.version)
        assertEquals(listOf("4.17.0"), remembered)   // the app remembers it and starts with it next time
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
        // if the server asks for the same version again and again, it is not a "wrong password":
        // the background job must not sign the user out
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

    // --- the Russian and the world Abbott servers ---

    @Test
    fun foreignAccountOnRussianPhone() {
        // the Russian server doesn't know the account → the world one → a redirect to the account's region
        val fake = FakeLibreView(home = "api-de.libreview.io")
        val c = client(fake, startRegions = ruFirst)
        c.login()
        assertEquals("de", c.session?.region)
        assertEquals(listOf("api.libreview.ru", "api.libreview.io", "api-de.libreview.io"), fake.loginHosts)
        assertEquals(listOf(110.0, 112.0, 115.0), c.readings().map { it.mgDl })
    }

    @Test
    fun russianAccountOnForeignPhone() {
        val fake = FakeLibreView(home = "api.libreview.ru")
        val c = client(fake, startRegions = worldFirst)
        c.login()
        assertEquals("ru", c.session?.region)
        assertEquals("https://api.libreview.ru", c.base)
        assertEquals(listOf("api.libreview.io", "api.libreview.ru"), fake.loginHosts)
    }

    @Test
    fun wrongPasswordIsTriedOnceOnEachServer() {
        val fake = FakeLibreView(home = "api-eu.libreview.io")
        assertThrows(WrongCredentials::class.java) { client(fake, password = "wrong").login() }
        assertEquals(listOf("api.libreview.ru", "api.libreview.io", "api-eu.libreview.io"), fake.loginHosts)
    }

    @Test
    fun termsStepStopsAtTheRightServer() {
        // the account was found, but the terms must be accepted: another server won't help, no second attempt
        val fake = FakeLibreView(home = "api.libreview.ru").apply { step = "tou" }
        val e = assertThrows(LoginStepRequired::class.java) { client(fake).login() }
        assertEquals("tou", e.step)
        assertEquals(1, fake.logins)
    }

    @Test
    fun unreachableServerIsSkipped() {
        val fake = FakeLibreView(home = "api-eu.libreview.io").apply { unreachable = setOf("api.libreview.ru") }
        val c = client(fake, startRegions = ruFirst)
        c.login()
        assertEquals("eu", c.session?.region)
    }

    @Test
    fun knownRegionIsNotGuessedAgain() {
        // the region is known from the last sign-in: signing in again (the password changed) leaves the other servers alone
        val fake = FakeLibreView(home = "api-eu.libreview.io")
        val c = client(fake, password = "changed", session = Session("eu", "OLD", sha256("user-42")))
        assertThrows(LoginFailed::class.java) { c.login() }
        assertEquals(listOf("api-eu.libreview.io"), fake.loginHosts)
    }

    @Test
    fun startServerDependsOnPhoneCountry() {
        assertEquals(listOf("ru", "us"), startRegionsFor("RU"))
        assertEquals(listOf("us", "ru"), startRegionsFor("DE"))
        assertEquals(listOf("us", "ru"), startRegionsFor(""))
    }
}
