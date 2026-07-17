package com.eink.dashboard.modules.todoist.model

/**
 * Pure mapping from a [TodoistView] to the official Todoist filter-query string
 * used by the `GET /api/v1/tasks/filter` endpoint. Kept separate and unit-tested so
 * the exact filter syntax is a single, verifiable place.
 *
 * - [TodoistView.TODAY] → overdue tasks plus tasks due today.
 * - [TodoistView.UPCOMING] → tasks due within the next seven days.
 *
 * `overdue`, `today` and `7 days` are Todoist's documented filter keywords; the
 * server resolves them against the account's own time zone, which is what we want
 * for a personal wall dashboard.
 */
object TodoistFilters {

    fun queryFor(view: TodoistView): String = when (view) {
        TodoistView.TODAY -> "overdue | today"
        TodoistView.UPCOMING -> "7 days"
    }
}
