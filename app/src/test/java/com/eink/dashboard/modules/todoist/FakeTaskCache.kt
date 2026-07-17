package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.data.PendingOp
import com.eink.dashboard.modules.todoist.data.TaskCache
import com.eink.dashboard.modules.todoist.model.TodoistProject
import com.eink.dashboard.modules.todoist.model.TodoistTask
import com.eink.dashboard.modules.todoist.model.TodoistView

/**
 * In-memory [TaskCache] for JVM tests. Mirrors the Room semantics that matter to the
 * repository: per-view task replacement, optimistic-completion flag shared across
 * views by task id, and an auto-incrementing durable pending-op queue.
 */
class FakeTaskCache : TaskCache {

    private val tasksByView: MutableMap<TodoistView, MutableList<TodoistTask>> = mutableMapOf()
    private var projectList: List<TodoistProject> = emptyList()
    val pending: MutableList<PendingOp> = mutableListOf()
    private var nextId = 1L

    override suspend fun replaceTasks(view: TodoistView, tasks: List<TodoistTask>) {
        tasksByView[view] = tasks.toMutableList()
    }

    override suspend fun tasks(view: TodoistView): List<TodoistTask> =
        tasksByView[view]?.toList() ?: emptyList()

    override suspend fun replaceProjects(projects: List<TodoistProject>) {
        projectList = projects.toList()
    }

    override suspend fun projects(): List<TodoistProject> = projectList

    override suspend fun setLocallyCompleted(taskId: String, completed: Boolean) {
        tasksByView.values.forEach { list ->
            list.replaceAll { if (it.id == taskId) it.copy(locallyCompleted = completed) else it }
        }
    }

    override suspend fun enqueue(op: PendingOp): Long {
        val id = nextId++
        pending += op.copy(id = id)
        return id
    }

    override suspend fun pendingOps(): List<PendingOp> = pending.sortedBy { it.id }

    override suspend fun removePending(opId: Long) {
        pending.removeAll { it.id == opId }
    }

    override suspend fun bumpAttempts(opId: Long) {
        val idx = pending.indexOfFirst { it.id == opId }
        if (idx >= 0) pending[idx] = pending[idx].copy(attempts = pending[idx].attempts + 1)
    }
}
