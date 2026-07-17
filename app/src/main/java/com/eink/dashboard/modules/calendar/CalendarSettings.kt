package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.model.CalendarInfo
import com.eink.dashboard.modules.calendar.model.CalendarRangeMode

/**
 * Calendar-module settings: which display window and which calendars are shown.
 *
 * Selection is stored as the set of **deselected** calendar ids (mirroring how the
 * shell stores hidden module ids). That way a calendar the user adds later shows
 * up by default instead of silently staying hidden — the safe default for a wall
 * dashboard is "show everything, let me hide".
 *
 * This lives in the Calendar module and is persisted by [CalendarSettingsStore] in
 * its own DataStore file — it deliberately does **not** touch the frozen shell
 * `settings/` package owned by T02.
 */
data class CalendarSettings(
    val range: CalendarRangeMode = CalendarRangeMode.TODAY,
    val deselectedCalendarIds: Set<Long> = emptySet(),
) {
    fun isSelected(id: Long): Boolean = id !in deselectedCalendarIds

    /** The selected ids among [calendars]. Empty set → the user hid everything. */
    fun selectedIdsAmong(calendars: List<CalendarInfo>): Set<Long> =
        calendars.map { it.id }.filter(::isSelected).toSet()

    companion object {
        val DEFAULT = CalendarSettings()
    }
}
