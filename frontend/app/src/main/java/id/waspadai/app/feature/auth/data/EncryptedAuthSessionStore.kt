package id.waspadai.app.feature.auth.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

data class StoredAuthSession(
    val accessToken: String,
    val refreshToken: String,
)

interface AuthSessionStore {
    fun load(): StoredAuthSession?
    fun save(session: StoredAuthSession)
    fun clear()
}

object NoOpAuthSessionStore : AuthSessionStore {
    override fun load(): StoredAuthSession? = null
    override fun save(session: StoredAuthSession) = Unit
    override fun clear() = Unit
}

/** Keeps the refreshable Supabase session available to system-triggered entry points. */
class EncryptedAuthSessionStore(context: Context) : AuthSessionStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): StoredAuthSession? = runCatching {
        val encrypted = preferences.getString(KEY_PAYLOAD, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        val plaintext = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP)),
            )
        }.doFinal(Base64.decode(encrypted, Base64.NO_WRAP))
        val payload = JSONObject(String(plaintext, StandardCharsets.UTF_8))
        StoredAuthSession(
            accessToken = payload.getString("access_token"),
            refreshToken = payload.optString("refresh_token"),
        ).takeIf { it.accessToken.isNotBlank() }
    }.getOrElse {
        clear()
        null
    }

    override fun save(session: StoredAuthSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, encryptionKey())
        }
        val plaintext = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val encrypted = cipher.doFinal(plaintext)
        preferences.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: createEncryptionKey()
    }

    private fun createEncryptionKey(): SecretKey = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        ANDROID_KEYSTORE,
    ).apply {
        init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
    }.generateKey()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "waspadai.auth.session"
        const val PREFERENCES_NAME = "waspadai_auth_session"
        const val KEY_PAYLOAD = "encrypted_payload"
        const val KEY_IV = "initialization_vector"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
    }
}
