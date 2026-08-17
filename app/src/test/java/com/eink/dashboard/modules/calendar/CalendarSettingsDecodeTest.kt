package com.eink.dashboard.modules.calendar

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eink.dashboard.modules.calendar.model.CalendarRangeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure Preferences → [CalendarSettings] mapping (mirrors the shell's decode test):
 * defaults on an empty store, full round-trip, and safe fallback on junk.
 */
class CalendarSettingsDecodeTest {

    @Test
    fun emptyStore_decodesToDefaults() {
        assertThat(CalendarSettingsStore.Keys.decode(emptyPreferences()))
            .isEqualTo(CalendarSettings.DEFAULT)
    }

    @Test
    fun fullRoundTrip() {
        val prefs = mutablePreferencesOf(
            CalendarSettingsStore.Keys.RANGE to CalendarRangeMode.WEEK.name,
            CalendarSettingsStore.Keys.DESELECTED to setOf("10", "20"),
        )
        val decoded = CalendarSettingsStore.Keys.decode(prefs)
        assertThat(decoded.range).isEqualTo(CalendarRangeMode.WEEK)
        assertThat(decoded.deselectedCalendarIds).containsExactly(10L, 20L)
    }

    @Test
    fun unknownRangeName_fallsBackToDefault() {
        val prefs = mutablePreferencesOf(
            CalendarSettingsStore.Keys.RANGE to "NOT_A_RANGE",
        )
        assertThat(CalendarSettingsStore.Keys.decode(prefs).range)
            .isEqualTo(CalendarSettings.DEFAULT.range)
    }

    @Test
    fun nonNumericDeselectedIds_areDroppedNotCrashed() {
        val prefs = mutablePreferencesOf(
            stringSetPreferencesKey("deselected_calendar_ids") to setOf("10", "garbage"),
        )
        assertThat(CalendarSettingsStore.Keys.decode(prefs).deselectedCalendarIds)
            .containsExactly(10L)
    }

    @Test
    fun sourceSelections_areIndependent() {
        val prefs = mutablePreferencesOf(
            CalendarSettingsStore.Keys.SOURCE to CalendarSourceMode.GOOGLE.name,
            CalendarSettingsStore.Keys.DEVICE_DESELECTED to setOf("10"),
            CalendarSettingsStore.Keys.GOOGLE_DESELECTED to setOf("20"),
        )

        val decoded = CalendarSettingsStore.Keys.decode(prefs)

        assertThat(decoded.deselectedIdsFor(CalendarSourceMode.DEVICE)).containsExactly(10L)
        assertThat(decoded.deselectedIdsFor(CalendarSourceMode.GOOGLE)).containsExactly(20L)
        assertThat(decoded.deselectedCalendarIds).containsExactly(20L)
    }

    @Test
    fun legacySelection_migratesToPreviouslyActiveSource() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("calendar_source") to CalendarSourceMode.GOOGLE.name,
            stringSetPreferencesKey("deselected_calendar_ids") to setOf("42"),
        )

        val decoded = CalendarSettingsStore.Keys.decode(prefs)

        assertThat(decoded.googleDeselectedCalendarIds).containsExactly(42L)
        assertThat(decoded.deviceDeselectedCalendarIds).isEmpty()
    }
}
