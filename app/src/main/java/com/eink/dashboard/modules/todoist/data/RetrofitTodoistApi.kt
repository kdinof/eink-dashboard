package com.eink.dashboard.modules.todoist.data

import com.eink.dashboard.modules.todoist.security.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real [TodoistApi] over Retrofit/OkHttp against the official v1 API.
 *
 * Security-relevant choices (T04 / ADR-0002):
 * - The `Authorization: Bearer …` header is added per-request by [authInterceptor],
 *   pulling the token from the [TokenStore] each time — the token is never baked
 *   into the Retrofit instance and never held longer than a call.
 * - **No logging interceptor is installed.** Body/header logging is the usual way a
 *   bearer token leaks into logcat; we simply never attach one for this client.
 * - Transport failures are translated to [TodoistError] (never raw `HttpException`),
 *   so no response body or header can reach a log or the UI verbatim.
 */
class RetrofitTodoistApi private constructor(
    private val service: TodoistService,
) : TodoistApi {

    override suspend fun tasksByFilter(query: String, cursor: String?): TaskPage = call {
        val dto = service.tasksByFilter(query = query, cursor = cursor)
        TaskPage(tasks = dto.results.map { it.toDomain() }, nextCursor = dto.nextCursor)
    }

    override suspend fun projects(cursor: String?): ProjectPage = call {
        val dto = service.projects(cursor = cursor)
        ProjectPage(projects = dto.results.map { it.toDomain() }, nextCursor = dto.nextCursor)
    }

    override suspend fun closeTask(id: String) {
        // `close` returns 204 with no body, so we read the raw Response and map a
        // non-2xx status ourselves (Retrofit only auto-throws for body-typed calls).
        val response = try {
            service.closeTask(id)
        } catch (e: IOException) {
            throw TodoistError.Network
        }
        if (!response.isSuccessful) {
            throw errorFor(response.code(), response.headers().get("Retry-After")?.toLongOrNull())
        }
    }

    /** Runs [block], mapping every transport failure onto a [TodoistError]. */
    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: retrofit2.HttpException) {
        throw errorFor(e.code(), e.response()?.headers()?.get("Retry-After")?.toLongOrNull())
    } catch (e: IOException) {
        throw TodoistError.Network
    }

    companion object {
        const val BASE_URL = "https://api.todoist.com/api/v1/"

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /** Wires the production client with token-injecting auth. */
        fun create(tokenStore: TokenStore, baseUrl: String = BASE_URL): RetrofitTodoistApi {
            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor(tokenStore))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            return fromClient(client, baseUrl)
        }

        /** Builds the API from a pre-configured client — used by MockWebServer tests. */
        fun fromClient(client: OkHttpClient, baseUrl: String): RetrofitTodoistApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitTodoistApi(retrofit.create(TodoistService::class.java))
        }

        private fun authInterceptor(tokenStore: TokenStore): Interceptor = Interceptor { chain ->
            val builder = chain.request().newBuilder()
            tokenStore.load()?.takeIf { it.isNotBlank() }?.let { token ->
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }

        /** Maps an HTTP status (+ optional `Retry-After`) onto a [TodoistError]. */
        private fun errorFor(code: Int, retryAfterSeconds: Long?): TodoistError = when (code) {
            401, 403 -> TodoistError.Unauthorized
            429 -> TodoistError.RateLimited(retryAfterSeconds)
            in 500..599 -> TodoistError.Server(code)
            else -> TodoistError.Unexpected(code)
        }
    }
}

/** Retrofit surface. Kept private to the module; the app depends on [TodoistApi]. */
internal interface TodoistService {

    @GET("tasks/filter")
    suspend fun tasksByFilter(
        @Query("query") query: String,
        @Query("cursor") cursor: String?,
    ): TaskPageDto

    @GET("projects")
    suspend fun projects(@Query("cursor") cursor: String?): ProjectPageDto

    @POST("tasks/{id}/close")
    suspend fun closeTask(@Path("id") id: String): Response<Unit>
}
