package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.model.CalendarEventVisibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

class CalendarEventVisibilityTest {
    private val losAngeles = ZoneId.of("America/Los_Angeles")

    @Test
    fun allDayEvent_remainsVisibleForItsWholeLocalCalendarDate() {
        val event = CalendarEvent(
            eventId = 1,
            calendarId = 1,
            title = "Holiday",
            beginMs = allDayMs(2026, 7, 17),
            endMs = allDayMs(2026, 7, 18),
            isAllDay = true,
        )
        // 18:00 local is already after the event's UTC end instant, but it is
        // still July 17 on the wall calendar and must remain on screen.
        val now = localMs(losAngeles, 2026, 7, 17, 18, 0)

        assertThat(CalendarEventVisibility.isUpcomingOrOngoing(event, now, losAngeles)).isTrue()
    }

    @Test
    fun timedEvent_usesItsActualEndInstant() {
        val now = localMs(TASHKENT, 2026, 7, 17, 10, 0)
        val event = CalendarEvent(1, 1, "Call", now - 3_600_000, now, false)

        assertThat(CalendarEventVisibility.isUpcomingOrOngoing(event, now, TASHKENT)).isFalse()
    }
}
