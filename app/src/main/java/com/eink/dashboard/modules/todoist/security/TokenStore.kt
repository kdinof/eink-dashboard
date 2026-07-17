package com.eink.dashboard.modules.todoist.security

/**
 * Storage for the Todoist personal API token. A one-method-per-op interface (not a
 * concrete class) so the module's repository/logic can be unit-tested with an
 * in-memory fake and no device — the real Android Keystore path is exercised on the
 * device (see `docs/adr/0002-todoist-auth.md`).
 *
 * Security contract for every implementation:
 * - The token is written to disk **only** in a Keystore-encrypted form.
 * - The token is never logged, never placed in Room, and never returned in any
 *   user-facing error string (see [TokenRedaction]).
 * - [load] returns the plaintext transiently in memory for building the request
 *   `Authorization` header; callers must not persist or log the returned value.
 */
interface TokenStore {

    /** Persist [token] encrypted at rest. An empty/blank token is treated as clear. */
    fun save(token: String)

    /** The decrypted token, or `null` if none is stored (or it can't be decrypted). */
    fun load(): String?

    /** Remove any stored token and, where possible, the backing Keystore key. */
    fun clear()

    /** Cheap check used by the UI/module to gate on "is a token configured". */
    fun hasToken(): Boolean = !load().isNullOrBlank()
}

/**
 * Removes a secret from arbitrary text before it can reach a log line, a crash
 * report, or the UI. Used defensively when surfacing errors: even though we never
 * intentionally log the token, an exception message from a lower layer could echo a
 * request. [redact] guarantees the raw token never leaves the process.
 */
object TokenRedaction {

    const val MASK: String = "***redacted***"

    /** Replace every occurrence of [token] in [text] with [MASK]. No-op if blank. */
    fun redact(text: String, token: String?): String =
        if (token.isNullOrEmpty()) text else text.replace(token, MASK)

    /** A safe one-line preview of a token for diagnostics: length only, never bytes. */
    fun describe(token: String?): String =
        if (token.isNullOrEmpty()) "no token" else "token set (${token.length} chars)"
}
