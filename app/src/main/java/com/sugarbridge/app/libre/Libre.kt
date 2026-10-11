package com.sugarbridge.app.libre

import android.content.Context
import androidx.core.content.edit

/** One LibreLinkUp client for the whole app: both the screen and the background sync use it. */
object Libre {
    private var client: LibreClient? = null

    /** A network stand-in for instrumented tests: a fake cloud answers instead of LibreLinkUp. */
    @Volatile
    internal var transportForTests: HttpTransport? = null

    /** The client for the saved account, or null if nobody has signed in yet. */
    @Synchronized
    fun client(context: Context): LibreClient? {
        client?.let { return it }
        val store = AccountStore(context.applicationContext)
        val account = store.load() ?: return null
        return create(context, store, account).also { client = it }
    }

    /** The LibreLinkUp account email for the settings screen, or null if nobody is signed in. */
    fun email(context: Context): String? = AccountStore(context.applicationContext).load()?.email

    /** Checks the email and password on the server and only then saves them. Call it off the main thread. */
    fun signIn(context: Context, email: String, password: String) {
        val store = AccountStore(context.applicationContext)
        val c = create(context, store, Account(email, password))
        c.login()   // wrong password → LoginFailed, and nothing is saved
        synchronized(this) { client = c }   // the network call runs outside the lock so the screen doesn't wait
    }

    @Synchronized
    fun signOut(context: Context) {
        AccountStore(context.applicationContext).clear()
        client = null
    }

    private fun create(context: Context, store: AccountStore, account: Account): LibreClient {
        val prefs = context.applicationContext.getSharedPreferences("libre", Context.MODE_PRIVATE)
        return LibreClient(
            email = account.email,
            password = account.password,
            http = transportForTests ?: UrlConnectionTransport(),
            session = account.session,
            onLogin = { session -> store.save(account.copy(session = session)) },   // every new sign-in is saved
            // the remembered version may be outdated if a new build has a higher DEFAULT_LLU_VERSION
            version = listOfNotNull(prefs.getString("version", null), DEFAULT_LLU_VERSION).maxWith(::compareVersions),
            onVersion = { prefs.edit { putString("version", it) } },
        )
    }
}
