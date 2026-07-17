package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.model.TodoistDue
import com.eink.dashboard.modules.todoist.model.TodoistTask
import java.time.LocalDate
import java.time.LocalDateTime

/** Shared task/due builders for the Todoist tests. */
object Fixtures {

    fun task(
        id: String,
        content: String = "Task $id",
        priority: Int = 1,
        projectId: String? = null,
        labels: List<String> = emptyList(),
        parentId: String? = null,
        order: Int = 0,
        date: LocalDate? = null,
        at: LocalDateTime? = null,
        recurring: Boolean = false,
    ): TodoistTask = TodoistTask(
        id = id,
        content = content,
        projectId = projectId,
        labels = labels,
        priority = priority,
        due = if (date == null && at == null) null else TodoistDue(
            date = date ?: at?.toLocalDate(),
            at = at,
            isRecurring = recurring,
            text = "",
        ),
        parentId = parentId,
        order = order,
    )
}
