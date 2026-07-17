package com.eink.dashboard.modules.calendar.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A single day of the agenda: its date plus the events on it, already ordered. */
data class AgendaDay(
    val date: LocalDate,
    val events: List<CalendarEvent>,
)

/**
 * Groups a flat list of event instances into ordered per-day buckets.
 *
 * This is the pure heart of the module — no Android, no I/O — so day membership,
 * midnight boundaries, all-day handling and sort order are all unit-tested against
 * a fixed clock and zone (including `Asia/Tashkent`, UTC+5).
 *
 * Rules:
 * - **Timed** events belong to the local date of their start ([ZoneId]).
 * - **All-day** events are UTC-dated by the provider and may span several days;
 *   each covered date within [range] gets its own entry (so a multi-day trip shows
 *   on every day of the week view, not only day one).
 * - Within a day, **all-day events sort first**, then timed events by start time,
 *   ties broken by title for stable output.
 * - Days with no events are omitted; the result is sorted by date ascending.
 */
object AgendaBuilder {

    private val dayOrder: Comparator<CalendarEvent> =
        compareByDescending<CalendarEvent> { it.isAllDay }
            .thenBy { it.beginMs }
            .thenBy { it.title }

    fun build(events: List<CalendarEvent>, range: DayRange, zone: ZoneId): List<AgendaDay> {
        val byDate = HashMap<LocalDate, MutableList<CalendarEvent>>()
        for (event in events) {
            for (date in coveredDates(event, range, zone)) {
                byDate.getOrPut(date) { mutableListOf() }.add(event)
            }
        }
        return byDate.entries
            .sortedBy { it.key }
            .map { (date, list) -> AgendaDay(date, list.sortedWith(dayOrder)) }
    }

    /** The dates in [range] that [event] occupies (see class rules). */
    private fun coveredDates(event: CalendarEvent, range: DayRange, zone: ZoneId): List<LocalDate> {
        if (!event.isAllDay) {
            val date = Instant.ofEpochMilli(event.beginMs).atZone(zone).toLocalDate()
            return if (range.contains(date)) listOf(date) else emptyList()
        }
        // All-day: begin/end are midnight-UTC; end is exclusive (day after last).
        val start = Instant.ofEpochMilli(event.beginMs).atZone(ZoneOffset.UTC).toLocalDate()
        val endExclusiveRaw = Instant.ofEpochMilli(event.endMs).atZone(ZoneOffset.UTC).toLocalDate()
        // Guard against zero/negative spans → treat as a single day.
        val endExclusive = if (endExclusiveRaw.isAfter(start)) endExclusiveRaw else start.plusDays(1)
        val dates = mutableListOf<LocalDate>()
        var d = start
        while (d.isBefore(endExclusive)) {
            if (range.contains(d)) dates.add(d)
            d = d.plusDays(1)
        }
        return dates
    }
}
