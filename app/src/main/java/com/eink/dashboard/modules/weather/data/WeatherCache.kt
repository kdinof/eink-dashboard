package com.eink.dashboard.modules.weather.data

import com.eink.dashboard.modules.weather.model.CurrentConditions
import com.eink.dashboard.modules.weather.model.DailyConditions
import com.eink.dashboard.modules.weather.model.LocationSource
import com.eink.dashboard.modules.weather.model.ResolvedLocation
import com.eink.dashboard.modules.weather.model.WeatherSnapshot
import com.eink.dashboard.modules.weather.model.WmoCondition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * Persistence of the single latest successful [WeatherSnapshot], so the block can
 * show the last known weather while offline (T05: "cache the last successful
 * response"). Only one snapshot is retained — a dashboard needs "now", not history.
 *
 * The seam is pure of Android; production uses [DataStoreWeatherCache], tests use an
 * in-memory implementation. The [StoredSnapshot] entity is the serialized form (dates
 * and times as ISO strings; the WMO code is re-derived on load), kept apart from the
 * domain so the on-disk shape is explicit and versionable.
 */
interface WeatherCache {
    suspend fun save(snapshot: WeatherSnapshot)
    suspend fun load(): WeatherSnapshot?
}

/** Simple heap cache — used in tests and as a safe default before DataStore is wired. */
class InMemoryWeatherCache(@Volatile private var value: WeatherSnapshot? = null) : WeatherCache {
    override suspend fun save(snapshot: WeatherSnapshot) {
        value = snapshot
    }

    override suspend fun load(): WeatherSnapshot? = value
}

// ---- Serializable entity <-> domain ------------------------------------------

@Serializable
data class StoredSnapshot(
    val label: String,
    val source: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String,
    @SerialName("fetched_at") val fetchedAtEpochMs: Long,
    val current: StoredCurrent,
    val days: List<StoredDay>,
)

@Serializable
data class StoredCurrent(
    val temp: Double,
    val apparent: Double? = null,
    val humidity: Int? = null,
    val code: Int,
    val wind: Double? = null,
    @SerialName("is_day") val isDay: Boolean = true,
    val precip: Double? = null,
)

@Serializable
data class StoredDay(
    val date: String,
    val code: Int,
    val max: Double,
    val min: Double,
    val sunrise: String? = null,
    val sunset: String? = null,
    @SerialName("precip_sum") val precipSum: Double? = null,
    @SerialName("precip_prob") val precipProb: Int? = null,
)

fun WeatherSnapshot.toStored(): StoredSnapshot = StoredSnapshot(
    label = location.label,
    source = location.source.name,
    latitude = location.latitude,
    longitude = location.longitude,
    timezone = timezone,
    fetchedAtEpochMs = fetchedAtEpochMs,
    current = StoredCurrent(
        temp = current.temperatureC,
        apparent = current.apparentTemperatureC,
        humidity = current.humidityPercent,
        code = current.condition.rawFallback(),
        wind = current.windKmh,
        isDay = current.isDay,
        precip = current.precipitationMm,
    ),
    days = forecast.map { day ->
        StoredDay(
            date = day.date.toString(),
            code = day.condition.rawFallback(),
            max = day.temperatureMaxC,
            min = day.temperatureMinC,
            sunrise = day.sunrise?.toString(),
            sunset = day.sunset?.toString(),
            precipSum = day.precipitationSumMm,
            precipProb = day.precipitationProbabilityMaxPercent,
        )
    },
)

fun StoredSnapshot.toDomain(): WeatherSnapshot {
    val loc = ResolvedLocation(
        label = label,
        latitude = latitude,
        longitude = longitude,
        timezone = timezone,
        source = runCatching { LocationSource.valueOf(source) }.getOrDefault(LocationSource.PRESET),
    )
    return WeatherSnapshot(
        location = loc,
        timezone = timezone,
        current = CurrentConditions(
            temperatureC = current.temp,
            apparentTemperatureC = current.apparent,
            humidityPercent = current.humidity,
            condition = WmoCondition.fromCode(current.code),
            windKmh = current.wind,
            isDay = current.isDay,
            precipitationMm = current.precip,
        ),
        forecast = days.map { day ->
            DailyConditions(
                date = LocalDate.parse(day.date),
                condition = WmoCondition.fromCode(day.code),
                temperatureMaxC = day.max,
                temperatureMinC = day.min,
                sunrise = day.sunrise?.let(LocalTime::parse),
                sunset = day.sunset?.let(LocalTime::parse),
                precipitationSumMm = day.precipSum,
                precipitationProbabilityMaxPercent = day.precipProb,
            )
        },
        fetchedAtEpochMs = fetchedAtEpochMs,
    )
}

/**
 * The stored form keeps the raw WMO code, but the domain only holds the bucketed
 * [WmoCondition]. Round-tripping to a representative code keeps the icon/label stable;
 * exact sub-codes within a bucket are not needed for display.
 */
private fun WmoCondition.rawFallback(): Int = when (this) {
    WmoCondition.CLEAR -> 0
    WmoCondition.MAINLY_CLEAR -> 1
    WmoCondition.PARTLY_CLOUDY -> 2
    WmoCondition.OVERCAST -> 3
    WmoCondition.FOG -> 45
    WmoCondition.DRIZZLE -> 51
    WmoCondition.RAIN -> 63
    WmoCondition.FREEZING_RAIN -> 66
    WmoCondition.SNOW -> 73
    WmoCondition.SHOWERS -> 81
    WmoCondition.SNOW_SHOWERS -> 85
    WmoCondition.THUNDERSTORM -> 95
    WmoCondition.UNKNOWN -> -1
}
