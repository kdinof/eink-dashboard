package com.eink.dashboard.modules.weather.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Domain model for the Weather block (T05), independent of the Open-Meteo wire
 * JSON (that lives in `data/WeatherDtos.kt`). Temperatures are Celsius, wind is
 * km/h — the app does not expose alternate units.
 */

/** A bare coordinate pair (e.g. a device fix), before it is labelled/zoned. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Where the coordinates the forecast was fetched for came from. */
enum class LocationSource { PRESET, FIXED, DEVICE }

/** A resolved place to fetch weather for. [timezone] is a hint; the API is asked
 * for `timezone=auto` and the response's zone is authoritative in the snapshot. */
data class ResolvedLocation(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    val source: LocationSource,
)

/** Fixed presets. Tashkent is the shipped default (see the T05 card). */
object LocationPresets {
    val TASHKENT = ResolvedLocation(
        label = "Tashkent",
        latitude = 41.2995,
        longitude = 69.2401,
        timezone = "Asia/Tashkent",
        source = LocationSource.PRESET,
    )
}

/**
 * WMO weather interpretation code grouped into the coarse buckets an e-ink panel
 * can convey without colour. [label] is a short human phrase; [symbol] is a
 * chroma-free ASCII glyph (no colour emoji — hue is meaningless on the panel).
 */
enum class WmoCondition(val label: String, val symbol: String) {
    CLEAR("Clear", "○"),
    MAINLY_CLEAR("Mainly clear", "◔"),
    PARTLY_CLOUDY("Partly cloudy", "◑"),
    OVERCAST("Overcast", "●"),
    FOG("Fog", "≡"),
    DRIZZLE("Drizzle", "‥"),
    RAIN("Rain", "▒"),
    FREEZING_RAIN("Freezing rain", "▓"),
    SNOW("Snow", "＊"),
    SHOWERS("Showers", "░"),
    SNOW_SHOWERS("Snow showers", "❄"),
    THUNDERSTORM("Thunderstorm", "▚"),
    UNKNOWN("—", "?");

    companion object {
        /** Map a raw WMO code onto a bucket. Unrecognised codes fall to [UNKNOWN]. */
        fun fromCode(code: Int): WmoCondition = when (code) {
            0 -> CLEAR
            1 -> MAINLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65 -> RAIN
            66, 67 -> FREEZING_RAIN
            71, 73, 75, 77 -> SNOW
            80, 81, 82 -> SHOWERS
            85, 86 -> SNOW_SHOWERS
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}

/** Present conditions at the location. */
data class CurrentConditions(
    val temperatureC: Double,
    val apparentTemperatureC: Double?,
    val humidityPercent: Int?,
    val condition: WmoCondition,
    val windKmh: Double?,
    val isDay: Boolean,
    val precipitationMm: Double?,
)

/** One day of the forecast (index 0 is "today"). */
data class DailyConditions(
    val date: LocalDate,
    val condition: WmoCondition,
    val temperatureMaxC: Double,
    val temperatureMinC: Double,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
    val precipitationSumMm: Double?,
    val precipitationProbabilityMaxPercent: Int?,
)

/**
 * A complete, self-contained weather reading: the place, the API-reported
 * [timezone], the [current] conditions, and a [forecast] whose first element is
 * today. [fetchedAtEpochMs] is the wall-clock time the snapshot was produced —
 * used by the shell for the "updated HH:mm" / stale chrome.
 */
data class WeatherSnapshot(
    val location: ResolvedLocation,
    val timezone: String,
    val current: CurrentConditions,
    val forecast: List<DailyConditions>,
    val fetchedAtEpochMs: Long,
) {
    /** Day summary == the first forecast day, or null if the API sent no days. */
    val today: DailyConditions? get() = forecast.firstOrNull()
}
