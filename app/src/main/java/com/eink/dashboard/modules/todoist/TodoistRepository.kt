package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.data.PendingOp
import com.eink.dashboard.modules.todoist.data.PendingOpType
import com.eink.dashboard.modules.todoist.data.TaskCache
import com.eink.dashboard.modules.todoist.data.TodoistApi
import com.eink.dashboard.modules.todoist.data.TodoistError
import com.eink.dashboard.modules.todoist.model.TaskTreeBuilder
import com.eink.dashboard.modules.todoist.model.TodoistBoard
import com.eink.dashboard.modules.todoist.model.TodoistFilters
import com.eink.dashboard.modules.todoist.model.TodoistProject
import com.eink.dashboard.modules.todoist.model.TodoistTask
import com.eink.dashboard.modules.todoist.model.TodoistView
import java.time.LocalDate

/** Outcome of a [TodoistRepository.refresh]. */
sealed interface TodoistLoad {
    /** A successful network load; [board] is authoritative. */
    data class Fresh(val board: TodoistBoard) : TodoistLoad

    /** The network failed but a cached board is shown instead. */
    data class Stale(val board: TodoistBoard, val error: TodoistError) : TodoistLoad

    /** The network failed and there is nothing cached to show. */
    data class Failed(val error: TodoistError) : TodoistLoad
}

/** Outcome of a completion attempt (immediate or drained). */
sealed interface CompleteResult {
    /** The task was accepted by the server (or a recurring task advanced). */
    data object Done : CompleteResult

    /** Couldn't reach the server; the op is queued and will retry (task stays hidden). */
    data class Queued(val error: TodoistError) : CompleteResult

    /** The server rejected it permanently; the task has been restored to the list. */
    data class Rejected(val error: TodoistError) : CompleteResult
}

/**
 * Orchestrates the Todoist module: network + [TaskCache] + the durable pending-op
 * queue, with optimistic completion and offline-tolerant retry. Pure of Android and
 * of any secret (the token is applied by the API client's interceptor), so it is
 * exercised end-to-end with a fake API and an in-memory cache.
 *
 * Network resilience contract (T04):
 * - `refresh` first drains the pending queue (retrying offline completions), then
 *   loads fresh data; on any [TodoistError] it falls back to the cached board and
 *   reports it as [TodoistLoad.Stale] (or [TodoistLoad.Failed] if nothing cached).
 * - `complete` marks the task done locally at once, enqueues a durable op, and tries
 *   to send it. A retryable failure keeps it queued ([CompleteResult.Queued]); a
 *   terminal rejection rolls the optimistic change back ([CompleteResult.Rejected]).
 * - Recurring tasks: the server advances them on `close`; a later fresh load surfaces
 *   the next occurrence, so no special client handling beyond re-reading is needed.
 */
class TodoistRepository(
    private val api: TodoistApi,
    private val cache: TaskCache,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    /** Retry queued completions, then load [view] fresh — falling back to cache. */
    suspend fun refresh(view: TodoistView): TodoistLoad {
        drainPending()
        return try {
            val projects = fetchAllProjects()
            val tasks = fetchAllTasks(TodoistFilters.queryFor(view))
            cache.replaceProjects(projects)
            cache.replaceTasks(view, tasks)
            // Keep optimistically-completed tasks hidden even across a fresh load
            // until their queued op is confirmed.
            reapplyPendingOptimism()
            TodoistLoad.Fresh(buildBoard(view, cache.tasks(view), projects))
        } catch (e: TodoistError) {
            val cached = cache.tasks(view)
            if (cached.isNotEmpty()) {
                TodoistLoad.Stale(buildBoard(view, cached, cache.projects()), e)
            } else {
                TodoistLoad.Failed(e)
            }
        }
    }

    /**
     * Optimistically complete [taskId]: hide it, queue a durable op, try to send.
     * Returns immediately with the send outcome; the UI reflects [CompleteResult].
     */
    suspend fun complete(taskId: String): CompleteResult {
        cache.setLocallyCompleted(taskId, true)
        val opId = cache.enqueue(PendingOp(taskId = taskId, type = PendingOpType.COMPLETE))
        return try {
            api.closeTask(taskId)
            cache.removePending(opId)
            CompleteResult.Done
        } catch (e: TodoistError) {
            if (e.isRetryable) {
                CompleteResult.Queued(e)
            } else {
                // Permanent rejection — undo the optimistic hide and drop the op.
                cache.removePending(opId)
                cache.setLocallyCompleted(taskId, false)
                CompleteResult.Rejected(e)
            }
        }
    }

    /** Rebuild the board from the cache only (no network) — for instant optimistic UI. */
    suspend fun cachedBoard(view: TodoistView): TodoistBoard =
        buildBoard(view, cache.tasks(view), cache.projects())

    /** Verify a freshly-entered token with one cheap request. `null` == valid. */
    suspend fun verifyToken(): TodoistError? = try {
        api.projects(cursor = null)
        null
    } catch (e: TodoistError) {
        e
    }

    /**
     * Replay queued completions oldest-first. Stops at the first retryable failure
     * (no point hammering while offline / rate-limited); abandons an op that has
     * exhausted [PendingOp.MAX_ATTEMPTS] or was permanently rejected, restoring the
     * task. Returns the error that halted the drain, or `null` if the queue cleared.
     */
    suspend fun drainPending(): TodoistError? {
        for (op in cache.pendingOps()) {
            try {
                api.closeTask(op.taskId)
                cache.removePending(op.id)
            } catch (e: TodoistError) {
                if (e.isRetryable) {
                    if (op.attempts + 1 >= PendingOp.MAX_ATTEMPTS) {
                        abandon(op)
                    } else {
                        cache.bumpAttempts(op.id)
                    }
                    return e // leave the rest queued for the next refresh
                }
                // Permanent rejection: restore the task and drop this op.
                abandon(op)
                if (e is TodoistError.Unauthorized) return e // token is bad for all ops
            }
        }
        return null
    }

    private suspend fun abandon(op: PendingOp) {
        cache.removePending(op.id)
        cache.setLocallyCompleted(op.taskId, false)
    }

    private suspend fun reapplyPendingOptimism() {
        cache.pendingOps().forEach { cache.setLocallyCompleted(it.taskId, true) }
    }

    private fun buildBoard(
        view: TodoistView,
        tasks: List<TodoistTask>,
        projects: List<TodoistProject>,
    ): TodoistBoard = TodoistBoard(
        view = view,
        days = TaskTreeBuilder.build(tasks, today()),
        projectNames = projects.associate { it.id to it.name },
    )

    private suspend fun fetchAllTasks(query: String): List<TodoistTask> {
        val all = mutableListOf<TodoistTask>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = api.tasksByFilter(query, cursor)
            all += page.tasks
            cursor = page.nextCursor
            pages++
        } while (cursor != null && pages < MAX_PAGES)
        return all
    }

    private suspend fun fetchAllProjects(): List<TodoistProject> {
        val all = mutableListOf<TodoistProject>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = api.projects(cursor)
            all += page.projects
            cursor = page.nextCursor
            pages++
        } while (cursor != null && pages < MAX_PAGES)
        return all
    }

    private companion object {
        /** Safety bound on pagination — a personal account is far under this. */
        const val MAX_PAGES = 25
    }
}
