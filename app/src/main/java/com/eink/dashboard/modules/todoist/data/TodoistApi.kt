package com.eink.dashboard.modules.todoist.data

import com.eink.dashboard.modules.todoist.model.TodoistProject
import com.eink.dashboard.modules.todoist.model.TodoistTask

/**
 * The seam over the Todoist HTTP API (official **v1**, base
 * `https://api.todoist.com/api/v1`). The rest of the module depends only on this
 * interface and the domain types it returns — never on Retrofit or the wire JSON —
 * so repository logic is unit-tested with an in-memory fake and the real client is
 * tested in isolation with MockWebServer.
 *
 * Verified against the current official docs (see `docs/adr/0002-todoist-auth.md`):
 * - `GET /api/v1/tasks/filter?query=…&cursor=…` — cursor-paginated active tasks.
 * - `POST /api/v1/tasks/{id}/close` — completes a task; for a recurring task the
 *   server advances it to the next occurrence instead of finishing it.
 * - `GET /api/v1/projects?cursor=…` — cursor-paginated projects (for names).
 *
 * Implementations translate transport outcomes into [TodoistError] rather than
 * leaking `HttpException`/`IOException`, so callers handle 401/403/429/5xx/offline
 * uniformly. No method takes or returns the token — auth is applied by the client's
 * interceptor from the [com.eink.dashboard.modules.todoist.security.TokenStore].
 */
interface TodoistApi {

    /** One page of active tasks matching [query]; pass [cursor] to page forward. */
    suspend fun tasksByFilter(query: String, cursor: String?): TaskPage

    /** One page of projects; pass [cursor] to page forward. */
    suspend fun projects(cursor: String?): ProjectPage

    /**
     * Complete the task with [id]. Returns normally on success (HTTP 2xx). For a
     * recurring task this advances the due date server-side; the next refresh will
     * surface the task again with its new date.
     */
    suspend fun closeTask(id: String)
}

/** A cursor-paginated page of tasks. [nextCursor] is `null` on the last page. */
data class TaskPage(val tasks: List<TodoistTask>, val nextCursor: String?)

/** A cursor-paginated page of projects. [nextCursor] is `null` on the last page. */
data class ProjectPage(val projects: List<TodoistProject>, val nextCursor: String?)

/**
 * Transport-level failures mapped away from Retrofit/OkHttp specifics. Messages are
 * fixed, generic strings — a token or response body is never embedded.
 */
sealed class TodoistError(message: String) : Exception(message) {

    /** 401/403 — the stored token is missing, invalid, or lacks access. */
    data object Unauthorized : TodoistError("Todoist rejected the token")

    /** 429 — rate limited. [retryAfterSeconds] echoes the `Retry-After` header if set. */
    data class RateLimited(val retryAfterSeconds: Long?) : TodoistError("Rate limited")

    /** Any 5xx — a transient server-side problem. */
    data class Server(val code: Int) : TodoistError("Todoist server error")

    /** No connectivity / timeout / DNS — retryable. */
    data object Network : TodoistError("No network")

    /** Anything else unexpected (bad 4xx, parse failure). */
    data class Unexpected(val code: Int?) : TodoistError("Unexpected error")

    /** Whether re-sending later is sensible (offline / rate-limited / server hiccup). */
    val isRetryable: Boolean
        get() = this is Network || this is RateLimited || this is Server
}
