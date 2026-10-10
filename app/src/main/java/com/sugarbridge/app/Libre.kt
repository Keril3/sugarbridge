package com.sugarbridge.app

import android.content.Context

/** Один клиент LibreLinkUp на всё приложение: им пользуются и экран, и фоновая синхронизация. */
object Libre {
    private var client: LibreClient? = null

    /** Клиент по сохранённому аккаунту или null, если вход ещё не выполнен. */
    @Synchronized
    fun client(context: Context): LibreClient? {
        client?.let { return it }
        val store = AccountStore(context.applicationContext)
        val account = store.load() ?: return null
        return create(store, account).also { client = it }
    }

    /** Проверяет логин и пароль на сервере и только тогда сохраняет их. Вызывать не из главного потока. */
    fun signIn(context: Context, email: String, password: String) {
        val store = AccountStore(context.applicationContext)
        val c = create(store, Account(email, password))
        c.login()   // неверный пароль → LoginFailed, и ничего не сохранится
        synchronized(this) { client = c }   // сеть идёт без блокировки, чтобы экран не ждал
    }

    @Synchronized
    fun signOut(context: Context) {
        AccountStore(context.applicationContext).clear()
        client = null
    }

    private fun create(store: AccountStore, account: Account) = LibreClient(
        email = account.email,
        password = account.password,
        session = account.session,
        onLogin = { session -> store.save(account.copy(session = session)) },   // каждый новый вход сохраняем
    )
}
