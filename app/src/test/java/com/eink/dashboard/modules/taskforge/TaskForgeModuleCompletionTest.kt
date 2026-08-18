package com.eink.dashboard.modules.taskforge

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.taskforge.data.CachedTaskForgeSnapshot
import com.eink.dashboard.modules.taskforge.data.FileCompletionResult
import com.eink.dashboard.modules.taskforge.data.PersistedTaskForgeFile
import com.eink.dashboard.modules.taskforge.data.TaskForgeFile
import com.eink.dashboard.modules.taskforge.data.TaskForgeFileStore
import com.eink.dashboard.modules.taskforge.data.TaskForgeParser
import com.eink.dashboard.modules.taskforge.data.TaskForgeSnapshotCache
import com.eink.dashboard.modules.taskforge.model.TaskForgeView
import com.eink.dashboard.modules.taskforge.model.TaskSourceRef
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/**
 * End-to-end completion through the real [TaskForgeModule] + [TaskForgeParser]:
 * tapping a task must flip exactly its checkbox byte to `x` in the Markdown
 * source, refresh the board, and surface failures via the notice.
 */
class TaskForgeModuleCompletionTest {

    // Mirrors the user's vault shape: Cyrillic titles (multi-byte UTF-8 before
    // the tasks under test), emoji metadata, nested and non-TODO statuses.
    private val vault = """
        # TaskForge
        ## Backlog
        - [ ] Закрыть честно ⏫ 📅 2026-08-18 #consulting
        - [/] Пробежка 🔽 📅 2026-08-18 #life
        - [ ] Friday review 🔁 every Friday #consulting
        - [x] Готово ✅ 2026-08-17 #life
    """.trimIndent().toByteArray()

    @Test fun completingTodoTaskFlipsItsCheckboxByteAndRemovesItFromTheBoard() = runTest {
        val files = InMemoryFileStore(vault.copyOf())
        val module = module(files)
        module.refresh(RefreshReason.MANUAL)

        val task = module.board.value!!.tasks.first { it.title == "Закрыть честно" }
        module.complete(task)

        val text = files.bytes.toString(Charsets.UTF_8)
        assertThat(text).contains("- [x] Закрыть честно ⏫ 📅 2026-08-18 #consulting")
        assertThat(text).contains("- [/] Пробежка") // neighbours untouched
        assertThat(module.notice.value).isNull()
        assertThat(module.board.value!!.tasks.map { it.title }).doesNotContain("Закрыть честно")
    }

    @Test fun completingInProgressTaskWorksToo() = runTest {
        val files = InMemoryFileStore(vault.copyOf())
        val module = module(files)
        module.refresh(RefreshReason.MANUAL)

        val task = module.board.value!!.tasks.first { it.title == "Пробежка" }
        assertThat(task.canComplete).isTrue()
        module.complete(task)

        assertThat(files.bytes.toString(Charsets.UTF_8))
            .contains("- [x] Пробежка 🔽 📅 2026-08-18 #life")
        assertThat(module.notice.value).isNull()
    }

    @Test fun recurringTaskIsNotCompletableAndFileStaysUntouched() = runTest {
        val files = InMemoryFileStore(vault.copyOf())
        val module = module(files, view = TaskForgeView.ALL_OPEN)
        module.refresh(RefreshReason.MANUAL)

        val task = module.board.value!!.tasks.first { it.title == "Friday review" }
        assertThat(task.canComplete).isFalse()
        module.complete(task)

        assertThat(files.bytes).isEqualTo(vault)
        assertThat(module.notice.value).isEqualTo("Complete recurring tasks in TaskForge")
    }

    @Test fun readOnlyFileSurfacesNoticeAndKeepsTheTask() = runTest {
        val files = InMemoryFileStore(vault.copyOf(), canWrite = false)
        val module = module(files)
        module.refresh(RefreshReason.MANUAL)

        val task = module.board.value!!.tasks.first { it.title == "Закрыть честно" }
        module.complete(task)

        assertThat(module.notice.value).isEqualTo("Read-only file — re-pick it in Settings via Internal storage")
        assertThat(module.board.value!!.tasks.map { it.title }).contains("Закрыть честно")
    }

    @Test fun taskEditedElsewhereBetweenReadAndTapIsAConflictNotAWrongWrite() = runTest {
        val files = InMemoryFileStore(vault.copyOf())
        val module = module(files)
        module.refresh(RefreshReason.MANUAL)
        val task = module.board.value!!.tasks.first { it.title == "Закрыть честно" }

        // The vault syncs a new version where that line was reworded.
        files.bytes = vault.toString(Charsets.UTF_8)
            .replace("Закрыть честно ⏫", "Закрыть мягко ⏫")
            .toByteArray()
        module.complete(task)

        assertThat(module.notice.value).isEqualTo("Task changed elsewhere — list refreshed")
        assertThat(files.bytes.toString(Charsets.UTF_8)).doesNotContain("[x] Закрыть")
    }

    private suspend fun module(
        files: InMemoryFileStore,
        view: TaskForgeView = TaskForgeView.TODAY,
    ): TaskForgeModule {
        val settingsStore = TaskForgeSettingsStore(InMemoryPreferencesDataStore())
        settingsStore.setFile("content://vault/TaskForge.md", "TaskForge.md", files.canWrite)
        settingsStore.setFilter(view, emptySet(), 10)
        return TaskForgeModule(
            repository = TaskForgeRepository(files, InMemoryCache(), { LocalDate.of(2026, 8, 18) }),
            settingsStore = settingsStore,
        )
    }
}

/**
 * In-memory stand-in for [com.eink.dashboard.modules.taskforge.data.AndroidTaskForgeFileStore]
 * with the same completion contract: re-resolve the offset against the current
 * bytes, verify the expected marker byte, then flip it to `x` in place.
 */
private class InMemoryFileStore(
    var bytes: ByteArray,
    val canWrite: Boolean = true,
) : TaskForgeFileStore {
    override fun persist(uri: Uri) = PersistedTaskForgeFile(uri.toString(), "TaskForge.md", canWrite)
    override fun release(uri: String) = Unit
    override fun read(uri: String) = TaskForgeFile(bytes.copyOf(), "TaskForge.md", canWrite)

    override fun complete(uri: String, source: TaskSourceRef, expectedMarker: Char): FileCompletionResult {
        if (!canWrite) return FileCompletionResult.ReadOnly
        val offset = TaskForgeParser.resolveCheckboxOffset(bytes, source) ?: return FileCompletionResult.Conflict
        if (offset < 0 || offset >= bytes.size || bytes[offset.toInt()] != expectedMarker.code.toByte()) {
            return FileCompletionResult.Conflict
        }
        bytes[offset.toInt()] = 'x'.code.toByte()
        return FileCompletionResult.Done
    }
}

private class InMemoryCache : TaskForgeSnapshotCache {
    private var value: CachedTaskForgeSnapshot? = null
    override fun load() = value
    override fun save(bytes: ByteArray, savedAtEpochMs: Long) { value = CachedTaskForgeSnapshot(bytes, savedAtEpochMs) }
    override fun clear() { value = null }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        state.value = transform(state.value)
        return state.value
    }
}
