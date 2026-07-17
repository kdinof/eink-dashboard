package com.eink.dashboard.modules.todoist.data.room

import android.content.Context
import com.eink.dashboard.modules.todoist.data.PendingOp
import com.eink.dashboard.modules.todoist.data.PendingOpType
import com.eink.dashboard.modules.todoist.data.TaskCache
import com.eink.dashboard.modules.todoist.model.TodoistDue
import com.eink.dashboard.modules.todoist.model.TodoistProject
import com.eink.dashboard.modules.todoist.model.TodoistTask
import com.eink.dashboard.modules.todoist.model.TodoistView
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [TaskCache] backed by [TodoistDatabase]. Thin mapping glue between the Room
 * entities and the domain model; all cache *behaviour* (optimistic completion,
 * queue/retry) is driven by the repository and covered by JVM tests against the
 * in-memory fake. This class is verified on-device / by the Room build.
 */
class RoomTaskCache(private val dao: TodoistDao) : TaskCache {

    constructor(context: Context) : this(TodoistDatabase.get(context).dao())

    override suspend fun replaceTasks(view: TodoistView, tasks: List<TodoistTask>) {
        dao.clearTasks(view.name)
        dao.insertTasks(tasks.map { it.toEntity(view) })
    }

    override suspend fun tasks(view: TodoistView): List<TodoistTask> =
        dao.tasks(view.name).map { it.toDomain() }

    override suspend fun replaceProjects(projects: List<TodoistProject>) {
        dao.clearProjects()
        dao.insertProjects(projects.map { CachedProjectEntity(it.id, it.name) })
    }

    override suspend fun projects(): List<TodoistProject> =
        dao.projects().map { TodoistProject(it.id, it.name) }

    override suspend fun setLocallyCompleted(taskId: String, completed: Boolean) {
        dao.setLocallyCompleted(taskId, completed)
    }

    override suspend fun enqueue(op: PendingOp): Long =
        dao.insertPending(PendingOpEntity(taskId = op.taskId, type = op.type.name, attempts = op.attempts))

    override suspend fun pendingOps(): List<PendingOp> =
        dao.pendingOps().map { PendingOp(it.id, it.taskId, PendingOpType.valueOf(it.type), it.attempts) }

    override suspend fun removePending(opId: Long) = dao.removePending(opId)

    override suspend fun bumpAttempts(opId: Long) = dao.bumpAttempts(opId)
}

private fun TodoistTask.toEntity(view: TodoistView) = CachedTaskEntity(
    view = view.name,
    id = id,
    content = content,
    projectId = projectId,
    labels = labels,
    priority = priority,
    parentId = parentId,
    order = order,
    dueDate = due?.date?.toString(),
    dueDateTime = due?.at?.toString(),
    dueIsRecurring = due?.isRecurring ?: false,
    dueText = due?.text,
    locallyCompleted = locallyCompleted,
)

private fun CachedTaskEntity.toDomain(): TodoistTask {
    val date = dueDate?.let { LocalDate.parse(it) }
    val at = dueDateTime?.let { LocalDateTime.parse(it) }
    val due = if (date == null && at == null && dueText.isNullOrEmpty()) {
        null
    } else {
        TodoistDue(date = date ?: at?.toLocalDate(), at = at, isRecurring = dueIsRecurring, text = dueText ?: "")
    }
    return TodoistTask(
        id = id,
        content = content,
        projectId = projectId,
        labels = labels,
        priority = priority,
        due = due,
        parentId = parentId,
        order = order,
        locallyCompleted = locallyCompleted,
    )
}
