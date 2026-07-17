package com.eink.dashboard.modules.calendar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.calendar.data.AndroidCalendarDataSource
import com.eink.dashboard.modules.calendar.model.CalendarInfo
import com.eink.dashboard.modules.calendar.ui.CalendarContent
import com.eink.dashboard.modules.calendar.ui.CalendarSettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

/**
 * Read-only Google Calendar block (T03).
 *
 * The shell drives all cadence via [refreshPolicy]; this module starts no loop of
 * its own. On each [refresh] it checks `READ_CALENDAR`, then asks
 * [CalendarRepository] for the agenda in the user's chosen window and calendars,
 * and maps the outcome onto the frozen [ModuleState] the shell renders chrome
 * from. Its own richer content (grouped days, grayscale markers) is drawn in
 * [Content]; the per-module calendar/range settings live in [SettingsContent],
 * surfaced by the shell's Settings screen via the additive `hasSettings` slot.
 *
 * State mapping:
 * - permission missing → [ModuleState.Error] ("grant access"), no provider read;
 * - read succeeds, events exist → [ModuleState.Ok];
 * - read succeeds, nothing in window → [ModuleState.Empty];
 * - read fails but a previous agenda exists → [ModuleState.Ok] with `isStale`;
 * - read fails with nothing cached → [ModuleState.Error].
 */
class CalendarModule(
    private val repo: CalendarRepository,
    private val settingsStore: CalendarSettingsStore,
    private val permission: CalendarPermission,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : DashboardModule {

    override val id: String = "calendar"
    override val title: String = "Calendar"
    override val refreshPolicy: RefreshPolicy = RefreshPolicy.Periodic(15.minutes)
    override val hasSettings: Boolean = true

    private val _state = MutableStateFlow<ModuleState>(ModuleState.Loading)
    override val state: StateFlow<ModuleState> = _state.asStateFlow()

    /** Latest resolved agenda for [Content]; null until the first successful load. */
    private val _agenda = MutableStateFlow<CalendarAgenda?>(null)
    val agenda: StateFlow<CalendarAgenda?> = _agenda.asStateFlow()

    /** Calendars available to pick from, for [SettingsContent]. */
    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()

    /** Whether the app currently holds `READ_CALENDAR`, for the settings prompt. */
    private val _permissionGranted = MutableStateFlow(permission.isGranted())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    override suspend fun refresh(reason: RefreshReason) {
        val granted = permission.isGranted()
        _permissionGranted.value = granted
        if (!granted) {
            _state.value = ModuleState.Error(
                message = "Calendar access needed — grant it in Settings",
                lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs,
            )
            return
        }
        try {
            // Heavy ContentResolver work off the caller thread (safe from either the
            // coordinator's background dispatcher or a settings-triggered reload).
            val settings = settingsStore.current()
            val loaded = withContext(Dispatchers.IO) { repo.load(settings, clock()) }
            _agenda.value = loaded
            _calendars.value = loaded.calendars
            _state.value = if (loaded.isEmpty) {
                ModuleState.Empty(lastUpdatedEpochMs = clock())
            } else {
                ModuleState.Ok(lastUpdatedEpochMs = clock())
            }
        } catch (t: Throwable) {
            // Never log calendar contents; surface a generic message only.
            val previous = _agenda.value
            _state.value = if (previous != null) {
                ModuleState.Ok(lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs, isStale = true)
            } else {
                ModuleState.Error(
                    message = "Couldn't read the calendar",
                    lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs,
                )
            }
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        CalendarContent(module = this, modifier = modifier)
    }

    @Composable
    override fun SettingsContent(modifier: Modifier) {
        CalendarSettingsSection(module = this, settingsStore = settingsStore, modifier = modifier)
    }

    /** Re-check the permission (called after a runtime grant from the settings UI). */
    fun refreshPermissionFlag() {
        _permissionGranted.value = permission.isGranted()
    }

    companion object {
        /** Wires the real Android implementations. Used by the composition root. */
        fun create(context: Context): CalendarModule {
            val app = context.applicationContext
            return CalendarModule(
                repo = CalendarRepository(AndroidCalendarDataSource(app)),
                settingsStore = CalendarSettingsStore(app),
                permission = AndroidCalendarPermission(app),
            )
        }
    }
}
