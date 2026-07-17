package com.eink.dashboard.modules.todoist.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entities for the Todoist cache (T04). Deliberately flat and free of any
 * secret: there is **no token column anywhere** — the token lives only in the
 * Keystore-backed store. Labels are persisted as a newline-joined string via
 * [Converters] to avoid a join table for a display-only list.
 *
 * [CachedTaskEntity] is keyed by (`view`, `id`) so the same task can be cached for
 * both the Today and Upcoming windows independently; [locallyCompleted] carries the
 * optimistic flag across process death.
 */
@Entity(tableName = "todoist_tasks", primaryKeys = ["view", "id"])
data class CachedTaskEntity(
    val view: String,
    val id: String,
    val content: String,
    val projectId: String?,
    val labels: List<String>,
    val priority: Int,
    val parentId: String?,
    val order: Int,
    val dueDate: String?,
    val dueDateTime: String?,
    val dueIsRecurring: Boolean,
    val dueText: String?,
    val locallyCompleted: Boolean,
)

@Entity(tableName = "todoist_projects")
data class CachedProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(tableName = "todoist_pending_ops")
data class PendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val type: String,
    val attempts: Int = 0,
)
