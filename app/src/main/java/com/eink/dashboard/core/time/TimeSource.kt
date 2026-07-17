package com.eink.dashboard.core.time

/**
 * Wall-clock abstraction so the minute ticker and refresh coordinator can be
 * driven by virtual time in unit tests instead of `System.currentTimeMillis()`.
 *
 * Production code uses [SystemTimeSource]; tests inject a fake backed by the
 * coroutines test scheduler.
 */
fun interface TimeSource {
    /** Current wall-clock time in epoch milliseconds. */
    fun nowMs(): Long
}

/** Real device clock. */
object SystemTimeSource : TimeSource {
    override fun nowMs(): Long = System.currentTimeMillis()
}
