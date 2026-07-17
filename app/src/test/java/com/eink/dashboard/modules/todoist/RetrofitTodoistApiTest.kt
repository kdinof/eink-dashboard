package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.data.RetrofitTodoistApi
import com.eink.dashboard.modules.todoist.data.TodoistError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The real Retrofit client, exercised against MockWebServer — **no real token, no
 * real network**. Verifies wire parsing, the `Authorization: Bearer` header from the
 * token store, and the HTTP-status → [TodoistError] mapping (401/403/429/5xx).
 */
class RetrofitTodoistApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(token: String? = "test-token") =
        RetrofitTodoistApi.create(FakeTokenStore(token), server.url("/api/v1/").toString())

    @Test
    fun tasksByFilter_parsesPage_andSendsBearerHeader(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"results":[
                  {"id":"1","content":"Buy milk","project_id":"P","priority":4,
                   "labels":["home"],"parent_id":null,"child_order":2,
                   "due":{"date":"2026-07-17","is_recurring":false,"string":"Jul 17"}}
                ],"next_cursor":"CURSOR2"}
                """.trimIndent(),
            ),
        )

        val page = api().tasksByFilter("overdue | today", cursor = null)

        assertThat(page.nextCursor).isEqualTo("CURSOR2")
        val task = page.tasks.single()
        assertThat(task.id).isEqualTo("1")
        assertThat(task.content).isEqualTo("Buy milk")
        assertThat(task.priority).isEqualTo(4)
        assertThat(task.labels).containsExactly("home")
        assertThat(task.order).isEqualTo(2)
        assertThat(task.due?.date.toString()).isEqualTo("2026-07-17")

        val recorded = server.takeRequest()
        assertThat(recorded.path).contains("/tasks/filter")
        assertThat(recorded.path).contains("query=overdue")
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-token")
    }

    @Test
    fun unauthorized_isMappedTo401Error(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val error = runCatching { api().tasksByFilter("today", null) }.exceptionOrNull()
        assertThat(error).isEqualTo(TodoistError.Unauthorized)
    }

    @Test
    fun forbidden_isMappedToUnauthorized(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        val error = runCatching { api().projects(null) }.exceptionOrNull()
        assertThat(error).isEqualTo(TodoistError.Unauthorized)
    }

    @Test
    fun rateLimited_carriesRetryAfter(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "42").setBody("{}"),
        )
        val error = runCatching { api().tasksByFilter("today", null) }.exceptionOrNull()
        assertThat(error).isInstanceOf(TodoistError.RateLimited::class.java)
        assertThat((error as TodoistError.RateLimited).retryAfterSeconds).isEqualTo(42L)
    }

    @Test
    fun serverError_isMapped(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        val error = runCatching { api().tasksByFilter("today", null) }.exceptionOrNull()
        assertThat(error).isInstanceOf(TodoistError.Server::class.java)
        assertThat((error as TodoistError.Server).code).isEqualTo(503)
    }

    @Test
    fun closeTask_success_hitsCloseEndpoint(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api().closeTask("99")
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/v1/tasks/99/close")
    }

    @Test
    fun closeTask_errorStatus_isMapped(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        val error = runCatching { api().closeTask("99") }.exceptionOrNull()
        assertThat(error).isEqualTo(TodoistError.Unauthorized)
    }

    @Test
    fun projects_parsePage(): Unit = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"results":[{"id":"P","name":"Work"}],"next_cursor":null}"""),
        )
        val page = api().projects(null)
        assertThat(page.projects.single().name).isEqualTo("Work")
        assertThat(page.nextCursor).isNull()
    }
}
