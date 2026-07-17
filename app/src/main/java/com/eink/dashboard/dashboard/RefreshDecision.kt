package com.eink.dashboard.dashboard

/**
 * Pure, side-effect-free rule for "should this module refresh now?". Extracted
 * from [RefreshCoordinator] so the scheduling policy is unit-tested directly
 * without coroutines, clocks or Android.
 */
object RefreshDecision {

    /**
     * @param policy the module's [RefreshPolicy]
     * @param reason why a refresh is being considered
     * @param lastRefreshEpochMs epoch millis of the module's last refresh, or
     *   `null` if it has never refreshed
     * @param nowEpochMs current epoch millis
     *
     * Lifecycle reasons always refresh. A [RefreshReason.MINUTE_TICK] refreshes
     * only if the policy allows it at [nowEpochMs].
     */
    fun shouldRefresh(
        policy: RefreshPolicy,
        reason: RefreshReason,
        lastRefreshEpochMs: Long?,
        nowEpochMs: Long,
    ): Boolean = when (reason) {
        RefreshReason.INITIAL,
        RefreshReason.RESUMED,
        RefreshReason.MANUAL,
        RefreshReason.SETTINGS_CHANGED -> true

        RefreshReason.MINUTE_TICK -> when (policy) {
            RefreshPolicy.Manual -> false
            RefreshPolicy.EveryMinute -> true
            is RefreshPolicy.Periodic ->
                lastRefreshEpochMs == null ||
                    (nowEpochMs - lastRefreshEpochMs) >= policy.minInterval.inWholeMilliseconds
        }
    }
}
