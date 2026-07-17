package com.eink.dashboard.dashboard

/**
 * Shell-visible lifecycle status of a [DashboardModule].
 *
 * The shell renders chrome (loading marker, error text, "stale" hint, last-
 * updated time) from this state; the module renders its own body from its own
 * data inside [DashboardModule.Content]. Keeping the SPI status free of module-
 * specific payload types lets T03–T05 evolve their data models independently
 * without touching this frozen contract.
 *
 * [lastUpdatedEpochMs] is the wall-clock time of the last successful data load,
 * or `null` if the module has never produced data. The shell uses it to show
 * "updated HH:mm" and to grey out data that is older than the module considers
 * fresh (staleness is the module's judgement, surfaced via [Ok.isStale]).
 */
sealed interface ModuleState {

    val lastUpdatedEpochMs: Long?

    /** Data is being loaded and nothing renderable exists yet. */
    data object Loading : ModuleState {
        override val lastUpdatedEpochMs: Long? get() = null
    }

    /** Data is available. [isStale] marks a successful older load that the module could not refresh. */
    data class Ok(
        override val lastUpdatedEpochMs: Long?,
        val isStale: Boolean = false,
    ) : ModuleState

    /** Loaded successfully but there is nothing to show (e.g. no events today). */
    data class Empty(
        override val lastUpdatedEpochMs: Long? = null,
    ) : ModuleState

    /** The last load failed. [message] is a short, user-facing reason. */
    data class Error(
        val message: String,
        override val lastUpdatedEpochMs: Long? = null,
    ) : ModuleState
}
