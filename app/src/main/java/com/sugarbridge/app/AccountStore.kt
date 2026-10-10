package com.sugarbridge.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Логин, пароль и сохранённая сессия LibreLinkUp. */
data class Account(val email: String, val password: String, val session: Session? = null)

/** Хранит аккаунт в SharedPreferences, зашифрованным ключом из Android Keystore. */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    fun save(account: Account) {
        val json = JSONObject().put("email", account.email).put("password", account.password)
        account.session?.let {
            json.put("region", it.region).put("token", it.token).put("accountId", it.accountId)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())   // вектор инициализации (IV) шифр создаёт сам
        val encrypted = cipher.doFinal(json.toString().toByteArray())
        prefs.edit {
            putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
    }

    fun load(): Account? {
        val iv = prefs.getString("iv", null) ?: return null
        val data = prefs.getString("data", null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            val json = JSONObject(String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP))))
            val session = if (json.has("token")) {
                Session(json.getString("region"), json.getString("token"), json.getString("accountId"))
            } else {
                null
            }
            Account(json.getString("email"), json.getString("password"), session)
        } catch (e: Exception) {
            null   // расшифровать не вышло (например, ключ пропал) — просто войдём заново
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    /** Ключ AES живёт внутри Android Keystore и никогда не покидает телефон. */
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "libre-account"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
