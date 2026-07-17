package com.eink.dashboard.modules.calendar.google

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class GoogleCredential(
    val refreshToken: String,
    val brokerToken: String,
    val accessToken: String,
    val accessTokenExpiresAtEpochMs: Long,
)

/** Keystore-encrypted persistence for Google OAuth credentials. */
class GoogleCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("google_calendar_secure", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun save(value: GoogleCredential) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val packed = cipher.iv + cipher.doFinal(json.encodeToString(value).toByteArray())
        prefs.edit().putString(KEY_VALUE, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun load(): GoogleCredential? {
        val encoded = prefs.getString(KEY_VALUE, null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            }
            json.decodeFromString<GoogleCredential>(String(cipher.doFinal(packed.copyOfRange(12, packed.size))))
        }.getOrElse { clear(); null }
    }

    fun clear() {
        prefs.edit().remove(KEY_VALUE).apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "google_calendar_oauth_key"
        const val KEY_VALUE = "credential"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
