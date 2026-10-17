package com.sugarbridge.app.sync

import com.sugarbridge.app.libre.ApiChanged
import com.sugarbridge.app.libre.LoginFailed
import com.sugarbridge.app.libre.LoginStepRequired
import com.sugarbridge.app.libre.NoConnections
import com.sugarbridge.app.libre.RateLimited
import com.sugarbridge.app.libre.ServerError
import com.sugarbridge.app.libre.WrongCredentials
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ProblemTest {

    @Test
    fun exceptionsBecomeCodesWithDetails() {
        assertEquals(Problem(Problem.Code.WRONG_CREDENTIALS), Problem.of(WrongCredentials("incorrect username/password")))
        assertEquals(Problem(Problem.Code.LOGIN_STEP, "tou"), Problem.of(LoginStepRequired("tou")))
        assertEquals(Problem(Problem.Code.LOGIN_FAILED, "locked"), Problem.of(LoginFailed("locked")))
        assertEquals(Problem(Problem.Code.RATE_LIMITED, "120"), Problem.of(RateLimited(120)))
        assertEquals(Problem(Problem.Code.NO_CONNECTIONS), Problem.of(NoConnections()))
        assertEquals(Problem(Problem.Code.API_CHANGED, "4.17.0"), Problem.of(ApiChanged("4.17.0")))
        assertEquals(Problem(Problem.Code.SERVER, "502"), Problem.of(ServerError(502)))
        assertEquals(Problem(Problem.Code.NETWORK, "timeout"), Problem.of(IOException("timeout")))
        assertEquals(Problem(Problem.Code.NO_PERMISSION), Problem.of(SyncBlocked(Problem.Code.NO_PERMISSION)))
        assertEquals(Problem(Problem.Code.OTHER, "boom"), Problem.of(IllegalArgumentException("boom")))
    }

    @Test
    fun onlyTheCloudOutOfReachCountsAsAConnectionProblem() {
        val connection = setOf(Problem.Code.OFFLINE, Problem.Code.NETWORK, Problem.Code.SERVER)
        Problem.Code.entries.forEach { assertEquals(it.name, it in connection, Problem(it).isConnection) }
    }

    @Test
    fun savedProblemComesBackTheSame() {
        listOf(
            Problem(Problem.Code.RATE_LIMITED, "60"),
            Problem(Problem.Code.SIGNED_OUT),
            Problem(Problem.Code.OTHER, "text with | a bar"),
        ).forEach { assertEquals(it, Problem.decode(it.encode())) }
    }

    @Test
    fun messageSavedByAnOlderVersionIsShownAsIs() {
        val legacy = "Нет разрешения на запись сахара в Health Connect"
        assertEquals(Problem(Problem.Code.OTHER, legacy), Problem.decode(legacy))
    }
}
