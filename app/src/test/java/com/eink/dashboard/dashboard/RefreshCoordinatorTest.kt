package com.eink.dashboard.dashboard

import com.eink.dashboard.core.time.MinuteTicker
import com.eink.dashboard.core.time.TimeSource
import com.eink.dashboard.modules.demo.DemoAgendaModule
import com.eink.dashboard.modules.demo.DemoClockModule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)

/**
 * Lifecycle behaviour of the foreground-only coordinator, driven by virtual time.
 *
 * Uses the real demo modules (EveryMinute clock + Periodic-15m agenda) as the
 * fixture — no Compose is touched because [DashboardModule.Content] is never
 * invoked here; only [DashboardModule.refresh] and the scheduling policy are.
 * Refresh calls are captured through the injected `onRefreshModule` hook.
 */
class RefreshCoordinatorTest {

    @Test
    fun lifecycle_start_tick_stop_resume() = runTest {
        val ts = TimeSource { testScheduler.currentTime }
        val clock = DemoClockModule(ts)
        val agenda = DemoAgendaModule(ts)
        val registry = DashboardModuleRegistry.builder(allowDemo = true)
            .register(clock)
            .register(agenda)
            .build()

        val calls = mutableListOf<Pair<String, RefreshReason>>()
        val coordinator = RefreshCoordinator(
            registry = registry,
            scope = backgroundScope,
            ticker = MinuteTicker(ts),
            timeSource = ts,
            onRefreshModule = { module, reason -> calls.add(module.id to reason); module.refresh(reason) },
        )

        // First start -> INITIAL forces every module.
        coordinator.start()
        runCurrent()
        assertThat(coordinator.isRunning).isTrue()
        assertThat(calls).containsExactly(
            "demo.clock" to RefreshReason.INITIAL,
            "demo.agenda" to RefreshReason.INITIAL,
        )
        calls.clear()

        // One minute later: only the EveryMinute clock refreshes; the 15-min
        // agenda is not due yet.
        advanceTimeBy(60_001)
        runCurrent()
        assertThat(calls).containsExactly("demo.clock" to RefreshReason.MINUTE_TICK)
        calls.clear()

        // Background: ticker stops, no periodic work even after a long wait.
        coordinator.stop()
        assertThat(coordinator.isRunning).isFalse()
        advanceTimeBy(10 * 60_000)
        runCurrent()
        assertThat(calls).isEmpty()

        // Resume: immediate forced refresh of everything.
        coordinator.start()
        runCurrent()
        assertThat(calls.map { it.second }.toSet()).containsExactly(RefreshReason.RESUMED)
        assertThat(calls.map { it.first }).containsExactly("demo.clock", "demo.agenda")
    }

    @Test
    fun periodicModule_refreshesOnTick_onceIntervalElapsed() = runTest {
        val ts = TimeSource { testScheduler.currentTime }
        val agenda = DemoAgendaModule(ts) // Periodic(15 min)
        val registry = DashboardModuleRegistry.builder(allowDemo = true).register(agenda).build()

        val calls = mutableListOf<RefreshReason>()
        val coordinator = RefreshCoordinator(
            registry = registry,
            scope = backgroundScope,
            ticker = MinuteTicker(ts),
            timeSource = ts,
            onRefreshModule = { _, reason -> calls.add(reason) },
        )

        coordinator.start() // INITIAL at t=0
        runCurrent()
        calls.clear()

        // 14 ticks (< 15 min since last refresh) -> no periodic refresh.
        advanceTimeBy(14 * 60_000 + 1)
        runCurrent()
        assertThat(calls).isEmpty()

        // Crossing the 15-minute mark -> one periodic refresh.
        advanceTimeBy(60_000)
        runCurrent()
        assertThat(calls).containsExactly(RefreshReason.MINUTE_TICK)
    }

    @Test
    fun manualRefresh_forcesEvenPeriodicModule() = runTest {
        val ts = TimeSource { testScheduler.currentTime }
        val agenda = DemoAgendaModule(ts)
        val registry = DashboardModuleRegistry.builder(allowDemo = true).register(agenda).build()

        val calls = mutableListOf<RefreshReason>()
        val coordinator = RefreshCoordinator(
            registry = registry,
            scope = backgroundScope,
            ticker = MinuteTicker(ts),
            timeSource = ts,
            onRefreshModule = { _, reason -> calls.add(reason) },
        )
        coordinator.start()
        runCurrent()
        calls.clear()

        coordinator.requestManualRefresh()
        runCurrent()
        assertThat(calls).containsExactly(RefreshReason.MANUAL)
    }
}
