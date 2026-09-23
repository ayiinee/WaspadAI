package id.waspadai.app.feature.auth.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class RememberedCredentials(
    val email: String,
    val password: String,
)

/** Stores the opt-in login autofill values encrypted with an Android Keystore key. */
class RememberedCredentialsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): RememberedCredentials? = runCatching {
        val encryptedPayload = preferences.getString(KEY_PAYLOAD, null) ?: return null
        val initializationVector = preferences.getString(KEY_INITIALIZATION_VECTOR, null) ?: return null
        val plaintext = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(
                    GCM_TAG_LENGTH_BITS,
                    Base64.decode(initializationVector, Base64.NO_WRAP),
                ),
            )
        }.doFinal(Base64.decode(encryptedPayload, Base64.NO_WRAP))
        val payload = JSONObject(String(plaintext, StandardCharsets.UTF_8))
        RememberedCredentials(
            email = payload.getString("email"),
            password = payload.getString("password"),
        )
    }.getOrElse {
        clear()
        null
    }

    fun save(email: String, password: String) {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, encryptionKey())
        }
        val plaintext = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val encryptedPayload = cipher.doFinal(plaintext)
        preferences.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(encryptedPayload, Base64.NO_WRAP))
            .putString(
                KEY_INITIALIZATION_VECTOR,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            )
            .apply()
    }

    fun clear() {
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
                .build(),
        )
    }.generateKey()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val PREFERENCES_NAME = "remembered_credentials"
        const val KEY_ALIAS = "waspadai.remembered_credentials"
        const val KEY_PAYLOAD = "encrypted_payload"
        const val KEY_INITIALIZATION_VECTOR = "initialization_vector"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
