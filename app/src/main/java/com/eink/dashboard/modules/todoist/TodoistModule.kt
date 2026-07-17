package com.eink.dashboard.modules.todoist

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.todoist.data.RetrofitTodoistApi
import com.eink.dashboard.modules.todoist.data.TodoistError
import com.eink.dashboard.modules.todoist.data.room.RoomTaskCache
import com.eink.dashboard.modules.todoist.model.TodoistBoard
import com.eink.dashboard.modules.todoist.security.KeystoreTokenStore
import com.eink.dashboard.modules.todoist.security.TokenStore
import com.eink.dashboard.modules.todoist.ui.TodoistContent
import com.eink.dashboard.modules.todoist.ui.TodoistSettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

/**
 * Todoist block (T04) — a pluggable [DashboardModule].
 *
 * The shell drives all cadence via [refreshPolicy] (every 5 minutes plus manual /
 * lifecycle refreshes); this module starts no loop of its own. Each [refresh] gates
 * on a configured token (never touching the network without one), then delegates to
 * [TodoistRepository], which retries queued completions and loads the chosen view
 * with cache fallback. Completion is optimistic and offline-tolerant.
 *
 * State mapping onto the frozen [ModuleState]:
 * - no token → [ModuleState.Error] ("add token"), no request is made;
 * - fresh load with tasks → [ModuleState.Ok]; nothing due → [ModuleState.Empty];
 * - network failure with a cached board → [ModuleState.Ok] `isStale`;
 * - network failure with nothing cached → [ModuleState.Error] (401/429/5xx/offline).
 */
class TodoistModule(
    private val repo: TodoistRepository,
    private val settingsStore: TodoistSettingsStore,
    private val tokenStore: TokenStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : DashboardModule {

    override val id: String = "todoist"
    override val title: String = "Todoist"
    override val refreshPolicy: RefreshPolicy = RefreshPolicy.Periodic(5.minutes)
    override val hasSettings: Boolean = true

    private val _state = MutableStateFlow<ModuleState>(ModuleState.Loading)
    override val state: StateFlow<ModuleState> = _state.asStateFlow()

    /** Latest resolved board for [Content]; null until the first load. */
    private val _board = MutableStateFlow<TodoistBoard?>(null)
    val board: StateFlow<TodoistBoard?> = _board.asStateFlow()

    /** Whether a token is configured, for the settings prompt / gating. */
    private val _hasToken = MutableStateFlow(tokenStore.hasToken())
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    /** A short, transient status line for completion feedback (offline / rejected). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    override suspend fun refresh(reason: RefreshReason) {
        val tokenPresent = tokenStore.hasToken()
        _hasToken.value = tokenPresent
        if (!tokenPresent) {
            _state.value = ModuleState.Error(
                message = "Add your Todoist token in Settings",
                lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs,
            )
            return
        }
        val view = settingsStore.current().view
        when (val load = withContext(Dispatchers.IO) { repo.refresh(view) }) {
            is TodoistLoad.Fresh -> {
                _board.value = load.board
                _state.value = if (load.board.isEmpty) {
                    ModuleState.Empty(lastUpdatedEpochMs = clock())
                } else {
                    ModuleState.Ok(lastUpdatedEpochMs = clock())
                }
            }
            is TodoistLoad.Stale -> {
                _board.value = load.board
                _state.value = ModuleState.Ok(
                    lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs,
                    isStale = true,
                )
            }
            is TodoistLoad.Failed -> {
                _state.value = ModuleState.Error(
                    message = messageFor(load.error),
                    lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs,
                )
            }
        }
    }

    /** Tick a task done: optimistic hide + durable send, then update the board. */
    suspend fun complete(taskId: String) {
        val result = withContext(Dispatchers.IO) { repo.complete(taskId) }
        // Reflect the optimistic (or rolled-back) cache immediately, no network.
        val view = settingsStore.current().view
        _board.value = withContext(Dispatchers.IO) { repo.cachedBoard(view) }
        _notice.value = when (result) {
            is CompleteResult.Done -> null
            is CompleteResult.Queued -> "Saved offline — will retry"
            is CompleteResult.Rejected -> "Couldn't complete — restored"
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    @Composable
    override fun Content(modifier: Modifier) {
        TodoistContent(module = this, modifier = modifier)
    }

    @Composable
    override fun SettingsContent(modifier: Modifier) {
        TodoistSettingsSection(module = this, settingsStore = settingsStore, modifier = modifier)
    }

    /** Save a new token (encrypted), verify it, and reload. `null` == verified OK. */
    suspend fun saveAndVerifyToken(token: String): TodoistError? {
        withContext(Dispatchers.IO) { tokenStore.save(token) }
        _hasToken.value = tokenStore.hasToken()
        val error = withContext(Dispatchers.IO) { repo.verifyToken() }
        if (error == null) refresh(RefreshReason.SETTINGS_CHANGED)
        return error
    }

    suspend fun clearToken() {
        withContext(Dispatchers.IO) { tokenStore.clear() }
        _hasToken.value = false
        _board.value = null
        _state.value = ModuleState.Error(
            message = "Add your Todoist token in Settings",
            lastUpdatedEpochMs = null,
        )
    }

    private fun messageFor(error: TodoistError): String = when (error) {
        is TodoistError.Unauthorized -> "Token rejected — re-enter it in Settings"
        is TodoistError.RateLimited -> "Todoist is rate limiting — try later"
        is TodoistError.Server -> "Todoist is unavailable — showing nothing yet"
        is TodoistError.Network -> "No network — can't reach Todoist"
        is TodoistError.Unexpected -> "Couldn't load Todoist"
    }

    companion object {
        /** Wires the real Android implementations. Used by the composition root. */
        fun create(context: Context): TodoistModule {
            val app = context.applicationContext
            val tokenStore = KeystoreTokenStore(app)
            return TodoistModule(
                repo = TodoistRepository(
                    api = RetrofitTodoistApi.create(tokenStore),
                    cache = RoomTaskCache(app),
                ),
                settingsStore = TodoistSettingsStore(app),
                tokenStore = tokenStore,
            )
        }
    }
}
