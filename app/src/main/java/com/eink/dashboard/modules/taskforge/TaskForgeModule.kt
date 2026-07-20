package com.eink.dashboard.modules.taskforge

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.taskforge.data.AndroidTaskForgeFileStore
import com.eink.dashboard.modules.taskforge.data.SharedPreferencesTaskForgeSnapshotCache
import com.eink.dashboard.modules.taskforge.model.TaskForgeBoard
import com.eink.dashboard.modules.taskforge.model.TaskForgeTask
import com.eink.dashboard.modules.taskforge.ui.TaskForgeContent
import com.eink.dashboard.modules.taskforge.ui.TaskForgeSettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

class TaskForgeModule(
    private val repository: TaskForgeRepository,
    val settingsStore: TaskForgeSettingsStore,
) : DashboardModule {
    override val id = "taskforge"
    override val title = "TaskForge"
    override val refreshPolicy = RefreshPolicy.Periodic(1.minutes)
    override val hasSettings = true

    private val _state = MutableStateFlow<ModuleState>(ModuleState.Loading)
    override val state: StateFlow<ModuleState> = _state.asStateFlow()
    private val _board = MutableStateFlow<TaskForgeBoard?>(null)
    val board: StateFlow<TaskForgeBoard?> = _board.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    private val _lastLocalRead = MutableStateFlow<Long?>(null)
    val lastLocalRead: StateFlow<Long?> = _lastLocalRead.asStateFlow()

    override suspend fun refresh(reason: RefreshReason) {
        val settings = settingsStore.current()
        when (val load = withContext(Dispatchers.IO) { repository.refresh(settings) }) {
            is TaskForgeLoad.Fresh -> {
                _board.value = load.board
                _lastLocalRead.value = load.readAtEpochMs
                if (load.fileName != settings.fileName || load.canWrite != settings.canWrite) {
                    settingsStore.setFile(settings.fileUri!!, load.fileName, load.canWrite)
                }
                _state.value = if (load.board.isEmpty) ModuleState.Empty(load.readAtEpochMs)
                else ModuleState.Ok(load.readAtEpochMs)
            }
            is TaskForgeLoad.Stale -> {
                _board.value = load.board
                _lastLocalRead.value = load.cachedAtEpochMs
                _state.value = ModuleState.Ok(load.cachedAtEpochMs, isStale = true)
            }
            is TaskForgeLoad.Failed -> {
                _state.value = ModuleState.Error(messageFor(load.reason), _lastLocalRead.value)
            }
        }
    }

    suspend fun connectFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connected = repository.connect(uri)
            settingsStore.setFile(connected.uri, connected.name, connected.canWrite)
        }.exceptionOrNull()?.message
    }.also { error ->
        _notice.value = error?.let { "Couldn't open Markdown file" }
        if (error == null) refresh(RefreshReason.SETTINGS_CHANGED)
    }

    suspend fun disconnectFile() {
        val current = settingsStore.current()
        withContext(Dispatchers.IO) { repository.disconnect(current.fileUri) }
        settingsStore.clearFile()
        _board.value = null
        _lastLocalRead.value = null
        _state.value = ModuleState.Error(messageFor(TaskForgeFailure.NOT_CONFIGURED), null)
    }

    suspend fun updateFilter(view: com.eink.dashboard.modules.taskforge.model.TaskForgeView, tags: Set<String>, limit: Int) {
        settingsStore.setFilter(view, tags, limit)
        refresh(RefreshReason.SETTINGS_CHANGED)
    }

    suspend fun complete(task: TaskForgeTask) {
        val failure = withContext(Dispatchers.IO) { repository.complete(settingsStore.current(), task) }
        _notice.value = failure?.let(::messageFor)
        refresh(RefreshReason.MANUAL)
    }

    fun clearNotice() { _notice.value = null }

    @Composable override fun Content(modifier: Modifier) = TaskForgeContent(this, modifier)
    @Composable override fun SettingsContent(modifier: Modifier) = TaskForgeSettingsSection(this, settingsStore, modifier)

    private fun messageFor(failure: TaskForgeFailure): String = when (failure) {
        TaskForgeFailure.NOT_CONFIGURED -> "Choose TaskForge.md in Settings"
        TaskForgeFailure.UNAVAILABLE -> "Local Markdown file is unavailable"
        TaskForgeFailure.CONFLICT -> "Task changed elsewhere — list refreshed"
        TaskForgeFailure.READ_ONLY -> "File is read-only — complete it in TaskForge"
        TaskForgeFailure.RECURRING -> "Complete recurring tasks in TaskForge"
    }

    companion object {
        fun create(context: Context): TaskForgeModule {
            val app = context.applicationContext
            return TaskForgeModule(
                repository = TaskForgeRepository(
                    files = AndroidTaskForgeFileStore(app),
                    cache = SharedPreferencesTaskForgeSnapshotCache(app),
                ),
                settingsStore = TaskForgeSettingsStore(app),
            )
        }
    }
}
