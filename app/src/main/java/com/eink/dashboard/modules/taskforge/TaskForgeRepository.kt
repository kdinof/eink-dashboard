package com.eink.dashboard.modules.taskforge

import android.net.Uri
import com.eink.dashboard.modules.taskforge.data.CachedTaskForgeSnapshot
import com.eink.dashboard.modules.taskforge.data.FileCompletionResult
import com.eink.dashboard.modules.taskforge.data.PersistedTaskForgeFile
import com.eink.dashboard.modules.taskforge.data.TaskForgeFileStore
import com.eink.dashboard.modules.taskforge.data.TaskForgeParser
import com.eink.dashboard.modules.taskforge.data.TaskForgeSnapshotCache
import com.eink.dashboard.modules.taskforge.model.TaskForgeBoard
import com.eink.dashboard.modules.taskforge.model.TaskForgeTask
import java.time.LocalDate

sealed interface TaskForgeLoad {
    data class Fresh(val board: TaskForgeBoard, val fileName: String, val canWrite: Boolean, val readAtEpochMs: Long) : TaskForgeLoad
    data class Stale(val board: TaskForgeBoard, val cachedAtEpochMs: Long) : TaskForgeLoad
    data class Failed(val reason: TaskForgeFailure) : TaskForgeLoad
}

enum class TaskForgeFailure { NOT_CONFIGURED, UNAVAILABLE, CONFLICT, READ_ONLY, RECURRING }

class TaskForgeRepository(
    private val files: TaskForgeFileStore,
    private val cache: TaskForgeSnapshotCache,
    private val today: () -> LocalDate = LocalDate::now,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun connect(uri: Uri): PersistedTaskForgeFile = files.persist(uri)

    fun disconnect(uri: String?) {
        uri?.let(files::release)
        cache.clear()
    }

    fun refresh(settings: TaskForgeSettings): TaskForgeLoad {
        val uri = settings.fileUri ?: return TaskForgeLoad.Failed(TaskForgeFailure.NOT_CONFIGURED)
        return try {
            val file = files.read(uri)
            val now = clock()
            cache.save(file.bytes, now)
            TaskForgeLoad.Fresh(
                board = board(file.bytes, settings),
                fileName = file.name,
                canWrite = file.canWrite,
                readAtEpochMs = now,
            )
        } catch (_: Exception) {
            cache.load()?.let { TaskForgeLoad.Stale(board(it.bytes, settings), it.savedAtEpochMs) }
                ?: TaskForgeLoad.Failed(TaskForgeFailure.UNAVAILABLE)
        }
    }

    fun complete(settings: TaskForgeSettings, task: TaskForgeTask): TaskForgeFailure? {
        if (task.isRecurring) return TaskForgeFailure.RECURRING
        if (!task.canComplete) return TaskForgeFailure.CONFLICT
        val uri = settings.fileUri ?: return TaskForgeFailure.NOT_CONFIGURED
        return when (files.complete(uri, task.source)) {
            FileCompletionResult.Done -> null
            FileCompletionResult.Conflict -> TaskForgeFailure.CONFLICT
            FileCompletionResult.ReadOnly -> TaskForgeFailure.READ_ONLY
            FileCompletionResult.Unavailable -> TaskForgeFailure.UNAVAILABLE
        }
    }

    private fun board(bytes: ByteArray, settings: TaskForgeSettings): TaskForgeBoard =
        TaskForgeParser.board(TaskForgeParser.parse(bytes), settings, today())
}
