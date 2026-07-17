package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.data.TodoistError
import com.eink.dashboard.modules.todoist.security.TokenRedaction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Token redaction is a security requirement (T04): the personal token must never
 * survive into a log line, crash report, or user-facing string.
 */
class TokenRedactionTest {

    private val token = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun redact_masksEveryOccurrence() {
        val text = "GET /tasks Authorization: Bearer $token failed; retried $token"
        val redacted = TokenRedaction.redact(text, token)
        assertThat(redacted).doesNotContain(token)
        assertThat(redacted).contains(TokenRedaction.MASK)
    }

    @Test
    fun redact_nullOrEmptyToken_isNoOp() {
        val text = "nothing secret here"
        assertThat(TokenRedaction.redact(text, null)).isEqualTo(text)
        assertThat(TokenRedaction.redact(text, "")).isEqualTo(text)
    }

    @Test
    fun describe_neverEchoesTheTokenBytes() {
        val described = TokenRedaction.describe(token)
        assertThat(described).doesNotContain(token)
        assertThat(described).contains("40") // length only
        assertThat(TokenRedaction.describe(null)).isEqualTo("no token")
    }

    @Test
    fun errorMessages_areGeneric_andCarryNoSecret() {
        // The mapped transport errors are fixed strings — no response body / token.
        val errors = listOf(
            TodoistError.Unauthorized,
            TodoistError.RateLimited(retryAfterSeconds = 30),
            TodoistError.Server(code = 503),
            TodoistError.Network,
            TodoistError.Unexpected(code = 418),
        )
        errors.forEach { e ->
            assertThat(e.message).isNotEmpty()
            assertThat(e.message).doesNotContain(token)
        }
    }
}
