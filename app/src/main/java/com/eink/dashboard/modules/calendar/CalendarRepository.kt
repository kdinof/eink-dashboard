package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.data.CalendarDataSource
import com.eink.dashboard.modules.calendar.model.AgendaBuilder
import com.eink.dashboard.modules.calendar.model.AgendaDay
import com.eink.dashboard.modules.calendar.model.CalendarInfo
import com.eink.dashboard.modules.calendar.model.CalendarMarker
import com.eink.dashboard.modules.calendar.model.CalendarMarkers
import com.eink.dashboard.modules.calendar.model.DayRange
import java.time.ZoneId

/** One day widened around the query window to catch UTC-dated all-day rows. */
private const val DAY_MS = 24L * 60 * 60 * 1000

/** The fully-resolved agenda the module renders: calendars, grouped days, markers. */
data class CalendarAgenda(
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
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun load(settings: CalendarSettings, nowMs: Long): CalendarAgenda {
        val zone = zoneProvider()
        val calendars = source.listCalendars()
        val range = DayRange.of(settings.range, nowMs, zone)
        val selected = settings.selectedIdsAmong(calendars)

        val events = if (selected.isEmpty()) {
            emptyList()
        } else {
            source.queryInstances(
                startMs = range.queryStartMs(zone) - DAY_MS,
                endMs = range.queryEndMs(zone) + DAY_MS,
                calendarIds = selected,
            )
        }

        val days = AgendaBuilder.build(events, range, zone)
        val markers = CalendarMarkers.assign(calendars)
        return CalendarAgenda(calendars, days, markers, range)
    }
}
