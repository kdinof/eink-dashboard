package com.eink.dashboard.modules.calendar.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Read-only calendar domain model for the Calendar module (T03).
 *
 * Everything here is a plain immutable value type with **no Android dependency**,
 * so the range math, grouping and marker assignment are unit-testable on the JVM
 * without a device or Robolectric. The Android `CalendarContract` reading lives in
 * [com.eink.dashboard.modules.calendar.data.AndroidCalendarDataSource]; this file
 * only describes what a calendar and an event *are* once read.
 */

/** A calendar the user's device already knows about (one row of `CalendarContract.Calendars`). */
data class CalendarInfo(
    /** `Calendars._ID`. Stable per device; the persistence key for selection. */
    val id: Long,
    /** `CALENDAR_DISPLAY_NAME`, e.g. "Work" or the account email. */
    val displayName: String,
    /** `ACCOUNT_NAME` — which account this calendar belongs to. */
    val accountName: String,
)

/**
 * One concrete event occurrence within a queried window.
 *
 * This is an *instance* (a row of `CalendarContract.Instances`): a recurring event
 * is already expanded by the provider into one [CalendarEvent] per occurrence, so
 * the rest of the module never has to interpret RRULEs. [beginMs]/[endMs] are the
 * instance's own start/end.
 *
 * ### Time zones (the subtle part)
 * - **Timed** events: [beginMs]/[endMs] are absolute UTC instants; the wall-clock
 *   day is the local day of [beginMs] in the device zone.
 * - **All-day** events: the provider stores [beginMs] as **midnight UTC** of the
 *   calendar date (and [endMs] as midnight UTC of the day *after* the last day).
 *   So an all-day date must be read at [ZoneOffset.UTC], never the device zone —
 *   otherwise it drifts a day west of Greenwich (Asia/Tashkent is UTC+5, so a
 *   naive local read would move every all-day event to the previous day).
 */
data class CalendarEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val beginMs: Long,
    val endMs: Long,
    val isAllDay: Boolean,
    val location: String? = null,
)

/** The three display windows required by the card: today, today+tomorrow, the week. */
enum class CalendarRangeMode {
    TODAY,
    TODAY_TOMORROW,
    WEEK;

    /** Number of calendar days the window spans, starting from today. */
    val dayCount: Int
        get() = when (this) {
            TODAY -> 1
            TODAY_TOMORROW -> 2
            WEEK -> 7
        }
}

/**
 * A half-open span of calendar **dates** `[startDate, endExclusive)` in the device
 * zone. Working in `LocalDate` rather than epoch millis keeps day membership exact
 * across midnight and DST, and lets all-day (UTC-dated) and timed (zone-dated)
 * events be compared on the same calendar.
 */
data class DayRange(val startDate: LocalDate, val endExclusive: LocalDate) {

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) && date.isBefore(endExclusive)

    /** Epoch-ms lower bound (inclusive) for a provider query, at local midnight. */
    fun queryStartMs(zone: ZoneId): Long =
        startDate.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Epoch-ms upper bound (exclusive) for a provider query, at local midnight. */
    fun queryEndMs(zone: ZoneId): Long =
        endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        /** The window for [mode], anchored on the local date of [nowMs] in [zone]. */
        fun of(mode: CalendarRangeMode, nowMs: Long, zone: ZoneId): DayRange {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            return DayRange(today, today.plusDays(mode.dayCount.toLong()))
        }
    }
}
