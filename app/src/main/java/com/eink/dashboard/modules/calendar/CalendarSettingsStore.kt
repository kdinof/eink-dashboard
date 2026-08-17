package com.eink.dashboard.modules.calendar

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
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
        dataStore.edit {
            migrateLegacySelection(it)
            it[Keys.RANGE] = range.name
        }
    }

    suspend fun setSource(source: CalendarSourceMode) {
        dataStore.edit {
            migrateLegacySelection(it)
            it[Keys.SOURCE] = source.name
        }
    }

    suspend fun setCalendarSelected(id: Long, selected: Boolean) {
        setCalendarSelected(current().source, id, selected)
    }

    suspend fun setCalendarSelected(source: CalendarSourceMode, id: Long, selected: Boolean) {
        dataStore.edit { prefs ->
            migrateLegacySelection(prefs)
            val key = Keys.deselectedKey(source)
            val current = prefs[key] ?: emptySet()
            prefs[key] =
                if (selected) current - id.toString() else current + id.toString()
        }
    }

    suspend fun setSelectedCalendars(
        source: CalendarSourceMode,
        availableIds: Set<Long>,
        selectedIds: Set<Long>,
    ) {
        dataStore.edit { prefs ->
            migrateLegacySelection(prefs)
            prefs[Keys.deselectedKey(source)] = (availableIds - selectedIds).map(Long::toString).toSet()
        }
    }

    /** Apply the remote form as one transaction so refresh never observes half-written settings. */
    suspend fun update(
        range: CalendarRangeMode,
        source: CalendarSourceMode,
        availableIds: Set<Long>? = null,
        selectedIds: Set<Long>? = null,
    ) {
        dataStore.edit { prefs ->
            migrateLegacySelection(prefs)
            prefs[Keys.RANGE] = range.name
            prefs[Keys.SOURCE] = source.name
            if (availableIds != null && selectedIds != null) {
                prefs[Keys.deselectedKey(source)] =
                    (availableIds - selectedIds).map(Long::toString).toSet()
            }
        }
    }

    /** Materialize the old shared selection before a source switch changes its meaning. */
    private fun migrateLegacySelection(prefs: MutablePreferences) {
        val legacy = prefs[Keys.DESELECTED] ?: return
        val oldSource = prefs[Keys.SOURCE]
            ?.let { raw -> CalendarSourceMode.entries.firstOrNull { it.name == raw } }
            ?: CalendarSourceMode.DEVICE
        if (prefs[Keys.deselectedKey(oldSource)] == null) {
            prefs[Keys.deselectedKey(oldSource)] = legacy
        }
        prefs.remove(Keys.DESELECTED)
    }

    /** Preference keys and the pure decoder, kept together and testable. */
    object Keys {
        val RANGE = stringPreferencesKey("range")
        /** Legacy shared key, read once for backwards-compatible migration. */
        val DESELECTED = stringSetPreferencesKey("deselected_calendar_ids")
        val DEVICE_DESELECTED = stringSetPreferencesKey("device_deselected_calendar_ids")
        val GOOGLE_DESELECTED = stringSetPreferencesKey("google_deselected_calendar_ids")
        val SOURCE = stringPreferencesKey("calendar_source")

        fun deselectedKey(source: CalendarSourceMode) = when (source) {
            CalendarSourceMode.DEVICE -> DEVICE_DESELECTED
            CalendarSourceMode.GOOGLE -> GOOGLE_DESELECTED
        }

        /** Pure Preferences → model mapping. Unknown/absent values fall back to defaults. */
        fun decode(prefs: Preferences): CalendarSettings {
            val range = prefs[RANGE]
                ?.let { name -> CalendarRangeMode.entries.firstOrNull { it.name == name } }
                ?: CalendarSettings.DEFAULT.range
            val source = prefs[SOURCE]
                ?.let { name -> CalendarSourceMode.entries.firstOrNull { it.name == name } }
                ?: CalendarSourceMode.DEVICE
            fun ids(key: Preferences.Key<Set<String>>): Set<Long>? =
                prefs[key]?.mapNotNull(String::toLongOrNull)?.toSet()
            // Old installations stored only the active source's ids in DESELECTED.
            val legacy = ids(DESELECTED).orEmpty()
            return CalendarSettings(
                range = range,
                source = source,
                deviceDeselectedCalendarIds = ids(DEVICE_DESELECTED)
                    ?: if (source == CalendarSourceMode.DEVICE) legacy else emptySet(),
                googleDeselectedCalendarIds = ids(GOOGLE_DESELECTED)
                    ?: if (source == CalendarSourceMode.GOOGLE) legacy else emptySet(),
            )
        }
    }
}
