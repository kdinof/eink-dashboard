package com.eink.dashboard.dashboard

/**
 * Why a refresh is being requested. The [RefreshCoordinator] passes this to each
 * module and to [RefreshDecision] so a module can, for example, do a light
 * update on a minute tick but a full reload when the screen resumes.
 */
enum class RefreshReason {
    /** First load when the dashboard is created. Always refreshes. */
    INITIAL,

    /** A minute-boundary tick. Policy-gated per [RefreshPolicy]. */
    MINUTE_TICK,

    /** The dashboard returned to the foreground (`onResume`). Always refreshes. */
    RESUMED,

    /** The user explicitly asked to refresh. Always refreshes. */
    MANUAL,

    /** A setting changed (e.g. a module was made visible). Always refreshes. */
    SETTINGS_CHANGED,
}
