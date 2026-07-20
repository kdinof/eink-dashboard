package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.model.AgendaEvent
import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.ui.CalendarFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
}
