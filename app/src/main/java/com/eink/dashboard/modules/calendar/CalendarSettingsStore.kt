package com.eink.dashboard.modules.calendar

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eink.dashboard.modules.calendar.model.CalendarRangeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.calendarDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "calendar_settings")

/**
 * DataStore-backed persistence for [CalendarSettings], kept entirely inside the
 * Calendar module (its own `calendar_settings` file). The Preferences ↔ model
 * translation ([Keys.decode]) is pure and unit-tested; this class only wires it to
 * a real `DataStore`.
 */
class CalendarSettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.calendarDataStore)

    val settings: Flow<CalendarSettings> = dataStore.data
        // A corrupt/absent store reads as defaults rather than crashing the block.
        .catch { emit(emptyPreferences()) }
        .map(Keys::decode)

    /** The current value once — used by the module at the start of a refresh. */
    suspend fun current(): CalendarSettings = settings.first()

    suspend fun setRange(range: CalendarRangeMode) {
        dataStore.edit { it[Keys.RANGE] = range.name }
    }

    suspend fun setCalendarSelected(id: Long, selected: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.DESELECTED] ?: emptySet()
            prefs[Keys.DESELECTED] =
                if (selected) current - id.toString() else current + id.toString()
        }
    }

    /** Preference keys and the pure decoder, kept together and testable. */
    object Keys {
        val RANGE = stringPreferencesKey("range")
        val DESELECTED = stringSetPreferencesKey("deselected_calendar_ids")

        /** Pure Preferences → model mapping. Unknown/absent values fall back to defaults. */
        fun decode(prefs: Preferences): CalendarSettings {
            val range = prefs[RANGE]
                ?.let { name -> CalendarRangeMode.entries.firstOrNull { it.name == name } }
                ?: CalendarSettings.DEFAULT.range
            val deselected = prefs[DESELECTED]
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: CalendarSettings.DEFAULT.deselectedCalendarIds
            return CalendarSettings(range = range, deselectedCalendarIds = deselected)
        }
    }
}
