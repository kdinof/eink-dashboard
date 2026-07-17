package com.eink.dashboard.modules.todoist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.todoist.data.TodoistError
import com.eink.dashboard.modules.todoist.model.TodoistView
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

/**
 * The module's state machine over the frozen [ModuleState] contract: token gating
 * (no network without a token), success/empty/error mapping, and optimistic hide.
 * Uses fakes + a real in-memory settings store — no device, no Robolectric.
 */
class TodoistModuleTest {

    private val today = LocalDate.of(2026, 7, 17)

    private fun tempStore(): TodoistSettingsStore {
        val dir = Files.createTempDirectory("todoist-settings").toFile()
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { File(dir, "todoist_settings.preferences_pb") }
        return TodoistSettingsStore(ds)
    }

    private fun module(
        api: FakeTodoistApi,
        token: String?,
        cache: FakeTaskCache = FakeTaskCache(),
    ): TodoistModule = TodoistModule(
        repo = TodoistRepository(api, cache, today = { today }),
        settingsStore = tempStore(),
        tokenStore = FakeTokenStore(token),
        clock = { 1_000L },
    )

    @Test
    fun contractIdentity_isStable() {
        val m = module(FakeTodoistApi(), token = "t")
        assertThat(m.id).isEqualTo("todoist")
        assertThat(m.title).isEqualTo("Todoist")
        assertThat(m.refreshPolicy).isInstanceOf(RefreshPolicy.Periodic::class.java)
        assertThat(m.isDemo).isFalse()
        assertThat(m.hasSettings).isTrue()
    }

    @Test
    fun noToken_yieldsError_andNeverCallsApi(): Unit = runBlocking {
        val api = FakeTodoistApi().apply { setSingleTaskPage(listOf(Fixtures.task("1", date = today))) }
        val m = module(api, token = null)
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Error::class.java)
        assertThat(m.hasToken.value).isFalse()
        assertThat(api.taskCalls).isEqualTo(0)
        assertThat(m.board.value).isNull()
    }

    @Test
    fun tokenWithTasks_yieldsOk_andPopulatesBoard(): Unit = runBlocking {
        val api = FakeTodoistApi().apply { setSingleTaskPage(listOf(Fixtures.task("1", date = today))) }
        val m = module(api, token = "t")
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Ok::class.java)
        assertThat(m.board.value?.days?.single()?.roots?.single()?.task?.id).isEqualTo("1")
    }

    @Test
    fun tokenWithNoTasks_yieldsEmpty(): Unit = runBlocking {
        val m = module(FakeTodoistApi(), token = "t")
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Empty::class.java)
    }

    @Test
    fun networkErrorWithNoCache_yieldsError(): Unit = runBlocking {
        val api = FakeTodoistApi().apply { tasksError = TodoistError.Network }
        val m = module(api, token = "t")
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Error::class.java)
    }

    @Test
    fun networkErrorAfterSuccess_marksStale_keepsBoard(): Unit = runBlocking {
        val cache = FakeTaskCache()
        val api = FakeTodoistApi().apply { setSingleTaskPage(listOf(Fixtures.task("1", date = today))) }
        val m = module(api, token = "t", cache = cache)
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Ok::class.java)

        api.tasksError = TodoistError.Network
        m.refresh(RefreshReason.MINUTE_TICK)
        val state = m.state.value
        assertThat(state).isInstanceOf(ModuleState.Ok::class.java)
        assertThat((state as ModuleState.Ok).isStale).isTrue()
        assertThat(m.board.value?.days).isNotEmpty()
    }

    @Test
    fun complete_hidesTaskFromBoardImmediately(): Unit = runBlocking {
        val cache = FakeTaskCache()
        val api = FakeTodoistApi().apply { setSingleTaskPage(listOf(Fixtures.task("1", date = today))) }
        val m = module(api, token = "t", cache = cache)
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.board.value?.isEmpty).isFalse()

        m.complete("1")
        // Optimistically dropped from the rendered board.
        assertThat(m.board.value?.isEmpty).isTrue()
        assertThat(api.closedIds).containsExactly("1")
    }
}
