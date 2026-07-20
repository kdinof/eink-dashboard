package com.eink.dashboard.modules.calendar.ui

import com.eink.dashboard.modules.calendar.model.AgendaEvent
import com.eink.dashboard.modules.calendar.model.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure, testable formatting for the agenda: day headers relative to "today" and
 * time labels that respect the all-day vs. timed distinction and the device zone.
 * No Compose here so the strings can be asserted in unit tests.
 */
object CalendarFormat {

    private val DAY_HEADER = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /** "Today" / "Tomorrow" / "Wed, 23 Jul", relative to [today]. */
    fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> DAY_HEADER.format(date)
    }

    /**
     * Left-hand time label for an event line: "all-day" for all-day rows, otherwise
     * "HH:mm" of the start in [zone].
     */
    fun timeLabel(event: CalendarEvent, zone: ZoneId): String =
        if (event.isAllDay) {
            "all-day"
        } else {
            HH_MM.format(Instant.ofEpochMilli(event.beginMs).atZone(zone))
        }

    /** Adds a count only when one row represents copies from multiple calendars. */
    fun titleLabel(event: AgendaEvent): String =
        if (event.duplicateCount > 1) "${event.title} (${event.duplicateCount})" else event.title

    /** The calendar date an event line is filed under (UTC for all-day, [zone] for timed). */
    fun eventDate(event: CalendarEvent, zone: ZoneId): LocalDate =
        if (event.isAllDay) {
            Instant.ofEpochMilli(event.beginMs).atZone(ZoneOffset.UTC).toLocalDate()
        } else {
            Instant.ofEpochMilli(event.beginMs).atZone(zone).toLocalDate()
        }
}
