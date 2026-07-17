package com.eink.dashboard.modules.todoist.data

import com.eink.dashboard.modules.todoist.model.TodoistProject
import com.eink.dashboard.modules.todoist.model.TodoistTask
import com.eink.dashboard.modules.todoist.model.TodoistView

/**
 * Local cache of tasks, projects and the durable pending-operation queue. A seam
 * (interface) over Room so repository logic — optimistic completion, offline
 * queueing, retry — is unit-tested with an in-memory fake, while the real
 * [com.eink.dashboard.modules.todoist.data.room.RoomTaskCache] is thin glue.
 *
 * The cache stores **task data only**. The personal token is never written here —
 * it lives solely in the Keystore-backed [com.eink.dashboard.modules.todoist.security.TokenStore].
 */
interface TaskCache {

    /** Replace the cached task set for [view] with [tasks] (a fresh server load). */
    suspend fun replaceTasks(view: TodoistView, tasks: List<TodoistTask>)

    /** Cached tasks for [view], including any locally-completed flag applied. */
    suspend fun tasks(view: TodoistView): List<TodoistTask>

    /** Replace the cached project list. */
    suspend fun replaceProjects(projects: List<TodoistProject>)

    /** Cached projects for id → name resolution. */
    suspend fun projects(): List<TodoistProject>

    /** Set/clear the optimistic-completion flag for a task across all cached views. */
    suspend fun setLocallyCompleted(taskId: String, completed: Boolean)

    /** Append a durable operation to the pending queue; returns its assigned id. */
    suspend fun enqueue(op: PendingOp): Long

    /** All queued operations, oldest first. */
    suspend fun pendingOps(): List<PendingOp>

    /** Remove a completed/abandoned operation from the queue. */
    suspend fun removePending(opId: Long)

    /** Record another failed send attempt (for capped retry / diagnostics). */
    suspend fun bumpAttempts(opId: Long)
}

/** The kind of durable operation queued for retry. Only completion is offline-safe. */
enum class PendingOpType { COMPLETE }

/**
 * A durable, replayable operation. Survives process death in Room so an offline
 * completion is retried on the next refresh. [attempts] caps runaway retries.
 */
data class PendingOp(
    val id: Long = 0,
    val taskId: String,
    val type: PendingOpType,
    val attempts: Int = 0,
) {
    companion object {
        /** After this many failed sends the op is abandoned and the task restored. */
        const val MAX_ATTEMPTS = 8
    }
}
