package com.eink.dashboard.modules.weather

import com.eink.dashboard.modules.weather.data.RetrofitWeatherApi
import com.eink.dashboard.modules.weather.data.WeatherError
import com.eink.dashboard.modules.weather.model.LocationPresets
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The real Retrofit client against MockWebServer — **no real network**. Verifies the
 * exact query the client sends to Open-Meteo (the official `current`/`daily` field
 * lists, `timezone=auto`, `forecast_days=7`), wire parsing on the saved fixture, and
 * the HTTP-status → [WeatherError] mapping.
 */
class RetrofitWeatherApiTest {

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

    private fun api() = RetrofitWeatherApi.fromClient(
        client = OkHttpClient.Builder().build(),
        baseUrl = server.url("/").toString(),
        clock = { 1_234L },
    )

    private fun fixture(): String =
        javaClass.getResource("/fixtures/open_meteo_tashkent.json")!!.readText()

    @Test
    fun fetch_parsesFixture_andSendsOfficialQuery(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(fixture()))

        val snapshot = api().fetch(LocationPresets.TASHKENT)

        assertThat(snapshot.current.temperatureC).isEqualTo(28.4)
        assertThat(snapshot.forecast).hasSize(7)
        assertThat(snapshot.timezone).isEqualTo("Asia/Tashkent")
        assertThat(snapshot.fetchedAtEpochMs).isEqualTo(1_234L)

        val request = server.takeRequest()
        assertThat(request.path).contains("/v1/forecast")
        assertThat(request.path).contains("latitude=41.2995")
        assertThat(request.path).contains("longitude=69.2401")
        assertThat(request.path).contains("timezone=auto")
        assertThat(request.path).contains("forecast_days=7")
        // Official current + daily field selections.
        assertThat(request.path).contains("temperature_2m")
        assertThat(request.path).contains("weather_code")
        assertThat(request.path).contains("temperature_2m_max")
        assertThat(request.path).contains("precipitation_probability_max")
        // Keyless: no apikey parameter is ever sent.
        assertThat(request.path).doesNotContain("apikey")
    }

    @Test
    fun serverError_isMappedToServer(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        val error = runCatching { api().fetch(LocationPresets.TASHKENT) }.exceptionOrNull()
        assertThat(error).isInstanceOf(WeatherError.Server::class.java)
        assertThat((error as WeatherError.Server).code).isEqualTo(503)
        assertThat(error.isRetryable).isTrue()
    }

    @Test
    fun clientError_isMappedToUnexpected(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
        val error = runCatching { api().fetch(LocationPresets.TASHKENT) }.exceptionOrNull()
        assertThat(error).isInstanceOf(WeatherError.Unexpected::class.java)
    }

    @Test
    fun malformedBody_isMappedToBadResponse(): Unit = runBlocking {
        // 200 OK but no current/daily blocks → the mapper rejects it.
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"timezone":"UTC"}"""))
        val error = runCatching { api().fetch(LocationPresets.TASHKENT) }.exceptionOrNull()
        assertThat(error).isEqualTo(WeatherError.BadResponse)
    }
}
