package com.eink.dashboard.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits exactly once per wall-clock minute, aligned to the minute boundary
 * (…:00 seconds). E-ink panels must never animate a seconds counter — a full
 * refresh every second would flash the screen and burn the panel — so the whole
 * UI updates on the minute edge only.
 *
 * The tick math ([millisUntilNextMinute]) is a pure function and is unit-tested
 * without an Android runtime. The [ticks] flow uses an injected [TimeSource] and
 * `delay`, so it runs under virtual time in `runTest`.
 */
class MinuteTicker(private val timeSource: TimeSource = SystemTimeSource) {

    /**
     * Cold flow that suspends until the next minute boundary, emits the epoch
     * millis at that boundary, then repeats. Cancelling the collector (e.g. when
     * the dashboard goes to background) stops the loop; no work happens off-screen.
     */
    fun ticks(): Flow<Long> = flow {
        while (true) {
            delay(millisUntilNextMinute(timeSource.nowMs()))
            emit(timeSource.nowMs())
        }
    }

    companion object {
        const val MINUTE_MS: Long = 60_000L

        /**
         * Milliseconds from [nowMs] until the next `:00` second boundary.
         * When [nowMs] is already exactly on a boundary this returns a full
         * minute (never 0), so the ticker always waits forward and never
         * busy-loops.
         */
        fun millisUntilNextMinute(nowMs: Long): Long {
            val intoMinute = nowMs % MINUTE_MS
            return if (intoMinute == 0L) MINUTE_MS else MINUTE_MS - intoMinute
        }
    }
}
