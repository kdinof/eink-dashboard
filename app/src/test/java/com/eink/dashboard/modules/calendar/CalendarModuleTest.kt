package com.eink.dashboard.modules.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.model.CalendarInfo
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The module's state machine over the frozen [ModuleState] contract: permission
 * gating, the required "no permission" and success paths, empty results, and the
 * stale-on-failure fallback. Uses a real in-memory settings store (defaults) and a
 * fake provider, so no device or Robolectric is needed.
 */
class CalendarModuleTest {

    private val now = localMs(TASHKENT, 2026, 7, 17, 8, 0)
    private val work = CalendarInfo(10, "Work", "work@example.com")

    private fun tempStore(): CalendarSettingsStore {
        val dir = Files.createTempDirectory("cal-settings").toFile()
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { File(dir, "calendar_settings.preferences_pb") }
        return CalendarSettingsStore(ds)
    }

    private fun todayEvent() = CalendarEvent(
        eventId = 1, calendarId = 10, title = "Stand-up",
        beginMs = localMs(TASHKENT, 2026, 7, 17, 9, 0),
        endMs = localMs(TASHKENT, 2026, 7, 17, 10, 0),
        isAllDay = false,
    )

    private fun module(source: FakeCalendarDataSource, granted: Boolean): CalendarModule =
        CalendarModule(
            repo = CalendarRepository(source, zoneProvider = { TASHKENT }),
            settingsStore = tempStore(),
            permission = CalendarPermission { granted },
            clock = { now },
        )

    @Test
    fun contractIdentity_isStable() {
        val m = module(FakeCalendarDataSource(listOf(work), emptyList()), granted = true)
        assertThat(m.id).isEqualTo("calendar")
        assertThat(m.refreshPolicy).isInstanceOf(RefreshPolicy.Periodic::class.java)
        assertThat(m.isDemo).isFalse()
        assertThat(m.hasSettings).isTrue()
    }

    @Test
    fun permissionDenied_yieldsError_andNeverQueries(): Unit = runBlocking {
        val source = FakeCalendarDataSource(listOf(work), listOf(todayEvent()))
        val m = module(source, granted = false)
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Error::class.java)
        assertThat(m.permissionGranted.value).isFalse()
        assertThat(source.queryCount).isEqualTo(0)
        assertThat(m.agenda.value).isNull()
    }

    @Test
    fun grantedWithEvents_yieldsOk_andPopulatesAgendaAndCalendars(): Unit = runBlocking {
        val source = FakeCalendarDataSource(listOf(work), listOf(todayEvent()))
        val m = module(source, granted = true)
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Ok::class.java)
        assertThat(m.agenda.value?.days?.single()?.events?.single()?.title).isEqualTo("Stand-up")
        assertThat(m.calendars.value).containsExactly(work)
    }

    @Test
    fun grantedWithNoEvents_yieldsEmpty(): Unit = runBlocking {
        val source = FakeCalendarDataSource(listOf(work), emptyList())
        val m = module(source, granted = true)
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Empty::class.java)
    }

    @Test
    fun queryFailureWithNoPriorData_yieldsError(): Unit = runBlocking {
        val source = FakeCalendarDataSource(listOf(work), listOf(todayEvent()), failOnQuery = true)
        val m = module(source, granted = true)
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Error::class.java)
    }

    @Test
    fun queryFailureAfterSuccess_keepsDataAndMarksStale(): Unit = runBlocking {
        val source = FakeCalendarDataSource(listOf(work), listOf(todayEvent()))
        val m = module(source, granted = true)
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Ok::class.java)

        source.failOnQuery = true
        m.refresh(RefreshReason.MINUTE_TICK)
        val state = m.state.value
        assertThat(state).isInstanceOf(ModuleState.Ok::class.java)
        assertThat((state as ModuleState.Ok).isStale).isTrue()
        // Previous agenda is retained for display.
        assertThat(m.agenda.value?.days).isNotEmpty()
    }
}
