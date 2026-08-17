package com.eink.dashboard.modules.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.model.CalendarInfo
import com.eink.dashboard.modules.calendar.data.CalendarDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

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
        assertThat(m.refreshPolicy).isEqualTo(RefreshPolicy.Periodic(5.minutes))
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
        assertThat(m.catalog.value.source).isEqualTo(CalendarSourceMode.DEVICE)
        assertThat(m.catalog.value.calendars).containsExactly(work)
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

    @Test
    fun concurrentRefreshes_areSerialized_soOlderLoadCannotOverwriteNewerState(): Unit = runBlocking {
        val activeLoads = AtomicInteger()
        val maxActiveLoads = AtomicInteger()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val source = object : CalendarDataSource {
            override fun listCalendars(): List<CalendarInfo> {
                val active = activeLoads.incrementAndGet()
                maxActiveLoads.updateAndGet { maxOf(it, active) }
                if (firstEntered.count > 0) {
                    firstEntered.countDown()
                    check(releaseFirst.await(2, TimeUnit.SECONDS))
                }
                return listOf(work)
            }

            override fun queryInstances(
                startMs: Long,
                endMs: Long,
                calendarIds: Set<Long>?,
            ): List<CalendarEvent> {
                activeLoads.decrementAndGet()
                return listOf(todayEvent())
            }
        }
        val m = CalendarModule(
            repo = CalendarRepository(source, zoneProvider = { TASHKENT }),
            settingsStore = tempStore(),
            permission = CalendarPermission { true },
            clock = { now },
        )

        val first = async(Dispatchers.Default) { m.refresh(RefreshReason.INITIAL) }
        check(firstEntered.await(2, TimeUnit.SECONDS))
        val second = async(Dispatchers.Default) { m.refresh(RefreshReason.SETTINGS_CHANGED) }
        delay(100)
        releaseFirst.countDown()
        first.await()
        second.await()

        assertThat(maxActiveLoads.get()).isEqualTo(1)
        assertThat(m.state.value).isInstanceOf(ModuleState.Ok::class.java)
    }
}
