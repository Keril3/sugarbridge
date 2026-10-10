package com.sugarbridge.app

import android.content.Context
import androidx.core.content.edit

/** Один клиент LibreLinkUp на всё приложение: им пользуются и экран, и фоновая синхронизация. */
object Libre {
    private var client: LibreClient? = null

    /** Подмена сети в инструментальных тестах: там вместо LibreLinkUp отвечает поддельное облако. */
    @Volatile
    internal var transportForTests: HttpTransport? = null

    /** Клиент по сохранённому аккаунту или null, если вход ещё не выполнен. */
    @Synchronized
    fun client(context: Context): LibreClient? {
        client?.let { return it }
        val store = AccountStore(context.applicationContext)
        val account = store.load() ?: return null
        return create(context, store, account).also { client = it }
    }

    /** Email аккаунта LibreLinkUp для экрана настроек или null, если вход не выполнен. */
    fun email(context: Context): String? = AccountStore(context.applicationContext).load()?.email

    /** Проверяет логин и пароль на сервере и только тогда сохраняет их. Вызывать не из главного потока. */
    fun signIn(context: Context, email: String, password: String) {
        val store = AccountStore(context.applicationContext)
        val c = create(context, store, Account(email, password))
        c.login()   // неверный пароль → LoginFailed, и ничего не сохранится
        synchronized(this) { client = c }   // сеть идёт без блокировки, чтобы экран не ждал
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
            onLogin = { session -> store.save(account.copy(session = session)) },   // каждый новый вход сохраняем
            // запомненная версия могла устареть, если в новой сборке DEFAULT_LLU_VERSION выше
            version = listOfNotNull(prefs.getString("version", null), DEFAULT_LLU_VERSION).maxWith(::compareVersions),
            onVersion = { prefs.edit { putString("version", it) } },
        )
    }
}
