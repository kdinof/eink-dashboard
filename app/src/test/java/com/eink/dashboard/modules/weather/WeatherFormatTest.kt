package com.eink.dashboard.modules.weather

import com.eink.dashboard.modules.weather.model.DailyConditions
import com.eink.dashboard.modules.weather.model.WmoCondition
import com.eink.dashboard.modules.weather.ui.WeatherFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/** Pure temperature/day/precip formatting for the Weather body. */
class WeatherFormatTest {

    private fun day(date: LocalDate, max: Double, min: Double, prob: Int?) = DailyConditions(
        date = date, condition = WmoCondition.CLEAR, temperatureMaxC = max, temperatureMinC = min,
        sunrise = null, sunset = null, precipitationSumMm = null, precipitationProbabilityMaxPercent = prob,
    )

    @Test
    fun temperature_roundsToWholeDegrees() {
        assertThat(WeatherFormat.temperature(22.6)).isEqualTo("23°")
        assertThat(WeatherFormat.temperature(22.4)).isEqualTo("22°")
        assertThat(WeatherFormat.temperature(-1.5)).isEqualTo("-1°") // half-up rounding
    }

    @Test
    fun highLow_formatsBothEnds() {
        val d = day(LocalDate.of(2026, 7, 17), max = 36.2, min = 22.0, prob = 0)
        assertThat(WeatherFormat.highLow(d)).isEqualTo("36° / 22°")
    }

    @Test
    fun windAndHumidity_handleNulls() {
        assertThat(WeatherFormat.wind(11.7)).isEqualTo("12 km/h")
        assertThat(WeatherFormat.wind(null)).isEqualTo("—")
        assertThat(WeatherFormat.humidity(38)).isEqualTo("38%")
        assertThat(WeatherFormat.humidity(null)).isEqualTo("—")
    }

    @Test
    fun dayLabel_saysTodayForToday_elseWeekday() {
        val today = LocalDate.of(2026, 7, 17) // a Friday
        assertThat(WeatherFormat.dayLabel(today, today)).isEqualTo("Today")
        assertThat(WeatherFormat.dayLabel(LocalDate.of(2026, 7, 18), today)).isEqualTo("Sat")
    }

    @Test
    fun precipProbability_shownOnlyWhenMeaningful() {
        assertThat(WeatherFormat.precipProbability(55)).isEqualTo(" · 55%")
        assertThat(WeatherFormat.precipProbability(0)).isEmpty()
        assertThat(WeatherFormat.precipProbability(null)).isEmpty()
    }
}
