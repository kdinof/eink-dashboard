package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.model.AgendaBuilder
import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.model.CalendarRangeMode
import com.eink.dashboard.modules.calendar.model.DayRange
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * The pure grouping/sorting core, with the timezone traps the card calls out:
 * midnight boundaries, all-day (UTC-dated) vs timed (zone-dated) events, and
 * multi-day all-day expansion — all under `Asia/Tashkent`.
 */
class AgendaBuilderTest {

    private fun timed(id: Long, cal: Long, title: String, h: Int, mi: Int, day: Int = 17) =
        CalendarEvent(
            eventId = id, calendarId = cal, title = title,
            beginMs = localMs(TASHKENT, 2026, 7, day, h, mi),
            endMs = localMs(TASHKENT, 2026, 7, day, h, mi) + 3_600_000,
            isAllDay = false,
        )

    @Test
    fun timedEvents_groupByLocalDay_sortedByStart() {
        val range = DayRange.of(CalendarRangeMode.TODAY_TOMORROW, localMs(TASHKENT, 2026, 7, 17, 8, 0), TASHKENT)
        val events = listOf(
            timed(1, 10, "Lunch", 13, 0),
            timed(2, 10, "Stand-up", 9, 0),
            timed(3, 10, "Tomorrow sync", 10, 0, day = 18),
        )
        val days = AgendaBuilder.build(events, range, TASHKENT)
        assertThat(days.map { it.date }).containsExactly(
            LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 18),
        ).inOrder()
        assertThat(days.first().events.map { it.title }).containsExactly("Stand-up", "Lunch").inOrder()
    }

    @Test
    fun allDayEvents_sortBeforeTimed_onSameDay() {
        val range = DayRange.of(CalendarRangeMode.TODAY, localMs(TASHKENT, 2026, 7, 17, 8, 0), TASHKENT)
        val allDay = CalendarEvent(9, 10, "Public holiday", allDayMs(2026, 7, 17), allDayMs(2026, 7, 18), isAllDay = true)
        val events = listOf(timed(1, 10, "Stand-up", 9, 0), allDay)
        val day = AgendaBuilder.build(events, range, TASHKENT).single()
        assertThat(day.events.map { it.title }).containsExactly("Public holiday", "Stand-up").inOrder()
    }

    @Test
    fun allDayEvent_notShiftedByPositiveOffsetZone() {
        // The classic bug: reading a UTC-midnight all-day date in Tashkent (UTC+5)
        // must NOT move it to the previous day. It stays on the 20th.
        val range = DayRange.of(CalendarRangeMode.WEEK, localMs(TASHKENT, 2026, 7, 17, 8, 0), TASHKENT)
        val allDay = CalendarEvent(9, 10, "Trip", allDayMs(2026, 7, 20), allDayMs(2026, 7, 21), isAllDay = true)
        val days = AgendaBuilder.build(listOf(allDay), range, TASHKENT)
        assertThat(days.single().date).isEqualTo(LocalDate.of(2026, 7, 20))
    }

    @Test
    fun multiDayAllDay_expandsAcrossCoveredDaysWithinRange() {
        val range = DayRange.of(CalendarRangeMode.WEEK, localMs(TASHKENT, 2026, 7, 17, 8, 0), TASHKENT)
        // 20th → 23rd exclusive ⇒ 20, 21, 22.
        val trip = CalendarEvent(9, 10, "Conference", allDayMs(2026, 7, 20), allDayMs(2026, 7, 23), isAllDay = true)
        val days = AgendaBuilder.build(listOf(trip), range, TASHKENT)
        assertThat(days.map { it.date }).containsExactly(
            LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21), LocalDate.of(2026, 7, 22),
        ).inOrder()
    }

    @Test
    fun midnightBoundary_timedEventStaysOnItsStartDay() {
        val range = DayRange.of(CalendarRangeMode.TODAY_TOMORROW, localMs(TASHKENT, 2026, 7, 17, 8, 0), TASHKENT)
        val lateTonight = timed(1, 10, "Late", 23, 59, day = 17)
        val earlyTomorrow = timed(2, 10, "Early", 0, 1, day = 18)
        val days = AgendaBuilder.build(listOf(lateTonight, earlyTomorrow), range, TASHKENT)
        assertThat(days.first { it.date == LocalDate.of(2026, 7, 17) }.events.map { it.title })
            .containsExactly("Late")
        assertThat(days.first { it.date == LocalDate.of(2026, 7, 18) }.events.map { it.title })
            .containsExactly("Early")
    }

    @Test
    fun eventsOutsideRange_areExcluded_andEmptyDaysOmitted() {
        val range = DayRange.of(CalendarRangeMode.TODAY, localMs(TASHKENT, 2026, 7, 17, 8, 0), TASHKENT)
        val yesterday = timed(1, 10, "Past", 9, 0, day = 16)
        val tomorrow = timed(2, 10, "Future", 9, 0, day = 18)
        assertThat(AgendaBuilder.build(listOf(yesterday, tomorrow), range, TASHKENT)).isEmpty()
    }

    @Test
    fun matchingEventsFromDifferentCalendars_areGroupedWithCount() {
        val range = DayRange.of(CalendarRangeMode.TODAY, localMs(TASHKENT, 2026, 7, 17, 8, 0), TASHKENT)
        val events = listOf(
            timed(1, 10, "Design Daily", 11, 0),
            timed(2, 20, "  design   daily ", 11, 0),
            timed(3, 30, "Design Daily", 11, 0),
        )

        val grouped = AgendaBuilder.build(events, range, TASHKENT).single().events.single()

        assertThat(grouped.title).isEqualTo("Design Daily")
        assertThat(grouped.duplicateCount).isEqualTo(3)
    }

    @Test
    fun matchingEventsFromSameCalendar_remainSeparate() {
        val range = DayRange.of(CalendarRangeMode.TODAY, localMs(TASHKENT, 2026, 7, 17, 8, 0), TASHKENT)
        val events = listOf(
            timed(1, 10, "Busy", 13, 0),
            timed(2, 10, "Busy", 13, 0),
        )

        val rows = AgendaBuilder.build(events, range, TASHKENT).single().events

        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.duplicateCount }).containsExactly(1, 1)
    }
}
