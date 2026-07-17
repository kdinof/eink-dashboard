package com.eink.dashboard.modules.weather

import com.eink.dashboard.modules.weather.data.toDomain
import com.eink.dashboard.modules.weather.data.toStored
import com.eink.dashboard.modules.weather.model.CurrentConditions
import com.eink.dashboard.modules.weather.model.DailyConditions
import com.eink.dashboard.modules.weather.model.LocationPresets
import com.eink.dashboard.modules.weather.model.WeatherSnapshot
import com.eink.dashboard.modules.weather.model.WmoCondition
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * The persisted [com.eink.dashboard.modules.weather.data.StoredSnapshot] must
 * round-trip a snapshot losslessly (except that exact WMO sub-codes collapse to a
 * representative code per bucket, which is all the display needs).
 */
class WeatherCacheTest {

    private val snapshot = WeatherSnapshot(
        location = LocationPresets.TASHKENT,
        timezone = "Asia/Tashkent",
        current = CurrentConditions(
            temperatureC = 28.4, apparentTemperatureC = 27.1, humidityPercent = 38,
            condition = WmoCondition.RAIN, windKmh = 11.2, isDay = true, precipitationMm = 0.4,
        ),
        forecast = listOf(
            DailyConditions(
                date = LocalDate.of(2026, 7, 17), condition = WmoCondition.THUNDERSTORM,
                temperatureMaxC = 36.2, temperatureMinC = 22.0,
                sunrise = LocalTime.of(5, 14), sunset = LocalTime.of(19, 56),
                precipitationSumMm = 2.4, precipitationProbabilityMaxPercent = 55,
            ),
        ),
        fetchedAtEpochMs = 1_700_000_000_000L,
    )

    @Test
    fun storedRoundTrip_preservesCurrentAndForecast() {
        val restored = snapshot.toStored().toDomain()

        assertThat(restored.timezone).isEqualTo("Asia/Tashkent")
        assertThat(restored.fetchedAtEpochMs).isEqualTo(1_700_000_000_000L)
        assertThat(restored.location.label).isEqualTo("Tashkent")

        assertThat(restored.current.temperatureC).isEqualTo(28.4)
        assertThat(restored.current.humidityPercent).isEqualTo(38)
        assertThat(restored.current.condition).isEqualTo(WmoCondition.RAIN)
        assertThat(restored.current.isDay).isTrue()

        val day = restored.forecast.single()
        assertThat(day.date).isEqualTo(LocalDate.of(2026, 7, 17))
        assertThat(day.condition).isEqualTo(WmoCondition.THUNDERSTORM)
        assertThat(day.sunrise).isEqualTo(LocalTime.of(5, 14))
        assertThat(day.precipitationProbabilityMaxPercent).isEqualTo(55)
    }
}
