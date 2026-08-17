package com.eink.dashboard.modules.calendar.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Defines "still relevant" without mixing two different notions of time.
 * Timed events end at an instant. All-day events end on an exclusive calendar
 * date encoded at UTC midnight, and must not disappear early in western zones.
 */
object CalendarEventVisibility {
    fun isUpcomingOrOngoing(event: CalendarEvent, nowMs: Long, zone: ZoneId): Boolean {
        if (!event.isAllDay) return event.endMs > nowMs

        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val endExclusive = Instant.ofEpochMilli(event.endMs)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        return endExclusive.isAfter(today)
    }
}
