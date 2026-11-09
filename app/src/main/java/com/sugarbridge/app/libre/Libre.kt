package com.sugarbridge.app.libre

import android.content.Context
import androidx.core.content.edit

/** One LibreLinkUp client for the whole app: both the screen and the background sync use it. */
object Libre {
    private var client: LibreClient? = null
    private var signOuts = 0   // a request from before a sign-out must not save the account again

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

    /**
     * The LibreLinkUp account email for the settings screen, or null if nobody is signed in. Asked on every return
     * to the screen: from the client when there is one, since decrypting the account takes the Keystore a moment.
     */
    @Synchronized
    fun email(context: Context): String? = client?.email ?: AccountStore(context.applicationContext).load()?.email

    /** Checks the email and password on the server and only then saves them. Call it off the main thread. */
    fun signIn(context: Context, email: String, password: String) {
        val store = AccountStore(context.applicationContext)
        val era = synchronized(this) { signOuts }
        val c = create(context, store, Account(email, password))
        c.login()   // wrong password → LoginFailed, and nothing is saved
        // the network call runs outside the lock so the screen doesn't wait
        synchronized(this) { if (era == signOuts) client = c }
    }

    @Synchronized
    fun signOut(context: Context) {
        signOuts++
        AccountStore(context.applicationContext).clear()
        client = null
    }

    @Synchronized
    private fun create(context: Context, store: AccountStore, account: Account): LibreClient {
        val prefs = context.applicationContext.getSharedPreferences("libre", Context.MODE_PRIVATE)
        val era = signOuts
        return LibreClient(
            email = account.email,
            password = account.password,
            http = transportForTests ?: UrlConnectionTransport(),
            session = account.session,
            // every new sign-in is saved, unless the person signed out while it ran: the password must stay gone
            onLogin = { session -> synchronized(this) { if (era == signOuts) store.save(account.copy(session = session)) } },
            // the remembered version may be outdated if a new build has a higher DEFAULT_LLU_VERSION
            version = listOfNotNull(prefs.getString("version", null), DEFAULT_LLU_VERSION).maxWith(::compareVersions),
            onVersion = { prefs.edit { putString("version", it) } },
        )
    }
}
