package com.eink.dashboard.modules.weather.data

import com.eink.dashboard.modules.weather.model.ResolvedLocation
import com.eink.dashboard.modules.weather.model.WeatherSnapshot
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Real [WeatherApi] over Retrofit/OkHttp against the official Open-Meteo forecast
 * endpoint. No auth (keyless), no logging interceptor. Transport failures are
 * translated to [WeatherError] (never a raw `HttpException`), so no response body
 * can reach a log or the UI verbatim. The wall-clock time of the successful read is
 * stamped by [clock] so the snapshot carries a real "fetched at" without the caller
 * needing the system clock.
 */
class RetrofitWeatherApi private constructor(
    private val service: OpenMeteoService,
    private val clock: () -> Long,
) : WeatherApi {

    override suspend fun fetch(location: ResolvedLocation): WeatherSnapshot {
        val dto = try {
            service.forecast(
                latitude = location.latitude,
                longitude = location.longitude,
                current = WeatherQuery.CURRENT,
                daily = WeatherQuery.DAILY,
                timezone = WeatherQuery.TIMEZONE,
                forecastDays = WeatherQuery.FORECAST_DAYS,
            )
        } catch (e: retrofit2.HttpException) {
            throw errorFor(e.code())
        } catch (e: IOException) {
            throw WeatherError.Network
        }
        return dto.toSnapshot(location, clock())
    }

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        /** Wires the production client. */
        fun create(
            baseUrl: String = BASE_URL,
            clock: () -> Long = { System.currentTimeMillis() },
        ): RetrofitWeatherApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
            return fromClient(client, baseUrl, clock)
        }

        /** Builds the API from a pre-configured client — used by MockWebServer tests. */
        fun fromClient(
            client: OkHttpClient,
            baseUrl: String,
            clock: () -> Long = { System.currentTimeMillis() },
        ): RetrofitWeatherApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitWeatherApi(retrofit.create(OpenMeteoService::class.java), clock)
        }

        private fun errorFor(code: Int): WeatherError = when (code) {
            in 500..599 -> WeatherError.Server(code)
            else -> WeatherError.Unexpected(code)
        }
    }
}

/** Retrofit surface. Kept internal; the app depends on [WeatherApi]. */
internal interface OpenMeteoService {

    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String,
        @Query("daily") daily: String,
        @Query("timezone") timezone: String,
        @Query("forecast_days") forecastDays: Int,
    ): ForecastDto
}
