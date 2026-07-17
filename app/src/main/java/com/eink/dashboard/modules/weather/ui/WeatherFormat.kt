package com.eink.dashboard.modules.weather.ui

import com.eink.dashboard.modules.weather.model.DailyConditions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure text formatting for the Weather block — no Android, no colour. Kept apart
 * from the composables so temperature rounding and day labelling are unit-tested
 * directly. Temperatures are whole degrees Celsius; the app exposes no other unit.
 */
object WeatherFormat {

    private val DOW = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

    /** Whole-degree Celsius with the degree sign, e.g. 22.6 → "23°". */
    fun temperature(celsius: Double): String = "${celsius.roundToInt()}°"

    /** "max° / min°" for a forecast day. */
    fun highLow(day: DailyConditions): String =
        "${temperature(day.temperatureMaxC)} / ${temperature(day.temperatureMinC)}"

    /** Wind in whole km/h, or "—" when the API omitted it. */
    fun wind(kmh: Double?): String = if (kmh == null) "—" else "${kmh.roundToInt()} km/h"

    /** Humidity as a percentage, or "—". */
    fun humidity(percent: Int?): String = if (percent == null) "—" else "$percent%"

    /**
     * Short day label for a forecast row: "Today" for [today], otherwise the weekday
     * abbreviation ("Mon", "Tue", …). Locale-fixed so tests are deterministic.
     */
    fun dayLabel(date: LocalDate, today: LocalDate): String =
        if (date == today) "Today" else DOW.format(date)

    /** Precipitation probability as "· 40%" suffix, or "" when absent/zero. */
    fun precipProbability(percent: Int?): String =
        if (percent == null || percent <= 0) "" else " · $percent%"
}
