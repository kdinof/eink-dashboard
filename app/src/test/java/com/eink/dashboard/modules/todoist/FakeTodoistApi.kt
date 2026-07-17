package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.data.ProjectPage
import com.eink.dashboard.modules.todoist.data.TaskPage
import com.eink.dashboard.modules.todoist.data.TodoistApi
import com.eink.dashboard.modules.todoist.data.TodoistError
import com.eink.dashboard.modules.todoist.model.TodoistProject
import com.eink.dashboard.modules.todoist.model.TodoistTask

/**
 * In-memory [TodoistApi] for JVM tests — no device, no network, no token. Tasks are
 * returned in [taskChunks] (one chunk per cursor page) so pagination can be exercised;
 * failures are injected per operation to drive the error paths.
 */
class FakeTodoistApi : TodoistApi {

    var taskChunks: List<List<TodoistTask>> = listOf(emptyList())
    var projectList: List<TodoistProject> = emptyList()

    var tasksError: TodoistError? = null
    var projectsError: TodoistError? = null
    var defaultCloseError: TodoistError? = null
    val closeErrors: MutableMap<String, TodoistError?> = mutableMapOf()

    val closedIds: MutableList<String> = mutableListOf()
    var lastQuery: String? = null
    var taskCalls: Int = 0
    var closeCalls: Int = 0

    fun setSingleTaskPage(tasks: List<TodoistTask>) {
        taskChunks = listOf(tasks)
    }

    override suspend fun tasksByFilter(query: String, cursor: String?): TaskPage {
        taskCalls++
        tasksError?.let { throw it }
        lastQuery = query
        val idx = cursor?.toIntOrNull() ?: 0
        val chunk = taskChunks.getOrElse(idx) { emptyList() }
        val next = if (idx + 1 < taskChunks.size) (idx + 1).toString() else null
        return TaskPage(chunk, next)
    }

    override suspend fun projects(cursor: String?): ProjectPage {
        projectsError?.let { throw it }
        return ProjectPage(projectList, null)
    }

    override suspend fun closeTask(id: String) {
        closeCalls++
        (closeErrors[id] ?: defaultCloseError)?.let { throw it }
        closedIds += id
    }
}
