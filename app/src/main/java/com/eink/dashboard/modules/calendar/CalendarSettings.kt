package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.model.CalendarInfo
import com.eink.dashboard.modules.calendar.model.CalendarRangeMode

enum class CalendarSourceMode { DEVICE, GOOGLE }

/**
 * Calendar-module settings: which display window and which calendars are shown.
 *
 * Selection is stored as a separate set of **deselected** calendar ids for each
 * source (mirroring how the shell stores hidden module ids). A newly discovered
 * calendar therefore shows by default, while switching Reader ↔ Google cannot
 * overwrite the other source's choices.
 *
 * This lives in the Calendar module and is persisted by [CalendarSettingsStore] in
 * its own DataStore file — it deliberately does **not** touch the frozen shell
 * `settings/` package owned by T02.
 */
data class CalendarSettings(
    val range: CalendarRangeMode = CalendarRangeMode.TODAY,
    val source: CalendarSourceMode = CalendarSourceMode.DEVICE,
    val deviceDeselectedCalendarIds: Set<Long> = emptySet(),
    val googleDeselectedCalendarIds: Set<Long> = emptySet(),
) {
    /** Selection belongs to a source. Device and Google ids must never overwrite each other. */
    val deselectedCalendarIds: Set<Long>
        get() = deselectedIdsFor(source)

    fun deselectedIdsFor(source: CalendarSourceMode): Set<Long> = when (source) {
        CalendarSourceMode.DEVICE -> deviceDeselectedCalendarIds
        CalendarSourceMode.GOOGLE -> googleDeselectedCalendarIds
    }

    fun isSelected(id: Long): Boolean = id !in deselectedCalendarIds

    /** The selected ids among [calendars]. Empty set → the user hid everything. */
    fun selectedIdsAmong(calendars: List<CalendarInfo>): Set<Long> =
        calendars.map { it.id }.filter(::isSelected).toSet()

    companion object {
        val DEFAULT = CalendarSettings()
    }
}
