package com.eink.dashboard.modules.weather.data

import com.eink.dashboard.modules.weather.model.CurrentConditions
import com.eink.dashboard.modules.weather.model.DailyConditions
import com.eink.dashboard.modules.weather.model.ResolvedLocation
import com.eink.dashboard.modules.weather.model.WeatherSnapshot
import com.eink.dashboard.modules.weather.model.WmoCondition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Wire DTOs for the Open-Meteo forecast JSON, kept apart from the domain model so
 * parsing (and any future field changes) live in one place. Deserialization uses
 * `ignoreUnknownKeys = true`, so the API adding fields never breaks us. The daily
 * block is column-oriented (parallel arrays indexed by day), which the mapper zips
 * back into per-day [DailyConditions].
 *
 * Field selection and query shape are documented on [WeatherApi]. These names are
 * the fixed strings [WeatherQuery] asks the API for.
 */

/** The query field lists the client requests — shared by the service and tests. */
object WeatherQuery {
    const val CURRENT =
        "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day,precipitation"
    const val DAILY =
        "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_sum,precipitation_probability_max"
    const val FORECAST_DAYS = 7
    const val TIMEZONE = "auto"
}

@Serializable
data class ForecastDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timezone: String = "UTC",
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentDto? = null,
    val daily: DailyDto? = null,
)

@Serializable
data class CurrentDto(
    val time: String? = null,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("is_day") val isDay: Int? = null,
    val precipitation: Double? = null,
)

@Serializable
data class DailyDto(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double> = emptyList(),
    val sunrise: List<String> = emptyList(),
    val sunset: List<String> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?> = emptyList(),
)

// ---- DTO → domain mapping -----------------------------------------------------

/**
 * Map a raw forecast onto a [WeatherSnapshot]. The API-reported [ForecastDto.timezone]
 * wins over the resolved-location hint. Throws [WeatherError.BadResponse] if the
 * payload lacks the current block or any usable day — the repository turns that into
 * a stale/cache fallback rather than showing a half-empty block.
 */
fun ForecastDto.toSnapshot(location: ResolvedLocation, fetchedAtEpochMs: Long): WeatherSnapshot {
    val cur = current ?: throw WeatherError.BadResponse
    val curTemp = cur.temperature ?: throw WeatherError.BadResponse
    val d = daily ?: throw WeatherError.BadResponse

    // Guard against ragged arrays: only build days that have all core fields.
    val dayCount = minOf(d.time.size, d.weatherCode.size, d.temperatureMax.size, d.temperatureMin.size)
    if (dayCount == 0) throw WeatherError.BadResponse

    val days = (0 until dayCount).map { i ->
        DailyConditions(
            date = parseDate(d.time[i]) ?: throw WeatherError.BadResponse,
            condition = WmoCondition.fromCode(d.weatherCode[i]),
            temperatureMaxC = d.temperatureMax[i],
            temperatureMinC = d.temperatureMin[i],
            sunrise = parseTime(d.sunrise.getOrNull(i)),
            sunset = parseTime(d.sunset.getOrNull(i)),
            precipitationSumMm = d.precipitationSum.getOrNull(i),
            precipitationProbabilityMaxPercent = d.precipitationProbabilityMax.getOrNull(i),
        )
    }

    return WeatherSnapshot(
        location = location.copy(timezone = timezone),
        timezone = timezone,
        current = CurrentConditions(
            temperatureC = curTemp,
            apparentTemperatureC = cur.apparentTemperature,
            humidityPercent = cur.humidity,
            condition = WmoCondition.fromCode(cur.weatherCode ?: -1),
            windKmh = cur.windSpeed,
            isDay = (cur.isDay ?: 1) == 1,
            precipitationMm = cur.precipitation,
        ),
        forecast = days,
        fetchedAtEpochMs = fetchedAtEpochMs,
    )
}

private fun parseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return try {
        LocalDate.parse(value.substring(0, 10))
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    }
}

/** Open-Meteo returns local ISO datetimes ("2026-07-17T05:14") when a timezone is set. */
private fun parseTime(value: String?): LocalTime? {
    if (value.isNullOrBlank()) return null
    return try {
        if (value.contains('T')) LocalDateTime.parse(value).toLocalTime() else LocalTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}
