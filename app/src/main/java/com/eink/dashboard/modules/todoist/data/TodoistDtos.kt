package com.eink.dashboard.modules.todoist.data

import com.eink.dashboard.modules.todoist.model.TodoistDue
import com.eink.dashboard.modules.todoist.model.TodoistProject
import com.eink.dashboard.modules.todoist.model.TodoistTask
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Wire DTOs for the Todoist v1 JSON, kept apart from the domain model so the parsing
 * (and any future field changes) live in one place. Deserialization is configured
 * with `ignoreUnknownKeys = true`, so the API adding fields never breaks us.
 *
 * The cursor-paginated list envelope is `{ "results": [...], "next_cursor": "..." }`.
 */

@Serializable
data class TaskPageDto(
    val results: List<TaskDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class ProjectPageDto(
    val results: List<ProjectDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class TaskDto(
    val id: String,
    val content: String = "",
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val labels: List<String> = emptyList(),
    val priority: Int = 1,
    @SerialName("child_order") val childOrder: Int? = null,
    val order: Int? = null,
    val due: DueDto? = null,
)

@Serializable
data class DueDto(
    val date: String? = null,
    val datetime: String? = null,
    val string: String = "",
    @SerialName("is_recurring") val isRecurring: Boolean = false,
    val timezone: String? = null,
)

@Serializable
data class ProjectDto(
    val id: String,
    val name: String = "",
)

// ---- DTO → domain mapping -----------------------------------------------------

fun TaskDto.toDomain(): TodoistTask = TodoistTask(
    id = id,
    content = content,
    projectId = projectId,
    labels = labels,
    priority = priority,
    due = due?.toDomain(),
    parentId = parentId,
    order = childOrder ?: order ?: 0,
)

fun ProjectDto.toDomain(): TodoistProject = TodoistProject(id = id, name = name)

fun DueDto.toDomain(): TodoistDue {
    // Prefer an explicit datetime; otherwise `date` may itself carry a time.
    val at: LocalDateTime? = parseDateTime(datetime) ?: parseDateTime(date)
    val day: LocalDate? = at?.toLocalDate() ?: parseDate(date)
    return TodoistDue(date = day, at = at, isRecurring = isRecurring, text = string)
}

private fun parseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return try {
        LocalDate.parse(value.substring(0, 10))
    } catch (_: DateTimeParseException) {
        null
    } catch (_: IndexOutOfBoundsException) {
        null
    }
}

private fun parseDateTime(value: String?): LocalDateTime? {
    if (value.isNullOrBlank() || !value.contains('T')) return null
    // Try zoned/offset first (…Z or …+05:00), then a floating local datetime.
    return try {
        OffsetDateTime.parse(value).toLocalDateTime()
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
