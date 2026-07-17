package com.eink.dashboard.modules.calendar.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.modules.calendar.CalendarModule
import com.eink.dashboard.modules.calendar.model.AgendaDay
import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.model.CalendarMarker
import java.time.ZoneId

/**
 * The Calendar block body: grouped days, each event prefixed by its calendar's
 * grayscale marker (shape + shade) so calendars are distinguishable without colour.
 * Pure grayscale, no animation, no self-scheduled timers — the shell owns refresh.
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
            DayGroup(day = day, today = today, markers = current.markers, zone = zone)
        }
    }
}

@Composable
private fun DayGroup(
    day: AgendaDay,
    today: java.time.LocalDate,
    markers: Map<Long, CalendarMarker>,
    zone: ZoneId,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.xs),
    ) {
        Text(
            text = CalendarFormat.dayHeader(day.date, today),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = EinkPalette.Ink,
        )
        day.events.forEach { event ->
            EventRow(event = event, marker = markers[event.calendarId], zone = zone)
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, marker: CalendarMarker?, zone: ZoneId) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // Grayscale calendar marker: shape carries identity, shade adds separation.
        Text(
            text = marker?.glyph ?: "•",
            style = MaterialTheme.typography.bodyMedium,
            color = marker?.let { grayOf(it.shade) } ?: EinkPalette.Ink,
        )
        Spacer(Modifier.width(EinkSpacing.sm))
        Text(
            text = CalendarFormat.timeLabel(event, zone),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = EinkPalette.InkMuted,
            modifier = Modifier.width(64.dp),
        )
        Spacer(Modifier.width(EinkSpacing.sm))
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.Ink,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Map a 0f..1f gray level onto a chroma-free Compose color. */
private fun grayOf(shade: Float): Color {
    val v = shade.coerceIn(0f, 1f)
    return Color(red = v, green = v, blue = v)
}
