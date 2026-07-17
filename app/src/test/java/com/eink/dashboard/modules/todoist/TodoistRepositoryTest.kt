package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.data.TodoistError
import com.eink.dashboard.modules.todoist.model.TodoistProject
import com.eink.dashboard.modules.todoist.model.TodoistView
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate

/** End-to-end repository behaviour: load/caching, filters, optimistic completion. */
class TodoistRepositoryTest {

    private val today = LocalDate.of(2026, 7, 17)

    private fun repo(api: FakeTodoistApi, cache: FakeTaskCache = FakeTaskCache()) =
        TodoistRepository(api, cache, today = { today })

    @Test
    fun freshLoad_usesViewFilter_andCachesTasksAndProjects(): Unit = runBlocking {
        val api = FakeTodoistApi().apply {
            setSingleTaskPage(listOf(Fixtures.task("1", projectId = "P", date = today)))
            projectList = listOf(TodoistProject("P", "Work"))
        }
        val cache = FakeTaskCache()
        val load = repo(api, cache).refresh(TodoistView.TODAY)

        assertThat(load).isInstanceOf(TodoistLoad.Fresh::class.java)
        val board = (load as TodoistLoad.Fresh).board
        assertThat(board.projectNames["P"]).isEqualTo("Work")
        assertThat(board.days.single().roots.single().task.id).isEqualTo("1")
        assertThat(api.lastQuery).isEqualTo("overdue | today")
        // Cached for offline reuse.
        assertThat(cache.tasks(TodoistView.TODAY)).hasSize(1)
        assertThat(cache.projects()).hasSize(1)
    }

    @Test
    fun paginatedTasks_areAllFetched(): Unit = runBlocking {
        val api = FakeTodoistApi().apply {
            taskChunks = listOf(
                listOf(Fixtures.task("1", date = today)),
                listOf(Fixtures.task("2", date = today)),
            )
        }
        val load = repo(api).refresh(TodoistView.TODAY) as TodoistLoad.Fresh
        val ids = load.board.days.single().roots.map { it.task.id }
        assertThat(ids).containsExactly("1", "2")
        assertThat(api.taskCalls).isEqualTo(2)
    }

    @Test
    fun networkFailureWithCache_returnsStale(): Unit = runBlocking {
        val cache = FakeTaskCache()
        // Prime the cache with a successful load first.
        val goodApi = FakeTodoistApi().apply {
            setSingleTaskPage(listOf(Fixtures.task("1", date = today)))
        }
        repo(goodApi, cache).refresh(TodoistView.TODAY)

        val badApi = FakeTodoistApi().apply { tasksError = TodoistError.Network }
        val load = repo(badApi, cache).refresh(TodoistView.TODAY)

        assertThat(load).isInstanceOf(TodoistLoad.Stale::class.java)
        assertThat((load as TodoistLoad.Stale).error).isEqualTo(TodoistError.Network)
        assertThat(load.board.days.single().roots.single().task.id).isEqualTo("1")
    }

    @Test
    fun failureWithEmptyCache_returnsFailed(): Unit = runBlocking {
        val api = FakeTodoistApi().apply { tasksError = TodoistError.Unauthorized }
        val load = repo(api).refresh(TodoistView.TODAY)
        assertThat(load).isInstanceOf(TodoistLoad.Failed::class.java)
        assertThat((load as TodoistLoad.Failed).error).isEqualTo(TodoistError.Unauthorized)
    }

    @Test
    fun optimisticCompletion_online_closesTask_andClearsQueue(): Unit = runBlocking {
        val api = FakeTodoistApi()
        val cache = FakeTaskCache()
        val r = repo(api, cache)

        val result = r.complete("42")
        assertThat(result).isEqualTo(CompleteResult.Done)
        assertThat(api.closedIds).containsExactly("42")
        assertThat(cache.pending).isEmpty()
    }

    @Test
    fun optimisticCompletion_terminalError_rollsBack(): Unit = runBlocking {
        val api = FakeTodoistApi().apply { defaultCloseError = TodoistError.Unauthorized }
        val cache = FakeTaskCache()
        cache.replaceTasks(TodoistView.TODAY, listOf(Fixtures.task("42", date = today)))
        val r = repo(api, cache)

        val result = r.complete("42")
        assertThat(result).isInstanceOf(CompleteResult.Rejected::class.java)
        // Optimistic flag rolled back, op dropped → task is visible again.
        assertThat(cache.tasks(TodoistView.TODAY).single().locallyCompleted).isFalse()
        assertThat(cache.pending).isEmpty()
    }

    @Test
    fun completion_offline_keepsTaskHiddenAndQueued(): Unit = runBlocking {
        val api = FakeTodoistApi().apply { defaultCloseError = TodoistError.Network }
        val cache = FakeTaskCache()
        cache.replaceTasks(TodoistView.TODAY, listOf(Fixtures.task("42", date = today)))
        val r = repo(api, cache)

        val result = r.complete("42")
        assertThat(result).isInstanceOf(CompleteResult.Queued::class.java)
        assertThat(cache.tasks(TodoistView.TODAY).single().locallyCompleted).isTrue()
        assertThat(cache.pending).hasSize(1)
    }
}
