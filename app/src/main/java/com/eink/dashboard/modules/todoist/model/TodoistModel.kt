package com.eink.dashboard.modules.todoist.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain model for the Todoist block (T04). These are the app's own types, mapped
 * from the wire DTOs (see `data/TodoistApi.kt`) so the rest of the module never
 * depends on the Todoist JSON shape. No secret ever lives here — the personal token
 * is confined to `security/TokenStore` and the request interceptor.
 */

/** Which window of tasks the block shows. Maps to a Todoist filter query. */
enum class TodoistView { TODAY, UPCOMING }

/**
 * A due specification. Todoist stores a full-day date, a floating date-time, or a
 * timezone-fixed date-time; [at] is non-null only when a wall-clock time is set.
 * [isRecurring] matters for completion: closing a recurring task advances it on the
 * server rather than finishing it (handled by the repository/optimistic layer).
 */
data class TodoistDue(
    val date: LocalDate?,
    val at: LocalDateTime?,
    val isRecurring: Boolean,
    val text: String,
) {
    /** Whether this due is on or before [today] (an overdue task). */
    fun isOverdue(today: LocalDate): Boolean = date != null && date.isBefore(today)
}

/**
 * A single task as the dashboard cares about it. [priority] follows the Todoist API
 * convention: 4 = P1 (highest) … 1 = P4/none. [locallyCompleted] is the optimistic
 * flag — set the instant the user ticks the box, before the network round-trip.
 */
data class TodoistTask(
    val id: String,
    val content: String,
    val projectId: String?,
    val labels: List<String>,
    val priority: Int,
    val due: TodoistDue?,
    val parentId: String?,
    val order: Int,
    val locallyCompleted: Boolean = false,
) {
    /** P1..P4 for display; API 4→P1, 1→P4. */
    val priorityLabel: String
        get() = when (priority) {
            4 -> "P1"
            3 -> "P2"
            2 -> "P3"
            else -> "P4"
        }
}

/** A project id → name lookup for showing the project of each task. */
data class TodoistProject(val id: String, val name: String)

/** A node in the subtask forest: a task plus its (present) children. */
data class TaskNode(
    val task: TodoistTask,
    val children: List<TaskNode>,
)

/** One dated group of root tasks in the rendered board. */
data class TodoistDay(
    /** The bucket date, or `null` for tasks with no due date. */
    val date: LocalDate?,
    /** True for the single "Overdue" bucket (dates strictly before today). */
    val isOverdue: Boolean,
    val roots: List<TaskNode>,
)

/** The fully-resolved board the module renders. */
data class TodoistBoard(
    val view: TodoistView,
    val days: List<TodoistDay>,
    val projectNames: Map<String, String>,
) {
    val isEmpty: Boolean get() = days.all { it.roots.isEmpty() }

    companion object {
        fun empty(view: TodoistView) = TodoistBoard(view, emptyList(), emptyMap())
    }
}
