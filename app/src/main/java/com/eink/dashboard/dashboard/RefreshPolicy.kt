package com.eink.dashboard.dashboard

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Shared foreground cadence for every data widget on the dashboard. */
val DataWidgetRefreshPolicy: RefreshPolicy = RefreshPolicy.Periodic(10.minutes)

/**
 * How often a [DashboardModule] wants to be refreshed on the minute ticker.
 *
 * Lifecycle reasons (INITIAL / RESUMED / MANUAL / SETTINGS_CHANGED) always
 * refresh a module regardless of policy — the policy only gates the recurring
 * [RefreshReason.MINUTE_TICK]. This keeps API-backed modules from hammering
 * their source once a minute while still updating instantly when the user comes
 * back to the screen.
 */
sealed interface RefreshPolicy {

    /** Only refreshes on lifecycle reasons, never on a minute tick. */
    data object Manual : RefreshPolicy

    /** Refreshes on every minute tick (e.g. the on-device clock). */
    data object EveryMinute : RefreshPolicy

    /**
     * Refreshes on a minute tick only if at least [minInterval] has elapsed
     * since the last refresh. `minInterval` is rounded to whole minutes in
     * effect because ticks only arrive on the minute.
     */
    data class Periodic(val minInterval: Duration) : RefreshPolicy {
        init {
            require(minInterval.isPositive()) { "minInterval must be positive, was $minInterval" }
        }
    }
}
