package com.eink.dashboard.modules.weather

import com.eink.dashboard.modules.weather.data.ForecastDto
import com.eink.dashboard.modules.weather.data.WeatherError
import com.eink.dashboard.modules.weather.data.toSnapshot
import com.eink.dashboard.modules.weather.model.LocationPresets
import com.eink.dashboard.modules.weather.model.WmoCondition
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Parsing of the official Open-Meteo forecast payload on a saved fixture (T05:
 * "forecast parsing on a stored fixture") plus the DTO → domain mapping, WMO code
 * bucketing, the API-reported timezone winning, and the malformed-payload guards.
 */
class WeatherDtoTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun fixture(): String =
        javaClass.getResource("/fixtures/open_meteo_tashkent.json")!!.readText()

    private fun parse(raw: String): ForecastDto = json.decodeFromString(ForecastDto.serializer(), raw)

    @Test
    fun fixture_parsesCurrentConditions() {
        val snapshot = parse(fixture()).toSnapshot(LocationPresets.TASHKENT, fetchedAtEpochMs = 1_000L)
        val current = snapshot.current
        assertThat(current.temperatureC).isEqualTo(28.4)
        assertThat(current.apparentTemperatureC).isEqualTo(27.1)
        assertThat(current.humidityPercent).isEqualTo(38)
        assertThat(current.condition).isEqualTo(WmoCondition.MAINLY_CLEAR)
        assertThat(current.windKmh).isEqualTo(11.2)
        assertThat(current.isDay).isTrue()
        assertThat(snapshot.fetchedAtEpochMs).isEqualTo(1_000L)
    }

    @Test
    fun fixture_parsesSevenForecastDays_firstIsToday() {
        val snapshot = parse(fixture()).toSnapshot(LocationPresets.TASHKENT, fetchedAtEpochMs = 0L)
        assertThat(snapshot.forecast).hasSize(7)
        val today = snapshot.forecast.first()
        assertThat(today.date).isEqualTo(LocalDate.of(2026, 7, 17))
        assertThat(today.temperatureMaxC).isEqualTo(36.2)
        assertThat(today.temperatureMinC).isEqualTo(22.0)
        assertThat(today.sunrise).isEqualTo(LocalTime.of(5, 14))
        assertThat(today.sunset).isEqualTo(LocalTime.of(19, 56))
        assertThat(snapshot.today).isEqualTo(today)
    }

    @Test
    fun fixture_mapsWmoCodesOntoBuckets() {
        val snapshot = parse(fixture()).toSnapshot(LocationPresets.TASHKENT, fetchedAtEpochMs = 0L)
        val conditions = snapshot.forecast.map { it.condition }
        // codes: 1, 0, 2, 3, 61, 95, 0
        assertThat(conditions).containsExactly(
            WmoCondition.MAINLY_CLEAR,
            WmoCondition.CLEAR,
            WmoCondition.PARTLY_CLOUDY,
            WmoCondition.OVERCAST,
            WmoCondition.RAIN,
            WmoCondition.THUNDERSTORM,
            WmoCondition.CLEAR,
        ).inOrder()
    }

    @Test
    fun apiTimezone_winsOverLocationHint() {
        // The preset hint is Asia/Tashkent; even a different hint must be overridden
        // by the API-reported zone. Re-resolve with a bogus hint to prove it.
        val bogusHint = LocationPresets.TASHKENT.copy(timezone = "UTC")
        val snapshot = parse(fixture()).toSnapshot(bogusHint, fetchedAtEpochMs = 0L)
        assertThat(snapshot.timezone).isEqualTo("Asia/Tashkent")
        assertThat(snapshot.location.timezone).isEqualTo("Asia/Tashkent")
    }

    @Test
    fun precipitationProbability_isCarried() {
        val snapshot = parse(fixture()).toSnapshot(LocationPresets.TASHKENT, fetchedAtEpochMs = 0L)
        assertThat(snapshot.forecast[4].precipitationProbabilityMaxPercent).isEqualTo(55)
        assertThat(snapshot.forecast[5].precipitationSumMm).isEqualTo(6.1)
    }

    @Test
    fun missingCurrentBlock_throwsBadResponse() {
        val error = runCatching {
            parse("""{"timezone":"UTC","daily":{"time":["2026-07-17"],"weather_code":[0],"temperature_2m_max":[10.0],"temperature_2m_min":[1.0]}}""")
                .toSnapshot(LocationPresets.TASHKENT, 0L)
        }.exceptionOrNull()
        assertThat(error).isEqualTo(WeatherError.BadResponse)
    }

    @Test
    fun emptyDailyArrays_throwBadResponse() {
        val error = runCatching {
            parse("""{"timezone":"UTC","current":{"temperature_2m":20.0,"weather_code":0},"daily":{"time":[],"weather_code":[],"temperature_2m_max":[],"temperature_2m_min":[]}}""")
                .toSnapshot(LocationPresets.TASHKENT, 0L)
        }.exceptionOrNull()
        assertThat(error).isEqualTo(WeatherError.BadResponse)
    }

    @Test
    fun raggedDailyArrays_areTruncatedToTheShortest() {
        // 3 dates but only 2 max-temps → only 2 usable days, no crash.
        val snapshot = parse(
            """{"timezone":"UTC","current":{"temperature_2m":20.0,"weather_code":0},
               "daily":{"time":["2026-07-17","2026-07-18","2026-07-19"],
               "weather_code":[0,1,2],"temperature_2m_max":[10.0,11.0],"temperature_2m_min":[1.0,2.0]}}""",
        ).toSnapshot(LocationPresets.TASHKENT, 0L)
        assertThat(snapshot.forecast).hasSize(2)
    }
}
