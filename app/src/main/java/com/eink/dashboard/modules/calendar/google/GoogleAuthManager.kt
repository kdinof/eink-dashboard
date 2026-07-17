package com.eink.dashboard.modules.calendar.google

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

@Serializable private data class CreateHandoff(val id: String, val secretHash: String, val returnUrl: String)
@Serializable private data class HandoffCreated(val authorizationUrl: String, val expiresIn: Int)
@Serializable private data class RedeemHandoff(val secret: String)
@Serializable private data class Redeemed(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val brokerToken: String,
)
@Serializable private data class RefreshRequest(val refreshToken: String, val brokerToken: String)
@Serializable private data class Refreshed(val accessToken: String, val expiresIn: Int)

data class GoogleConnectStart(val handoffId: String, val authorizationUrl: String)
fun interface GoogleAccessTokenProvider { fun accessToken(): String }

/** OAuth handoff + access-token lifecycle. Calendar data never passes through the broker. */
class GoogleAuthManager(
    private val brokerUrl: String,
    private val store: GoogleCredentialStore,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val clock: () -> Long = System::currentTimeMillis,
) : GoogleAccessTokenProvider {
    private val pendingSecrets = ConcurrentHashMap<String, String>()
    val isConfigured: Boolean get() = brokerUrl.startsWith("https://")
    val isConnected: Boolean get() = store.load() != null

    fun begin(returnUrl: String): GoogleConnectStart {
        check(isConfigured) { "Google OAuth broker is not configured" }
        val id = random(32)
        val secret = random(32)
        val secretHash = urlSafe(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()))
        val response = post("handoffs", json.encodeToString(CreateHandoff(id, secretHash, returnUrl)))
        val created = json.decodeFromString<HandoffCreated>(response)
        pendingSecrets[id] = secret
        return GoogleConnectStart(id, created.authorizationUrl)
    }

    fun complete(handoffId: String) {
        val secret = pendingSecrets[handoffId] ?: error("OAuth handoff expired on the reader")
        var lastFailure: Throwable? = null
        var response: String? = null
        for (attempt in 0..2) {
            runCatching {
                post("handoffs/$handoffId/redeem", json.encodeToString(RedeemHandoff(secret)))
            }.onSuccess {
                response = it
            }.onFailure {
                lastFailure = it
                if (attempt < 2) Thread.sleep(750)
            }
            if (response != null) break
        }
        val redeemedResponse = response ?: throw checkNotNull(lastFailure)
        pendingSecrets.remove(handoffId)
        val redeemed = json.decodeFromString<Redeemed>(redeemedResponse)
        store.save(
            GoogleCredential(
                refreshToken = redeemed.refreshToken,
                brokerToken = redeemed.brokerToken,
                accessToken = redeemed.accessToken,
                accessTokenExpiresAtEpochMs = clock() + redeemed.expiresIn * 1_000L - 60_000L,
            ),
        )
    }

    @Synchronized
    override fun accessToken(): String {
        val current = store.load() ?: error("Google Calendar is not connected")
        if (current.accessTokenExpiresAtEpochMs > clock()) return current.accessToken
        val response = post(
            "oauth/refresh",
            json.encodeToString(RefreshRequest(current.refreshToken, current.brokerToken)),
        )
        val refreshed = json.decodeFromString<Refreshed>(response)
        store.save(
            current.copy(
                accessToken = refreshed.accessToken,
                accessTokenExpiresAtEpochMs = clock() + refreshed.expiresIn * 1_000L - 60_000L,
            ),
        )
        return refreshed.accessToken
    }

    fun disconnect() {
        store.load()?.let { credential ->
            runCatching {
                post("oauth/revoke", json.encodeToString(RefreshRequest(credential.refreshToken, credential.brokerToken)))
            }
        }
        store.clear()
        pendingSecrets.clear()
    }

    private fun post(path: String, body: String): String {
        val request = Request.Builder()
            .url(brokerUrl.trimEnd('/') + "/" + path)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        return client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Google OAuth broker rejected the request (${response.code})")
            responseBody
        }
    }

    private fun random(bytes: Int): String = urlSafe(ByteArray(bytes).also(SecureRandom()::nextBytes))
    private fun urlSafe(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private companion object { val JSON_MEDIA = "application/json".toMediaType() }
}
