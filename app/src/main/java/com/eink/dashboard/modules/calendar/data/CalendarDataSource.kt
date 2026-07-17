package com.eink.dashboard.modules.calendar.data

import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.model.CalendarInfo

/**
 * Read-only access to the device's calendars and event instances.
 *
 * Abstracting this behind an interface is what lets the whole module be tested
 * without a device: [AndroidCalendarDataSource] reads `CalendarContract`, while the
 * tests supply a fake that returns canned calendars and instances (including
 * expanded recurrences, all-day rows and midnight-boundary cases).
 *
 * Implementations assume the caller already holds `READ_CALENDAR`; the module
 * checks the permission before ever calling these methods.
 */
interface CalendarDataSource {

    /** All calendars visible to the device account(s). */
    fun listCalendars(): List<CalendarInfo>

    /**
     * Event **instances** overlapping `[startMs, endMs)`. Recurring events are
     * already expanded to one [CalendarEvent] per occurrence by the provider.
     *
     * @param calendarIds restrict to these calendar ids; `null` means all.
     */
    fun queryInstances(startMs: Long, endMs: Long, calendarIds: Set<Long>?): List<CalendarEvent>
}
