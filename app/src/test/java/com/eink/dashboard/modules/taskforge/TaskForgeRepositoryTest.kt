package com.eink.dashboard.modules.taskforge

import android.net.Uri
import com.eink.dashboard.modules.taskforge.data.CachedTaskForgeSnapshot
import com.eink.dashboard.modules.taskforge.data.FileCompletionResult
import com.eink.dashboard.modules.taskforge.data.PersistedTaskForgeFile
import com.eink.dashboard.modules.taskforge.data.TaskForgeFile
import com.eink.dashboard.modules.taskforge.data.TaskForgeFileStore
import com.eink.dashboard.modules.taskforge.data.TaskForgeParser
import com.eink.dashboard.modules.taskforge.data.TaskForgeSnapshotCache
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class TaskForgeRepositoryTest {
    private val settings = TaskForgeSettings(fileUri = "content://tasks", fileName = "TaskForge.md", canWrite = true)

    @Test fun fallsBackToPersistentSnapshotWhenLocalFileIsUnavailable() {
        val cache = FakeCache().apply { save("- [ ] cached 📅 2026-07-18".toByteArray(), 42L) }
        val repo = TaskForgeRepository(FakeFiles().apply { unavailable = true }, cache, { LocalDate.of(2026, 7, 18) })

        val load = repo.refresh(settings)

        assertThat(load).isInstanceOf(TaskForgeLoad.Stale::class.java)
        assertThat((load as TaskForgeLoad.Stale).board.tasks.single().title).isEqualTo("cached")
        assertThat(load.cachedAtEpochMs).isEqualTo(42L)
    }

    @Test fun mapsWriteConflictAndRejectsRecurringBeforeWriting() {
        val files = FakeFiles().apply { completion = FileCompletionResult.Conflict }
        val repo = TaskForgeRepository(files, FakeCache())
        val plain = TaskForgeParser.parse("- [ ] plain".toByteArray()).single()
        val recurring = TaskForgeParser.parse("- [ ] repeat 🔁 every Friday".toByteArray()).single()

        assertThat(repo.complete(settings, plain)).isEqualTo(TaskForgeFailure.CONFLICT)
        assertThat(repo.complete(settings, recurring)).isEqualTo(TaskForgeFailure.RECURRING)
        assertThat(files.completeCalls).isEqualTo(1)
    }

    @Test fun completesOpenNonTodoStatusesPassingTheirActualMarker() {
        val files = FakeFiles()
        val repo = TaskForgeRepository(files, FakeCache())
        val inProgress = TaskForgeParser.parse("- [/] running".toByteArray()).single()
        val deferred = TaskForgeParser.parse("- [>] later".toByteArray()).single()
        val onHold = TaskForgeParser.parse("- [!] blocked".toByteArray()).single()

        assertThat(repo.complete(settings, inProgress)).isNull()
        assertThat(repo.complete(settings, deferred)).isNull()
        assertThat(repo.complete(settings, onHold)).isNull()
        assertThat(files.markers).containsExactly('/', '>', '!').inOrder()
    }

    @Test fun refusesDoneAndUnknownMarkers() {
        val files = FakeFiles()
        val repo = TaskForgeRepository(files, FakeCache())
        val done = TaskForgeParser.parse("- [x] shipped".toByteArray()).single()
        val unknown = TaskForgeParser.parse("- [?] custom".toByteArray()).single()

        assertThat(repo.complete(settings, done)).isEqualTo(TaskForgeFailure.CONFLICT)
        assertThat(repo.complete(settings, unknown)).isEqualTo(TaskForgeFailure.CONFLICT)
        assertThat(files.completeCalls).isEqualTo(0)
    }
}

private class FakeFiles : TaskForgeFileStore {
    var unavailable = false
    var completion: FileCompletionResult = FileCompletionResult.Done
    var completeCalls = 0
    val markers = mutableListOf<Char>()
    override fun persist(uri: Uri) = PersistedTaskForgeFile(uri.toString(), "TaskForge.md", true)
    override fun release(uri: String) = Unit
    override fun read(uri: String): TaskForgeFile {
        if (unavailable) error("offline")
        return TaskForgeFile("- [ ] live".toByteArray(), "TaskForge.md", true)
    }
    override fun complete(
        uri: String,
        source: com.eink.dashboard.modules.taskforge.model.TaskSourceRef,
        expectedMarker: Char,
    ): FileCompletionResult {
        completeCalls++
        markers += expectedMarker
        return completion
    }
}

private class FakeCache : TaskForgeSnapshotCache {
    private var value: CachedTaskForgeSnapshot? = null
    override fun load() = value
    override fun save(bytes: ByteArray, savedAtEpochMs: Long) { value = CachedTaskForgeSnapshot(bytes, savedAtEpochMs) }
    override fun clear() { value = null }
}
