package com.eink.dashboard.modules.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.modules.weather.WeatherModule
import com.eink.dashboard.modules.weather.model.CurrentConditions
import com.eink.dashboard.modules.weather.model.DailyConditions
import com.eink.dashboard.modules.weather.model.WeatherSnapshot

/**
 * The Weather block body: the place, current conditions (big temperature + a
 * grayscale condition glyph and label), a secondary line (feels-like / humidity /
 * wind), then the 7-day forecast as compact rows. Pure grayscale, no animation, no
 * self-scheduled timers — the shell owns refresh and draws the loading/error/stale
 * chrome from [com.eink.dashboard.dashboard.ModuleState]; here we render the snapshot
 * (or a quiet placeholder before the first load).
 */
@Composable
fun WeatherContent(module: WeatherModule, modifier: Modifier = Modifier) {
    val snapshot by module.snapshot.collectAsStateWithLifecycle()

    val current = snapshot
    if (current == null) {
        Text(
            text = "…",
            modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.xs),
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.Faint,
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm),
    ) {
        CurrentBlock(snapshot = current)
        ForecastList(snapshot = current)
    }
}

@Composable
private fun CurrentBlock(snapshot: WeatherSnapshot) {
    val c: CurrentConditions = snapshot.current
    Text(
        text = snapshot.location.label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = EinkPalette.Ink,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = WeatherFormat.temperature(c.temperatureC),
            style = MaterialTheme.typography.displaySmall,
            color = EinkPalette.Ink,
        )
        Spacer(Modifier.width(EinkSpacing.md))
        Column {
            Text(
                text = "${c.condition.symbol}  ${c.condition.label}",
                style = MaterialTheme.typography.titleMedium,
                color = EinkPalette.Ink,
            )
            Text(
                text = secondaryLine(c),
                style = MaterialTheme.typography.bodyMedium,
                color = EinkPalette.InkMuted,
            )
        }
    }
}

private fun secondaryLine(c: CurrentConditions): String {
    val feels = c.apparentTemperatureC?.let { "Feels ${WeatherFormat.temperature(it)}" }
    val humidity = "Hum ${WeatherFormat.humidity(c.humidityPercent)}"
    val wind = "Wind ${WeatherFormat.wind(c.windKmh)}"
    return listOfNotNull(feels, humidity, wind).joinToString("   ")
}

@Composable
private fun ForecastList(snapshot: WeatherSnapshot) {
    val today = snapshot.today?.date ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.xs),
    ) {
        snapshot.forecast.forEach { day ->
            ForecastRow(day = day, today = today)
        }
    }
}

@Composable
private fun ForecastRow(day: DailyConditions, today: java.time.LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = WeatherFormat.dayLabel(day.date, today),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = EinkPalette.Ink,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = day.condition.symbol,
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.Ink,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = day.condition.label + WeatherFormat.precipProbability(day.precipitationProbabilityMaxPercent),
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.InkMuted,
            // RowScope here: take the remaining width so the high/low stays right-aligned.
            modifier = Modifier.weight(1f),
        )
        Text(
            text = WeatherFormat.highLow(day),
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.Ink,
        )
    }
}
