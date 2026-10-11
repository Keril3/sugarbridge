package com.sugarbridge.app.libre

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

/** LibreLinkUp email, password and the saved session. */
data class Account(val email: String, val password: String, val session: Session? = null)

/** Keeps the account in SharedPreferences, encrypted with a key from the Android Keystore. */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    fun save(account: Account) {
        val json = JSONObject().put("email", account.email).put("password", account.password)
        account.session?.let {
            json.put("region", it.region).put("token", it.token).put("accountId", it.accountId)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())   // the cipher creates the initialization vector (IV) itself
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
            null   // couldn't decrypt (e.g. the key is gone): just sign in again
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    /** The AES key lives inside the Android Keystore and never leaves the phone. */
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
