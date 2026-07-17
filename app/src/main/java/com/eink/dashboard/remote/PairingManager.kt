package com.eink.dashboard.remote

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class PairingPin(val value: String, val expiresAtEpochMs: Long)

@Serializable
data class TrustedSession(
    val id: String,
    val tokenHash: String,
    val clientName: String,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long,
)

sealed interface PairResult {
    data class Success(val token: String, val session: TrustedSession) : PairResult
    data object Rejected : PairResult
    data object RateLimited : PairResult
}

/** Persistent trusted-browser sessions; raw bearer tokens are never stored on the reader. */
class PairingManager(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private val prefs = context.getSharedPreferences("remote_pairing", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _sessions = MutableStateFlow(loadSessions())
    val sessions: StateFlow<List<TrustedSession>> = _sessions.asStateFlow()
    private val failures = mutableMapOf<String, MutableList<Long>>()
    private val _pin = MutableStateFlow(newPin())
    val pin: StateFlow<PairingPin> = _pin.asStateFlow()

    @Synchronized
    fun regeneratePin(): PairingPin = newPin().also { _pin.value = it }

    @Synchronized
    fun pair(pinValue: String, clientName: String, remoteIp: String): PairResult {
        val now = clock()
        val attempts = failures.getOrPut(remoteIp) { mutableListOf() }
        attempts.removeAll { now - it >= RATE_WINDOW_MS }
        if (attempts.size >= MAX_FAILURES) return PairResult.RateLimited
        val current = _pin.value
        if (now >= current.expiresAtEpochMs || !constantEquals(pinValue, current.value)) {
            attempts += now
            return PairResult.Rejected
        }

        val tokenBytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.encodeToString(tokenBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val session = TrustedSession(
            id = UUID.randomUUID().toString(),
            tokenHash = hash(token),
            clientName = clientName.trim().take(80).ifBlank { "Browser" },
            createdAtEpochMs = now,
            lastUsedAtEpochMs = now,
        )
        failures.remove(remoteIp)
        updateSessions(_sessions.value + session)
        _pin.value = newPin()
        return PairResult.Success(token, session)
    }

    @Synchronized
    fun authenticate(token: String): TrustedSession? {
        if (token.isBlank()) return null
        val candidate = hash(token)
        val session = _sessions.value.firstOrNull { constantEquals(candidate, it.tokenHash) } ?: return null
        val now = clock()
        if (now - session.lastUsedAtEpochMs >= LAST_USED_WRITE_INTERVAL_MS) {
            val updated = session.copy(lastUsedAtEpochMs = now)
            updateSessions(_sessions.value.map { if (it.id == session.id) updated else it })
            return updated
        }
        return session
    }

    @Synchronized
    fun revoke(id: String) = updateSessions(_sessions.value.filterNot { it.id == id })

    @Synchronized
    fun revokeAll() = updateSessions(emptyList())

    private fun newPin(): PairingPin {
        val value = random.nextInt(1_000_000).toString().padStart(6, '0')
        return PairingPin(value, clock() + PIN_TTL_MS)
    }

    private fun updateSessions(value: List<TrustedSession>) {
        _sessions.value = value
        prefs.edit().putString(KEY_SESSIONS, json.encodeToString(ListSerializer(TrustedSession.serializer()), value)).apply()
    }

    private fun loadSessions(): List<TrustedSession> = runCatching {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return@runCatching emptyList()
        json.decodeFromString(ListSerializer(TrustedSession.serializer()), raw)
    }.getOrDefault(emptyList())

    private fun hash(value: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()),
        Base64.NO_WRAP,
    )

    private fun constantEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    companion object {
        private const val KEY_SESSIONS = "sessions"
        private const val PIN_TTL_MS = 10 * 60_000L
        private const val RATE_WINDOW_MS = 10 * 60_000L
        private const val LAST_USED_WRITE_INTERVAL_MS = 60_000L
        private const val MAX_FAILURES = 5
    }
}
