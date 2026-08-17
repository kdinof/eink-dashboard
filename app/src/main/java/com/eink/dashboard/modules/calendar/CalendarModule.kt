package com.eink.dashboard.modules.calendar

import android.content.Context
import android.util.Log
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
import com.eink.dashboard.modules.calendar.google.GoogleAuthManager
import com.eink.dashboard.modules.calendar.google.GoogleCalendarDataSource
import com.eink.dashboard.modules.calendar.google.GoogleCredentialStore
import com.eink.dashboard.modules.calendar.google.GoogleCalendarApiException
import com.eink.dashboard.modules.calendar.google.GoogleReconnectRequiredException
import com.eink.dashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val googleAuth: GoogleAuthManager? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : DashboardModule {

    override val id: String = "calendar"
    override val title: String = "Calendar"

    /** The agenda is the board's centerpiece — refresh twice as often as other data widgets. */
    override val refreshPolicy: RefreshPolicy = RefreshPolicy.Periodic(5.minutes)
    override val hasSettings: Boolean = true

    private val _state = MutableStateFlow<ModuleState>(ModuleState.Loading)
    override val state: StateFlow<ModuleState> = _state.asStateFlow()

    /** Latest resolved agenda for [Content]; null until the first successful load. */
    private val _agenda = MutableStateFlow<CalendarAgenda?>(null)
    val agenda: StateFlow<CalendarAgenda?> = _agenda.asStateFlow()

    /** Calendar choices are tagged with their source so ids cannot cross sources. */
    private val _catalog = MutableStateFlow(CalendarCatalog())
    val catalog: StateFlow<CalendarCatalog> = _catalog.asStateFlow()

    /** Whether the app currently holds `READ_CALENDAR`, for the settings prompt. */
    private val _permissionGranted = MutableStateFlow(permission.isGranted())
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()
    val googleConnected: Boolean get() = googleAuth?.isConnected == true
    val googleBrokerConfigured: Boolean get() = googleAuth?.isConfigured == true

    /** Prevent an older, slower request from overwriting a newer settings refresh. */
    private val refreshMutex = Mutex()

    override suspend fun refresh(reason: RefreshReason) = refreshMutex.withLock {
        val settings = settingsStore.current()
        val granted = permission.isGranted()
        _permissionGranted.value = granted
        if (settings.source == CalendarSourceMode.DEVICE && !granted) {
            _state.value = ModuleState.Error(
                message = "Calendar access needed — grant it in Settings",
                lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs,
            )
            return
        }
        if (settings.source == CalendarSourceMode.GOOGLE && !googleConnected) {
            _state.value = ModuleState.Error(
                message = "Connect Google Calendar in Settings",
                lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs,
            )
            return
        }
        try {
            // Heavy ContentResolver work off the caller thread (safe from either the
            // coordinator's background dispatcher or a settings-triggered reload).
            val loaded = withContext(Dispatchers.IO) { repo.load(settings, clock()) }
            _agenda.value = loaded
            _catalog.value = CalendarCatalog(loaded.source, loaded.calendars)
            _state.value = if (loaded.isEmpty) {
                ModuleState.Empty(lastUpdatedEpochMs = clock())
            } else {
                ModuleState.Ok(lastUpdatedEpochMs = clock())
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val safeReason = when (t) {
                is GoogleCalendarApiException -> "HTTP ${t.statusCode} ${t.reason.orEmpty()}".trim()
                // Our own error() strings (broker/connect failures) — fixed text, never calendar data.
                is IllegalStateException -> t.message ?: t.javaClass.simpleName
                else -> t.javaClass.simpleName
            }
            Log.w("EinkCalendar", "Calendar refresh failed: $safeReason")
            // Never log calendar contents; surface a generic message only.
            val previous = _agenda.value
            _state.value = when {
                // A dead grant can't heal by retrying — tell the user what to do.
                t is GoogleReconnectRequiredException -> ModuleState.Error(
                    message = "Reconnect Google Calendar in Settings",
                    lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs,
                )
                previous != null ->
                    ModuleState.Ok(lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs, isStale = true)
                else -> ModuleState.Error(
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
            val auth = GoogleAuthManager(BuildConfig.GOOGLE_BROKER_URL, GoogleCredentialStore(app))
            return CalendarModule(
                repo = CalendarRepository(AndroidCalendarDataSource(app), GoogleCalendarDataSource(auth)),
                settingsStore = CalendarSettingsStore(app),
                permission = AndroidCalendarPermission(app),
                googleAuth = auth,
            )
        }
    }
}

data class CalendarCatalog(
    val source: CalendarSourceMode? = null,
    val calendars: List<CalendarInfo> = emptyList(),
)
