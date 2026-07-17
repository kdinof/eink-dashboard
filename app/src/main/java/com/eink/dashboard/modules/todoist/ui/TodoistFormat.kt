package com.eink.dashboard.modules.todoist.ui

import com.eink.dashboard.modules.todoist.model.TodoistDay
import com.eink.dashboard.modules.todoist.model.TodoistDue
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure formatting for the Todoist block — day headers, due times, priority glyphs.
 * No Android, no colour; kept separate so it is unit-testable and the [TodoistContent]
 * composable stays declarative.
 */
object TodoistFormat {

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Header for a day bucket relative to [today]: "Overdue", "Today", weekday, or date. */
    fun dayHeader(day: TodoistDay, today: LocalDate): String {
        if (day.isOverdue) return "Overdue"
        val date = day.date ?: return "No date"
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> {
                val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                "$weekday ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
            }
        }
    }

    /** A compact due-time label, empty when the task has only a date. */
    fun timeLabel(due: TodoistDue?): String = due?.at?.toLocalTime()?.format(TIME).orEmpty()

    /**
     * A grayscale priority mark: filled bars for P1..P3, blank for P4/none, so
     * priority reads without colour. P1 = "▓▓▓", P2 = "▓▓", P3 = "▓", P4 = "".
     */
    fun priorityGlyph(priority: Int): String = when (priority) {
        4 -> "▓▓▓"
        3 -> "▓▓"
        2 -> "▓"
        else -> ""
    }
}
