package com.eink.dashboard.modules.calendar.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.modules.calendar.CalendarModule
import com.eink.dashboard.modules.calendar.model.AgendaDay
import com.eink.dashboard.modules.calendar.model.AgendaEvent
import java.time.ZoneId

/**
 * The Calendar block body: upcoming events grouped by day and rendered as
 * individual calendar cards. Pure grayscale, no decorative calendar markers,
 * no animation and no self-scheduled timers — the shell owns refresh.
 * Loading/empty/error chrome is drawn by the shell from [ModuleState]; here we only
 * render the resolved agenda (or a quiet placeholder before the first load).
 */
@Composable
fun CalendarContent(module: CalendarModule, modifier: Modifier = Modifier) {
    val agenda by module.agenda.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    val current = agenda
    if (current == null || current.days.isEmpty()) {
        Text(
            text = "…",
            modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.xs),
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.Faint,
        )
        return
    }

    val today = current.range.startDate
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm),
    ) {
        current.days.forEach { day ->
            DayGroup(day = day, today = today, zone = zone)
        }
    }
}

@Composable
private fun DayGroup(
    day: AgendaDay,
    today: java.time.LocalDate,
    zone: ZoneId,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm),
    ) {
        Text(
            text = CalendarFormat.dayHeader(day.date, today),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = EinkPalette.Ink,
        )
        day.events.forEach { event ->
            EventCard(event = event, zone = zone)
        }
    }
}

@Composable
private fun EventCard(event: AgendaEvent, zone: ZoneId) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(EinkSpacing.hairline, EinkPalette.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.xs),
    ) {
        Text(
            text = CalendarFormat.timeRangeLabel(event.event, zone),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = EinkPalette.InkMuted,
        )
        Text(
            text = CalendarFormat.titleLabel(event),
            style = MaterialTheme.typography.titleMedium,
            color = EinkPalette.Ink,
        )
        event.event.location?.takeIf { it.isNotBlank() }?.let { location ->
            Text(
                text = location,
                style = MaterialTheme.typography.labelMedium,
                color = EinkPalette.InkMuted,
            )
        }
    }
}
