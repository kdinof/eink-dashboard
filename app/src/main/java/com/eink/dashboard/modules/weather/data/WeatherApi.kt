package com.eink.dashboard.modules.weather.data

import com.eink.dashboard.modules.weather.model.ResolvedLocation
import com.eink.dashboard.modules.weather.model.WeatherSnapshot

/**
 * The seam over the Open-Meteo Forecast API (official, keyless for non-commercial
 * use — base `https://api.open-meteo.com/v1/forecast`). The rest of the module
 * depends only on this interface and the domain [WeatherSnapshot] it returns,
 * never on Retrofit or the wire JSON, so the repository is unit-tested with an
 * in-memory fake and the real client is exercised in isolation with MockWebServer.
 *
 * Verified against the current official docs (2026-07):
 * - `GET /v1/forecast?latitude=..&longitude=..&current=..&daily=..&timezone=auto&forecast_days=7`
 * - `current`: `temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,`
 *   `wind_speed_10m,is_day,precipitation`
 * - `daily`: `weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,`
 *   `precipitation_sum,precipitation_probability_max`
 * - `timezone=auto` makes the API resolve the zone from the coordinates and return
 *   local ISO times; the resolved zone is echoed in the response `timezone` field.
 * - No API key. WMO `weather_code` interpretation → [com.eink.dashboard.modules.weather.model.WmoCondition].
 *
 * Implementations translate transport outcomes into [WeatherError] rather than
 * leaking `HttpException`/`IOException`, so the caller handles offline/5xx/bad
 * payload uniformly. There is no secret in this module (Open-Meteo is unauthenticated).
 */
interface WeatherApi {

    /**
     * Fetch current conditions + a 7-day forecast for [location]. The request uses
     * `timezone=auto`; the returned snapshot carries the API-resolved timezone.
     * Throws [WeatherError] on any transport or payload failure.
     */
    suspend fun fetch(location: ResolvedLocation): WeatherSnapshot
}

/**
 * Transport / payload failures mapped away from Retrofit/OkHttp specifics. Messages
 * are fixed generic strings; no response body is embedded.
 */
sealed class WeatherError(message: String) : Exception(message) {

    /** No connectivity / timeout / DNS — retryable. */
    data object Network : WeatherError("No network")

    /** Any 5xx — a transient server-side problem. */
    data class Server(val code: Int) : WeatherError("Weather server error")

    /** A 2xx response we could not parse into a usable snapshot. */
    data object BadResponse : WeatherError("Bad weather response")

    /** Anything else unexpected (e.g. a 4xx from a malformed query). */
    data class Unexpected(val code: Int?) : WeatherError("Unexpected error")

    /** Whether re-fetching later is sensible. */
    val isRetryable: Boolean
        get() = this is Network || this is Server
}
