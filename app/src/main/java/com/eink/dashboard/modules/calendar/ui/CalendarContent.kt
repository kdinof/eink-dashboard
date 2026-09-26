package com.eink.dashboard.modules.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.modules.calendar.CalendarModule
import com.eink.dashboard.modules.calendar.model.AgendaDay
import com.eink.dashboard.modules.calendar.model.AgendaEvent
import com.eink.dashboard.ui.ink.InkBadge
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkEyebrow
import com.eink.dashboard.ui.ink.InkListItem
import com.eink.dashboard.ui.ink.InkLoading
import com.eink.dashboard.ui.ink.InkRuledList
import com.eink.dashboard.ui.ink.InkSpace
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Calendar block body: upcoming events grouped by day as `.ink-list--ruled`
 * rows — bold title, grey time/location line, and the start time as a trailing
 * badge (solid for today, framed otherwise). Pure grayscale, no animation and no
 * self-scheduled timers — the shell owns refresh. Loading/empty/error chrome is
 * drawn by the shell from [com.eink.dashboard.dashboard.ModuleState]; here we
 * only render the resolved agenda (or a static loading label before the first load).
 */
@Composable
fun CalendarContent(module: CalendarModule, modifier: Modifier = Modifier) {
    val agenda by module.agenda.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    val current = agenda
    if (current == null || current.days.isEmpty()) {
        InkLoading(modifier = modifier)
        return
    }

    val today = current.range.startDate
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s3),
    ) {
        current.days.forEach { day ->
            DayGroup(day = day, today = today, zone = zone)
        }
    }
}

@Composable
private fun DayGroup(day: AgendaDay, today: LocalDate, zone: ZoneId) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkEyebrow(CalendarFormat.dayHeader(day.date, today), color = if (day.date == today) InkColors.Ink else InkColors.Ink3)
        }
        InkRuledList(day.events) { event -> EventRow(event = event, isToday = day.date == today, zone = zone) }
    }
}

@Composable
private fun EventRow(event: AgendaEvent, isToday: Boolean, zone: ZoneId) {
    val sub = listOfNotNull(
        CalendarFormat.timeRangeLabel(event.event, zone),
        event.event.location?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    InkListItem(
        title = CalendarFormat.titleLabel(event),
        sub = sub,
        strong = true,
        trailing = {
            InkBadge(CalendarFormat.timeLabel(event.event, zone), outline = !isToday)
        },
    )
}
