package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.data.PendingOp
import com.eink.dashboard.modules.todoist.data.PendingOpType
import com.eink.dashboard.modules.todoist.data.TodoistError
import com.eink.dashboard.modules.todoist.model.TodoistView
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate

/** Durable pending-operation queue: offline retry, exhaustion, and recurring tasks. */
class PendingOpRetryTest {

    private val today = LocalDate.of(2026, 7, 17)

    private fun repo(api: FakeTodoistApi, cache: FakeTaskCache) =
        TodoistRepository(api, cache, today = { today })

    @Test
    fun offlineCompletion_isRetriedOnNextRefresh(): Unit = runBlocking {
        val cache = FakeTaskCache()
        cache.replaceTasks(TodoistView.TODAY, listOf(Fixtures.task("1", date = today)))

        // First: offline → op stays queued, task hidden.
        val offline = FakeTodoistApi().apply { defaultCloseError = TodoistError.Network }
        repo(offline, cache).complete("1")
        assertThat(cache.pending).hasSize(1)

        // Later: back online → refresh drains the queue and clears it.
        val online = FakeTodoistApi().apply { setSingleTaskPage(emptyList()) }
        repo(online, cache).refresh(TodoistView.TODAY)
        assertThat(online.closedIds).containsExactly("1")
        assertThat(cache.pending).isEmpty()
    }

    @Test
    fun drainStopsOnFirstRetryableError_leavingRestQueued(): Unit = runBlocking {
        val cache = FakeTaskCache()
        cache.enqueue(PendingOp(taskId = "a", type = PendingOpType.COMPLETE))
        cache.enqueue(PendingOp(taskId = "b", type = PendingOpType.COMPLETE))

        val api = FakeTodoistApi().apply {
            closeErrors["a"] = TodoistError.Network // first op fails → stop
        }
        val error = repo(api, cache).drainPending()

        assertThat(error).isEqualTo(TodoistError.Network)
        assertThat(api.closedIds).isEmpty() // never reached "b"
        assertThat(cache.pending.map { it.taskId }).containsExactly("a", "b")
    }

    @Test
    fun exhaustedRetries_abandonOp_andRestoreTask(): Unit = runBlocking {
        val cache = FakeTaskCache()
        cache.replaceTasks(TodoistView.TODAY, listOf(Fixtures.task("1", date = today).copy(locallyCompleted = true)))
        // One attempt short of the cap; the next retryable failure abandons it.
        cache.enqueue(PendingOp(taskId = "1", type = PendingOpType.COMPLETE, attempts = PendingOp.MAX_ATTEMPTS - 1))

        val api = FakeTodoistApi().apply { defaultCloseError = TodoistError.Network }
        repo(api, cache).drainPending()

        assertThat(cache.pending).isEmpty() // abandoned
        assertThat(cache.tasks(TodoistView.TODAY).single().locallyCompleted).isFalse() // restored
    }

    @Test
    fun recurringTask_reappearsWithAdvancedDate_afterCompletion(): Unit = runBlocking {
        val cache = FakeTaskCache()
        // A recurring task completed offline → queued and hidden.
        cache.replaceTasks(TodoistView.UPCOMING, listOf(Fixtures.task("r", date = today, recurring = true)))
        val offline = FakeTodoistApi().apply { defaultCloseError = TodoistError.Network }
        repo(offline, cache).complete("r")
        assertThat(cache.pending).hasSize(1)

        // Back online: the server accepted the close and advanced the recurrence; the
        // next fresh load returns the task with its new date.
        val online = FakeTodoistApi().apply {
            setSingleTaskPage(listOf(Fixtures.task("r", date = today.plusDays(1), recurring = true)))
        }
        val load = repo(online, cache).refresh(TodoistView.UPCOMING)

        assertThat(online.closedIds).containsExactly("r")
        assertThat(cache.pending).isEmpty()
        val board = (load as TodoistLoad.Fresh).board
        val node = board.days.single().roots.single()
        assertThat(node.task.id).isEqualTo("r")
        assertThat(node.task.due?.date).isEqualTo(today.plusDays(1))
    }
}
