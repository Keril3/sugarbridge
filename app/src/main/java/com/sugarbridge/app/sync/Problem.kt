package com.sugarbridge.app.sync

import android.content.res.Resources
import com.sugarbridge.app.R
import com.sugarbridge.app.libre.ApiChanged
import com.sugarbridge.app.libre.LoginFailed
import com.sugarbridge.app.libre.LoginStepRequired
import com.sugarbridge.app.libre.NoConnections
import com.sugarbridge.app.libre.RateLimited
import com.sugarbridge.app.libre.ServerError
import com.sugarbridge.app.libre.WrongCredentials
import java.io.IOException

/**
 * A failure as a stable code plus details. It can be saved and later shown in whatever language
 * the app uses at that moment, unlike an exception message.
 */
data class Problem(val code: Code, val detail: String? = null) {

    enum class Code {
        WRONG_CREDENTIALS, LOGIN_FAILED, LOGIN_STEP, RATE_LIMITED, NO_CONNECTIONS, API_CHANGED, SERVER, NETWORK,
        NO_HEALTH_CONNECT, NO_PERMISSION, SIGNED_OUT, OTHER,
    }

    fun text(res: Resources): String = when (code) {
        Code.WRONG_CREDENTIALS -> res.getString(R.string.error_wrong_credentials)
        Code.LOGIN_FAILED -> res.getString(R.string.error_login, detail.orEmpty())
        Code.LOGIN_STEP -> res.getString(R.string.error_login_step, detail.orEmpty())
        Code.RATE_LIMITED -> res.getString(R.string.error_rate_limited, detail?.toIntOrNull() ?: 60)
        Code.NO_CONNECTIONS -> res.getString(R.string.error_no_connections)
        Code.API_CHANGED -> res.getString(R.string.error_api_changed, detail.orEmpty())
        Code.SERVER -> res.getString(R.string.error_server, detail?.toIntOrNull() ?: 0)
        Code.NETWORK -> res.getString(R.string.error_network) + (detail?.let { " ($it)" } ?: "")
        Code.NO_HEALTH_CONNECT -> res.getString(R.string.error_no_health_connect)
        Code.NO_PERMISSION -> res.getString(R.string.error_no_permission)
        Code.SIGNED_OUT -> res.getString(R.string.error_signed_out)
        Code.OTHER -> detail.orEmpty()
    }

    /** "CODE|detail" for SharedPreferences. */
    fun encode(): String = code.name + "|" + detail.orEmpty()

    companion object {
        fun of(e: Throwable): Problem = when (e) {
            is WrongCredentials -> Problem(Code.WRONG_CREDENTIALS)
            is LoginStepRequired -> Problem(Code.LOGIN_STEP, e.step)
            is LoginFailed -> Problem(Code.LOGIN_FAILED, e.message)
            is RateLimited -> Problem(Code.RATE_LIMITED, e.retryAfter.toString())
            is NoConnections -> Problem(Code.NO_CONNECTIONS)
            is ApiChanged -> Problem(Code.API_CHANGED, e.minimum)
            is ServerError -> Problem(Code.SERVER, e.code.toString())
            is IOException -> Problem(Code.NETWORK, e.message)
            is SyncBlocked -> Problem(e.code)
            else -> Problem(Code.OTHER, e.message ?: e.javaClass.simpleName)
        }

        /** Older versions saved the message text itself: it is shown as is. */
        fun decode(text: String): Problem {
            val code = Code.entries.firstOrNull { text.startsWith(it.name + "|") } ?: return Problem(Code.OTHER, text)
            return Problem(code, text.substringAfter('|').ifEmpty { null })
        }
    }
}

/** Sync can't even start: Health Connect is missing, there is no permission or no LibreLinkUp account. */
class SyncBlocked(val code: Problem.Code) : IllegalStateException("Sync blocked: $code")
