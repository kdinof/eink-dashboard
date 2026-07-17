package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.data.CalendarDataSource
import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.model.CalendarInfo
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Asia/Tashkent (UTC+5, no DST) — the device's zone, used across calendar tests. */
val TASHKENT: ZoneId = ZoneId.of("Asia/Tashkent")

/** Epoch ms of a wall-clock local time in [zone]. */
fun localMs(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

/** Epoch ms a provider uses for an all-day date: midnight **UTC** of that date. */
fun allDayMs(year: Int, month: Int, day: Int): Long =
    LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * In-memory [CalendarDataSource] for tests. Filters returned instances by the
 * queried window and calendar-id subset the same way the provider would, records
 * the last query args for assertions, and can be flipped to fail a query to
 * exercise the stale/error paths.
 */
class FakeCalendarDataSource(
    private val calendars: List<CalendarInfo>,
    private val events: List<CalendarEvent>,
    @Volatile var failOnQuery: Boolean = false,
) : CalendarDataSource {

    var queryCount: Int = 0
        private set
    var lastCalendarIds: Set<Long>? = null
        private set

    override fun listCalendars(): List<CalendarInfo> = calendars

    override fun queryInstances(startMs: Long, endMs: Long, calendarIds: Set<Long>?): List<CalendarEvent> {
        if (failOnQuery) throw RuntimeException("provider unavailable")
        queryCount++
        lastCalendarIds = calendarIds
        return events.filter { event ->
            (calendarIds == null || event.calendarId in calendarIds) &&
                event.beginMs < endMs && event.endMs > startMs
        }
    }
}
