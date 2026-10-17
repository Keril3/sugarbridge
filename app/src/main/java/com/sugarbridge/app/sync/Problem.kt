package com.sugarbridge.app.sync

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
 * the app uses at that moment, unlike an exception message. [text] is a sentence for people; the detail
 * (the exception's own words, a server's answer) goes only to the event log.
 */
data class Problem(val code: Code, val detail: String? = null) {

    enum class Code {
        WRONG_CREDENTIALS, LOGIN_FAILED, LOGIN_STEP, RATE_LIMITED, NO_CONNECTIONS, API_CHANGED, SERVER, NETWORK,
        NO_HEALTH_CONNECT, NO_PERMISSION, SIGNED_OUT, OTHER,
        OFFLINE,   // the phone itself has no internet
    }

    /** LibreLinkUp can't be reached or fails: the internet or the cloud, nothing to do but wait. */
    val isConnection: Boolean get() = code == Code.OFFLINE || code == Code.NETWORK || code == Code.SERVER

    fun text(res: Resources): String = when (code) {
        Code.WRONG_CREDENTIALS -> res.getString(R.string.error_wrong_credentials)
        Code.LOGIN_FAILED -> res.getString(R.string.error_login)
        Code.LOGIN_STEP -> res.getString(R.string.error_login_step, detail.orEmpty())
        Code.RATE_LIMITED -> res.getString(R.string.error_rate_limited, detail?.toIntOrNull() ?: 60)
        Code.NO_CONNECTIONS -> res.getString(R.string.error_no_connections)
        Code.API_CHANGED -> res.getString(R.string.error_api_changed, detail.orEmpty())
        Code.SERVER -> res.getString(R.string.error_server, detail?.toIntOrNull() ?: 0)
        Code.NETWORK -> res.getString(R.string.error_network)
        Code.OFFLINE -> res.getString(R.string.error_offline)
        Code.NO_HEALTH_CONNECT -> res.getString(R.string.error_no_health_connect)
        Code.NO_PERMISSION -> res.getString(R.string.error_no_permission)
        Code.SIGNED_OUT -> res.getString(R.string.error_signed_out)
        Code.OTHER -> res.getString(R.string.error_other)
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

        /**
         * The same, told apart by the phone's own connection: a network failure while the phone has no
         * working internet is [Code.OFFLINE], with nothing to fix on LibreLinkUp's side.
         */
        fun of(e: Throwable, context: Context): Problem = of(e).let {
            if (it.code == Code.NETWORK && !isOnline(context)) it.copy(code = Code.OFFLINE) else it
        }

        /** Older versions saved the message text itself: it is kept as the detail. */
        fun decode(text: String): Problem {
            val code = Code.entries.firstOrNull { text.startsWith(it.name + "|") } ?: return Problem(Code.OTHER, text)
            return Problem(code, text.substringAfter('|').ifEmpty { null })
        }
    }
}

/** Whether Android sees a working internet connection: a network that has internet and has been checked. */
fun isOnline(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/** Sync can't even start: Health Connect is missing, there is no permission or no LibreLinkUp account. */
class SyncBlocked(val code: Problem.Code) : IllegalStateException("Sync blocked: $code")
