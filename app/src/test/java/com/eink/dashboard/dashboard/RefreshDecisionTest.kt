package com.eink.dashboard.dashboard

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * The scheduling policy in isolation: lifecycle reasons always refresh; a minute
 * tick is gated by the module's [RefreshPolicy].
 */
class RefreshDecisionTest {

    private val now = 1_000_000L

    @Test
    fun lifecycleReasons_alwaysRefresh_regardlessOfPolicy() {
        val reasons = listOf(
            RefreshReason.INITIAL,
            RefreshReason.RESUMED,
            RefreshReason.MANUAL,
            RefreshReason.SETTINGS_CHANGED,
        )
        val policies = listOf(
            RefreshPolicy.Manual,
            RefreshPolicy.EveryMinute,
            RefreshPolicy.Periodic(30.minutes),
        )
        for (reason in reasons) {
            for (policy in policies) {
                assertThat(RefreshDecision.shouldRefresh(policy, reason, lastRefreshEpochMs = now, nowEpochMs = now))
                    .isTrue()
            }
        }
    }

    @Test
    fun minuteTick_manualPolicy_neverRefreshes() {
        assertThat(
            RefreshDecision.shouldRefresh(RefreshPolicy.Manual, RefreshReason.MINUTE_TICK, now, now + 60_000),
        ).isFalse()
    }

    @Test
    fun minuteTick_everyMinutePolicy_alwaysRefreshes() {
        assertThat(
            RefreshDecision.shouldRefresh(RefreshPolicy.EveryMinute, RefreshReason.MINUTE_TICK, now, now + 60_000),
        ).isTrue()
    }

    @Test
    fun minuteTick_periodic_refreshesOnlyAfterInterval() {
        val policy = RefreshPolicy.Periodic(30.minutes)
        val interval = 30 * 60_000L
        // Just short of the interval -> skip.
        assertThat(
            RefreshDecision.shouldRefresh(policy, RefreshReason.MINUTE_TICK, now, now + interval - 1),
        ).isFalse()
        // At/after the interval -> refresh.
        assertThat(
            RefreshDecision.shouldRefresh(policy, RefreshReason.MINUTE_TICK, now, now + interval),
        ).isTrue()
    }

    @Test
    fun minuteTick_periodic_neverRefreshedYet_refreshes() {
        assertThat(
            RefreshDecision.shouldRefresh(RefreshPolicy.Periodic(30.minutes), RefreshReason.MINUTE_TICK, null, now),
        ).isTrue()
    }
}
