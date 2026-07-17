package com.eink.dashboard.modules.todoist.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [TokenStore] backed by the Android Keystore (ADR-0002).
 *
 * The AES-256-GCM key is generated inside the `AndroidKeyStore` provider and is
 * **non-exportable**: the raw key bytes never enter app memory or touch disk. Only
 * the ciphertext (IV ‖ encrypted token, Base64) is persisted, in a private
 * `SharedPreferences` file. The plaintext token exists only transiently while
 * building a request header. This satisfies the T04 requirement to keep the token
 * out of logs, Room, and plain storage while still surviving process death.
 *
 * We deliberately implement the small Keystore wrapper directly instead of adding
 * the (now-deprecated) `androidx.security:security-crypto` dependency — it keeps the
 * pinned dependency set unchanged and the crypto surface auditable in one file.
 */
class KeystoreTokenStore(context: Context) : TokenStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun save(token: String) {
        if (token.isBlank()) {
            clear()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        // Persist IV ‖ ciphertext; the IV is public and required to decrypt.
        val packed = cipher.iv + ciphertext
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    override fun load(): String? {
        val packed = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val bytes = Base64.decode(packed, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = bytes.copyOfRange(GCM_IV_BYTES, bytes.size)
            val key = existingKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (t: Throwable) {
            // Key invalidated (e.g. lock-screen change) or corrupt blob → treat as
            // "no usable token" and drop it, forcing a clean re-entry. Never surface
            // the exception text (could echo request bytes).
            clear()
            null
        }
    }

    override fun clear() {
        prefs.edit().remove(KEY_CIPHERTEXT).apply()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey = existingKey() ?: createKey()

    private fun existingKey(): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // No setUserAuthenticationRequired: the dashboard is an always-on kiosk
            // with no unlock; the key is still hardware-backed and app-scoped.
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "todoist_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS = "todoist_secure"
        const val KEY_CIPHERTEXT = "token_ciphertext"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
