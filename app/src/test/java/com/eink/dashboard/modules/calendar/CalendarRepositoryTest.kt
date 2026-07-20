package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.model.CalendarInfo
import com.eink.dashboard.modules.calendar.model.CalendarRangeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * End-to-end repository behaviour over a fake provider: the required two-calendar
 * case, selection filtering, expanded-recurrence mapping, and the empty result.
 */
class CalendarRepositoryTest {

    private val work = CalendarInfo(10, "Work", "work@example.com")
    private val personal = CalendarInfo(20, "Personal", "me@example.com")
    private val now = localMs(TASHKENT, 2026, 7, 17, 8, 0)

    private fun repo(events: List<CalendarEvent>, calendars: List<CalendarInfo> = listOf(work, personal)) =
        FakeCalendarDataSource(calendars, events).let { src ->
            src to CalendarRepository(src, zoneProvider = { TASHKENT })
        }

    private fun ev(id: Long, cal: Long, title: String, h: Int, day: Int = 17) = CalendarEvent(
        eventId = id, calendarId = cal, title = title,
        beginMs = localMs(TASHKENT, 2026, 7, day, h, 0),
        endMs = localMs(TASHKENT, 2026, 7, day, h, 0) + 3_600_000,
        isAllDay = false,
    )

    @Test
    fun twoCalendars_bothSelected_includesEventsFromBoth() {
        val (_, repo) = repo(listOf(ev(1, 10, "Work sync", 9), ev(2, 20, "Dentist", 11)))
        val agenda = repo.load(CalendarSettings(range = CalendarRangeMode.TODAY), now)
        val titles = agenda.days.single().events.map { it.title }
        assertThat(titles).containsExactly("Work sync", "Dentist").inOrder()
        assertThat(agenda.markers.keys).containsExactly(10L, 20L)
    }

    @Test
    fun deselectingOneCalendar_filtersItsEvents_andQueriesOnlySelected() {
        val (src, repo) = repo(listOf(ev(1, 10, "Work sync", 9), ev(2, 20, "Dentist", 11)))
        val settings = CalendarSettings(deselectedCalendarIds = setOf(20))
        val agenda = repo.load(settings, now)
        assertThat(agenda.days.single().events.map { it.title }).containsExactly("Work sync")
        assertThat(src.lastCalendarIds).containsExactly(10L)
    }

    @Test
    fun allCalendarsDeselected_yieldsEmpty_withoutQueryingProvider() {
        val (src, repo) = repo(listOf(ev(1, 10, "Work sync", 9)))
        val settings = CalendarSettings(deselectedCalendarIds = setOf(10, 20))
        val agenda = repo.load(settings, now)
        assertThat(agenda.isEmpty).isTrue()
        assertThat(src.queryCount).isEqualTo(0)
    }

    @Test
    fun expandedRecurrence_mapsEachOccurrenceToItsOwnDay() {
        // A daily stand-up: the provider expands it into one instance per day,
        // all sharing eventId=1. Each must land on its own day.
        val instances = listOf(
            ev(1, 10, "Daily stand-up", 9, day = 17),
            ev(1, 10, "Daily stand-up", 9, day = 18),
            ev(1, 10, "Daily stand-up", 9, day = 19),
        )
        val (_, repo) = repo(instances)
        val agenda = repo.load(CalendarSettings(range = CalendarRangeMode.WEEK), now)
        assertThat(agenda.days.map { it.date }).containsExactly(
            LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 19),
        ).inOrder()
        agenda.days.forEach { assertThat(it.events.single().title).isEqualTo("Daily stand-up") }
    }

    @Test
    fun noEventsInWindow_yieldsEmptyAgenda_butKeepsCalendarsForSettings() {
        val (_, repo) = repo(events = emptyList())
        val agenda = repo.load(CalendarSettings(range = CalendarRangeMode.TODAY), now)
        assertThat(agenda.isEmpty).isTrue()
        assertThat(agenda.calendars).hasSize(2) // still offered in Settings
    }

    @Test
    fun completedEventsAreHidden_whileOngoingAndFutureEventsRemain() {
        val completed = ev(1, 10, "Completed", 7) // ends exactly at now (08:00)
        val ongoing = CalendarEvent(
            eventId = 2,
            calendarId = 10,
            title = "In progress",
            beginMs = localMs(TASHKENT, 2026, 7, 17, 7, 30),
            endMs = localMs(TASHKENT, 2026, 7, 17, 8, 30),
            isAllDay = false,
        )
        val future = ev(3, 10, "Next meeting", 9)

        val (_, repo) = repo(listOf(completed, ongoing, future))
        val titles = repo.load(CalendarSettings(range = CalendarRangeMode.TODAY), now)
            .days.single().events.map { it.title }

        assertThat(titles).containsExactly("In progress", "Next meeting").inOrder()
    }
}
