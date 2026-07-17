package com.eink.dashboard.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/**
 * A pluggable dashboard block — the frozen SPI that T03 (Calendar), T04
 * (Todoist) and T05 (Weather / system) implement. The shell owns nothing about
 * a module's data: it asks the module to [refresh], observes its [state] for
 * chrome, and hands it a slot to draw itself via [Content].
 *
 * ## Contract for implementers
 * - [id] must be stable and unique; it is the persistence key for visibility
 *   settings and the diagnostics label. Use a short reverse-dotted string,
 *   e.g. `"calendar"`, `"todoist"`, `"weather"`.
 * - [refresh] is **always called on a background dispatcher** by the coordinator
 *   and must be safe to cancel — when the dashboard goes to background the
 *   coordinator cancels in-flight refreshes. Do the network/DB work here and
 *   push results into your own state holder; then update [state].
 * - [state] is hot and lifecycle-independent; expose the latest known status.
 * - [Content] must be pure grayscale, allocate no timers of its own (the shell
 *   drives all refresh), and must render acceptably at both portrait and
 *   landscape widths. It renders the module body only — the shell draws the
 *   title bar and status chrome around it.
 * - A module MUST NOT start its own periodic work. All cadence comes from the
 *   coordinator via [refreshPolicy]; a self-scheduled loop would keep running in
 *   background and defeats the foreground-only guarantee.
 */
interface DashboardModule {

    /** Stable unique key. Persistence + diagnostics identity. */
    val id: String

    /** Human title drawn by the shell in the block header. */
    val title: String

    /** How the coordinator schedules recurring refreshes for this module. */
    val refreshPolicy: RefreshPolicy

    /**
     * `true` for fake/sample modules that must never be presented as real user
     * data in a release build. The registry refuses to register a demo module in
     * a release build (see [DashboardModuleRegistry]), and the shell tags demo
     * blocks with a visible "DEMO" marker.
     */
    val isDemo: Boolean get() = false

    /**
     * `true` if this module contributes a settings section to the Settings screen.
     * Additive extension point (default `false`) introduced by T03 so a module can
     * own module-specific configuration (calendar selection / range, and later the
     * Todoist token, weather location) without the shell knowing its shape. The
     * shell renders [SettingsContent] under the module [title] only when this is
     * `true`. See `reports/T03_calendar.md` for the rationale handed to T04–T06.
     */
    val hasSettings: Boolean get() = false

    /** Latest shell-visible status. */
    val state: StateFlow<ModuleState>

    /**
     * Load or reload this module's data. Called on a background dispatcher.
     * Implementations should honour cancellation and update [state]. The
     * [reason] lets a module vary its behaviour (e.g. cheap tick vs. full reload).
     */
    suspend fun refresh(reason: RefreshReason)

    /** Draw the module body. Grayscale, no animation, no self-scheduled timers. */
    @Composable
    fun Content(modifier: Modifier)

    /**
     * Draw this module's own settings, rendered by the shell's Settings screen when
     * [hasSettings] is `true`. Default no-op so existing modules need no change.
     * Same rules as [Content]: grayscale, no animation, no self-scheduled timers.
     */
    @Composable
    fun SettingsContent(modifier: Modifier) {
    }
}
