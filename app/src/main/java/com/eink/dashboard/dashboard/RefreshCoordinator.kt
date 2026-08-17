package com.eink.dashboard.dashboard

import com.eink.dashboard.core.time.MinuteTicker
import com.eink.dashboard.core.time.SystemTimeSource
import com.eink.dashboard.core.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the foreground-only refresh lifecycle for the whole dashboard.
 *
 * Guarantees required by T02:
 * - **Foreground only.** The minute ticker runs only between [start] and [stop].
 *   In background there is no periodic loop and no API refresh — [stop] cancels
 *   the ticking job.
 * - **Immediate refresh on resume.** [start] fires a [RefreshReason.RESUMED]
 *   (or [RefreshReason.INITIAL] the very first time) pass before it begins
 *   ticking, so returning to the screen shows fresh data at once.
 * - **Minute cadence.** While started, every minute boundary triggers a
 *   [RefreshReason.MINUTE_TICK] pass, gated per module by [RefreshDecision].
 *
 * The coordinator is driven by the Activity lifecycle (`onResume`/`onPause`).
 * It records each module's last-refresh time to feed the periodic policy, and
 * exposes [lastTickEpochMs] so the header clock updates on the same tick.
 */
class RefreshCoordinator(
    private val registry: DashboardModuleRegistry,
    private val scope: CoroutineScope,
    private val ticker: MinuteTicker = MinuteTicker(),
    private val timeSource: TimeSource = SystemTimeSource,
    /** Injectable so tests can assert refresh calls; production runs each module's [DashboardModule.refresh]. */
    private val onRefreshModule: suspend (DashboardModule, RefreshReason) -> Unit = { m, r -> m.refresh(r) },
) {
    private val lastRefresh = mutableMapOf<String, Long>()

    private var tickerJob: Job? = null
    private var started = false

    private val _lastTickEpochMs = MutableStateFlow(timeSource.nowMs())
    /** Epoch millis of the most recent tick/resume — the clock reads this. */
    val lastTickEpochMs: StateFlow<Long> = _lastTickEpochMs.asStateFlow()

    private val _running = MutableStateFlow(false)
    /** True while the ticker loop is active (foreground). Observable for diagnostics/tests. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** True while the ticker loop is active (foreground). */
    val isRunning: Boolean get() = started

    /**
     * Call from `onResume`. Runs a forced refresh pass then starts ticking.
     * The first ever call uses [RefreshReason.INITIAL]; later calls use
     * [RefreshReason.RESUMED]. Idempotent while already started.
     */
    fun start() {
        if (started) return
        started = true
        _running.value = true
        val firstEver = lastRefresh.isEmpty()
        val reason = if (firstEver) RefreshReason.INITIAL else RefreshReason.RESUMED
        _lastTickEpochMs.value = timeSource.nowMs()
        refreshPass(reason)
        tickerJob = scope.launch {
            ticker.ticks().collect { tickMs ->
                _lastTickEpochMs.value = tickMs
                refreshPass(RefreshReason.MINUTE_TICK)
            }
        }
    }

    /**
     * Call from `onPause`. Cancels the ticker and any in-flight refresh; no
     * periodic work happens until the next [start].
     */
    fun stop() {
        if (!started) return
        started = false
        _running.value = false
        tickerJob?.cancel()
        tickerJob = null
    }

    /** User-driven full refresh (pull/refresh button). No-op semantics if stopped. */
    fun requestManualRefresh() = refreshPass(RefreshReason.MANUAL)

    /** Called when settings change (e.g. a module made visible). */
    fun onSettingsChanged() = refreshPass(RefreshReason.SETTINGS_CHANGED)

    private fun refreshPass(reason: RefreshReason) {
        val now = timeSource.nowMs()
        for (module in registry.all) {
            if (RefreshDecision.shouldRefresh(module.refreshPolicy, reason, lastRefresh[module.id], now)) {
                lastRefresh[module.id] = now
                scope.launch {
                    onRefreshModule(module, reason)
                    // A failed load must not consume the whole periodic slot —
                    // otherwise one bad refresh at app start (Wi-Fi not up yet,
                    // expired OAuth token) leaves the module empty/stale for the
                    // full interval. Forgetting the timestamp lets the next
                    // minute tick retry. The guard keeps a newer pass's stamp.
                    if (module.state.value.isFailedLoad && lastRefresh[module.id] == now) {
                        lastRefresh.remove(module.id)
                    }
                }
            }
        }
    }

    private val ModuleState.isFailedLoad: Boolean
        get() = this is ModuleState.Error || (this is ModuleState.Ok && isStale)
}
