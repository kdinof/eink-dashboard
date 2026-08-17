package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.data.CalendarDataSource
import com.eink.dashboard.modules.calendar.model.AgendaBuilder
import com.eink.dashboard.modules.calendar.model.AgendaDay
import com.eink.dashboard.modules.calendar.model.CalendarInfo
import com.eink.dashboard.modules.calendar.model.CalendarMarker
import com.eink.dashboard.modules.calendar.model.CalendarMarkers
import com.eink.dashboard.modules.calendar.model.CalendarEventVisibility
import com.eink.dashboard.modules.calendar.model.DayRange
import java.time.ZoneId

/** One day widened around the query window to catch UTC-dated all-day rows. */
private const val DAY_MS = 24L * 60 * 60 * 1000

/** The fully-resolved agenda the module renders: calendars, grouped days, markers. */
data class CalendarAgenda(
    val source: CalendarSourceMode,
    val calendars: List<CalendarInfo>,
    val days: List<AgendaDay>,
    val markers: Map<Long, CalendarMarker>,
    val range: DayRange,
) {
    val isEmpty: Boolean get() = days.isEmpty()
}

/**
 * Turns raw [CalendarDataSource] reads into a ready-to-render [CalendarAgenda]
 * according to the user's [CalendarSettings]. Pure orchestration — no Android, no
 * state — so it is exercised end-to-end with a fake data source (two calendars,
 * expanded recurrences, all-day rows).
 *
 * The provider query is widened by a day on each side of the local window and then
 * re-filtered precisely by [AgendaBuilder], because all-day events are dated in UTC
 * and can sit a few hours outside the local-midnight bounds (Asia/Tashkent is
 * UTC+5).
 */
class CalendarRepository(
    private val source: CalendarDataSource,
    private val googleSource: CalendarDataSource? = null,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun load(settings: CalendarSettings, nowMs: Long): CalendarAgenda {
        val zone = zoneProvider()
        val activeSource = if (settings.source == CalendarSourceMode.GOOGLE) googleSource ?: source else source
        val calendars = activeSource.listCalendars()
        val range = DayRange.of(settings.range, nowMs, zone)
        val selected = settings.selectedIdsAmong(calendars)

        val events = if (selected.isEmpty()) {
            emptyList()
        } else {
            activeSource.queryInstances(
                startMs = range.queryStartMs(zone) - DAY_MS,
                endMs = range.queryEndMs(zone) + DAY_MS,
                calendarIds = selected,
            )
        }

        // The dashboard is a forward-looking surface: completed timed events and
        // all-day spans whose exclusive end has passed no longer belong in it.
        // Ongoing events remain visible until their real end time.
        val upcomingEvents = events.filter { CalendarEventVisibility.isUpcomingOrOngoing(it, nowMs, zone) }
        val days = AgendaBuilder.build(upcomingEvents, range, zone)
        val markers = CalendarMarkers.assign(calendars)
        return CalendarAgenda(settings.source, calendars, days, markers, range)
    }
}
