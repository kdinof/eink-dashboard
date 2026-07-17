package com.eink.dashboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dashboardDataStore: DataStore<Preferences> by preferencesDataStore(name = "dashboard_settings")

/**
 * DataStore-backed persistence for [DashboardSettings]. The Preferences ↔ model
 * translation ([Keys.decode] / the write lambdas) is pure and unit-tested; this
 * class only wires it to a real `DataStore`.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.dashboardDataStore)

    val settings: Flow<DashboardSettings> = dataStore.data
        // A corrupt/absent store reads as defaults rather than crashing the dashboard.
        .catch { emit(emptyPreferences()) }
        .map(Keys::decode)

    suspend fun setOrientation(value: OrientationSetting) {
        dataStore.edit { it[Keys.ORIENTATION] = value.name }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }
    }

    suspend fun setModuleVisible(id: String, visible: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_MODULES] ?: emptySet()
            prefs[Keys.HIDDEN_MODULES] = if (visible) current - id else current + id
        }
    }

    /** Preference keys and the pure decoder, kept together and testable. */
    object Keys {
        val ORIENTATION = stringPreferencesKey("orientation")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val HIDDEN_MODULES = stringSetPreferencesKey("hidden_modules")

        /** Pure Preferences → model mapping. Unknown/absent values fall back to [DashboardSettings.DEFAULT]. */
        fun decode(prefs: Preferences): DashboardSettings {
            val orientation = prefs[ORIENTATION]
                ?.let { name -> OrientationSetting.entries.firstOrNull { it.name == name } }
                ?: DashboardSettings.DEFAULT.orientation
            return DashboardSettings(
                orientation = orientation,
                keepScreenOn = prefs[KEEP_SCREEN_ON] ?: DashboardSettings.DEFAULT.keepScreenOn,
                hiddenModuleIds = prefs[HIDDEN_MODULES] ?: DashboardSettings.DEFAULT.hiddenModuleIds,
            )
        }
    }
}
