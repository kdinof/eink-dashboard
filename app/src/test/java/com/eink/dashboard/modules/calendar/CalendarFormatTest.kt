package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.model.AgendaEvent
import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.ui.CalendarFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneOffset

class CalendarFormatTest {

    private val event = CalendarEvent(
        eventId = 1,
        calendarId = 10,
        title = "Design Daily",
        beginMs = 0,
        endMs = 1,
        isAllDay = false,
    )

    @Test
    fun titleLabel_appendsCountForGroupedEvent() {
        assertThat(CalendarFormat.titleLabel(AgendaEvent(event, duplicateCount = 3)))
            .isEqualTo("Design Daily (3)")
    }

    @Test
    fun titleLabel_keepsSingleEventUnchanged() {
        assertThat(CalendarFormat.titleLabel(AgendaEvent(event)))
            .isEqualTo("Design Daily")
    }

    @Test
    fun timeRangeLabel_formatsCalendarCardRange() {
        val timed = event.copy(beginMs = 9 * 3_600_000L + 30 * 60_000L, endMs = 10 * 3_600_000L + 15 * 60_000L)
        assertThat(CalendarFormat.timeRangeLabel(timed, ZoneOffset.UTC)).isEqualTo("09:30–10:15")
        assertThat(CalendarFormat.timeRangeLabel(event.copy(isAllDay = true), ZoneOffset.UTC)).isEqualTo("All day")
    }
}
