package com.eink.dashboard.modules.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.eink.dashboard.modules.calendar.model.CalendarRangeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CalendarSettingsStoreTest {
    private fun dataStore(): DataStore<Preferences> {
        val dir = Files.createTempDirectory("calendar-settings-store").toFile()
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { File(dir, "settings.preferences_pb") }
    }

    private fun store(): CalendarSettingsStore = CalendarSettingsStore(dataStore())

    @Test
    fun sourceSwitchAndRemoteUpdate_preserveOtherSourcesSelection(): Unit = runBlocking {
        val store = store()
        store.setCalendarSelected(CalendarSourceMode.DEVICE, 10, selected = false)
        store.setCalendarSelected(CalendarSourceMode.GOOGLE, 20, selected = false)

        // Switching before the new source's catalog loads carries no selection ids.
        store.update(CalendarRangeMode.WEEK, CalendarSourceMode.GOOGLE)
        var settings = store.current()
        assertThat(settings.deviceDeselectedCalendarIds).containsExactly(10L)
        assertThat(settings.googleDeselectedCalendarIds).containsExactly(20L)

        // Once Google ids are loaded, updating them still leaves Device untouched.
        store.update(
            CalendarRangeMode.WEEK,
            CalendarSourceMode.GOOGLE,
            availableIds = setOf(20, 21),
            selectedIds = setOf(21),
        )
        settings = store.current()
        assertThat(settings.deviceDeselectedCalendarIds).containsExactly(10L)
        assertThat(settings.googleDeselectedCalendarIds).containsExactly(20L)
    }

    @Test
    fun legacySelection_isMaterializedBeforeSourceChanges(): Unit = runBlocking {
        val dataStore = dataStore()
        dataStore.edit { prefs ->
            prefs[CalendarSettingsStore.Keys.SOURCE] = CalendarSourceMode.DEVICE.name
            prefs[CalendarSettingsStore.Keys.DESELECTED] = setOf("10")
        }
        val store = CalendarSettingsStore(dataStore)

        store.setSource(CalendarSourceMode.GOOGLE)

        val settings = store.current()
        assertThat(settings.deviceDeselectedCalendarIds).containsExactly(10L)
        assertThat(settings.googleDeselectedCalendarIds).isEmpty()
    }
}
