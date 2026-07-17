package com.eink.dashboard.core.time

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)

/**
 * Locks the minute-boundary math (pure) and the flow's alignment under virtual
 * time. Seconds must never be a refresh unit — the ticker only ever fires on the
 * `:00` boundary.
 */
class MinuteTickerTest {

    @Test
    fun millisUntilNextMinute_midMinute_returnsRemainder() {
        // 12_345 ms into a minute -> 60_000 - 12_345 left.
        assertThat(MinuteTicker.millisUntilNextMinute(12_345)).isEqualTo(60_000 - 12_345)
    }

    @Test
    fun millisUntilNextMinute_onBoundary_returnsFullMinute_neverZero() {
        assertThat(MinuteTicker.millisUntilNextMinute(0)).isEqualTo(60_000)
        assertThat(MinuteTicker.millisUntilNextMinute(120_000)).isEqualTo(60_000)
    }

    @Test
    fun millisUntilNextMinute_oneMsBeforeBoundary_returns1() {
        assertThat(MinuteTicker.millisUntilNextMinute(59_999)).isEqualTo(1)
    }

    @Test
    fun ticks_emitOnEachMinuteBoundary() = runTest {
        // TimeSource reads virtual time so `delay` and `nowMs` stay in lockstep.
        val ticker = MinuteTicker(TimeSource { testScheduler.currentTime })
        val collected = async { ticker.ticks().take(3).toList() }
        advanceUntilIdle()
        assertThat(collected.await()).containsExactly(60_000L, 120_000L, 180_000L).inOrder()
    }
}
