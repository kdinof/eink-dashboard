package com.eink.dashboard.modules.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.modules.weather.WeatherModule
import com.eink.dashboard.modules.weather.model.CurrentConditions
import com.eink.dashboard.modules.weather.model.DailyConditions
import com.eink.dashboard.modules.weather.model.WeatherSnapshot
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkEyebrow
import com.eink.dashboard.ui.ink.InkIcon
import com.eink.dashboard.ui.ink.InkListItem
import com.eink.dashboard.ui.ink.InkLoading
import com.eink.dashboard.ui.ink.InkRuledList
import com.eink.dashboard.ui.ink.InkSize
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkType
import com.eink.dashboard.ui.ink.InkWeatherSummary
import java.time.LocalDate

/**
 * The Weather block body: the place as an eyebrow, current conditions as an
 * `.ink-weather` row (line icon, big temperature, condition + feels/humidity/wind),
 * then the 7-day forecast as ruled rows. Pure grayscale, no animation, no
 * self-scheduled timers — the shell owns refresh and draws the loading/error/stale
 * chrome from [com.eink.dashboard.dashboard.ModuleState].
 */
@Composable
fun WeatherContent(module: WeatherModule, modifier: Modifier = Modifier) {
    val snapshot by module.snapshot.collectAsStateWithLifecycle()

    val current = snapshot
    if (current == null) {
        InkLoading(modifier = modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s3),
    ) {
        InkEyebrow(current.location.label)
        InkWeatherSummary(
            temperature = WeatherFormat.temperature(current.current.temperatureC),
            condition = current.current.condition.label,
            meta = secondaryLine(current.current),
            icon = current.current.condition.icon,
        )
        ForecastList(snapshot = current)
    }
}

private fun secondaryLine(c: CurrentConditions): String {
    val feels = c.apparentTemperatureC?.let { "Feels ${WeatherFormat.temperature(it)}" }
    val humidity = "Hum ${WeatherFormat.humidity(c.humidityPercent)}"
    val wind = "Wind ${WeatherFormat.wind(c.windKmh)}"
    return listOfNotNull(feels, humidity, wind).joinToString(" · ")
}

@Composable
private fun ForecastList(snapshot: WeatherSnapshot) {
    val today = snapshot.today?.date ?: return
    InkRuledList(snapshot.forecast) { day -> ForecastRow(day = day, today = today) }
}

@Composable
private fun ForecastRow(day: DailyConditions, today: LocalDate) {
    InkListItem(
        title = day.condition.label + WeatherFormat.precipProbability(day.precipitationProbabilityMaxPercent),
        strong = day.date == today,
        leading = {
            Text(
                text = WeatherFormat.dayLabel(day.date, today),
                style = InkType.smallStrong,
                modifier = Modifier.width(InkSpace.s12),
            )
            InkIcon(day.condition.icon, size = InkSize.iconMd)
        },
        trailing = { Text(text = WeatherFormat.highLow(day), style = InkType.smallStrong, color = InkColors.Ink) },
    )
}
