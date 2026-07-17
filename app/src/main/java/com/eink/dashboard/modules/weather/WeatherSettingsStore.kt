package com.eink.dashboard.modules.weather

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.weatherSettingsStore: DataStore<Preferences> by
    preferencesDataStore(name = "weather_settings")

/**
 * DataStore-backed persistence for [WeatherSettings], kept inside the Weather module
 * (its own `weather_settings` file, separate from the snapshot cache). The
 * Preferences ↔ model translation ([Keys.decode]) is pure and unit-tested; this class
 * only wires it to a real `DataStore`. A corrupt/absent store reads as the Tashkent
 * preset default rather than crashing the block.
 */
class WeatherSettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.weatherSettingsStore)

    val settings: Flow<WeatherSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map(Keys::decode)

    /** The current value once — read by the module at the start of a refresh. */
    suspend fun current(): WeatherSettings = settings.first()

    suspend fun setLocationMode(mode: LocationMode) {
        dataStore.edit { it[Keys.MODE] = mode.name }
    }

    /** Set (or clear, with a null pair) the fixed coordinates and their label. */
    suspend fun setFixedLocation(latitude: Double?, longitude: Double?, label: String) {
        dataStore.edit { prefs ->
            if (latitude != null && longitude != null) {
                prefs[Keys.LAT] = latitude
                prefs[Keys.LON] = longitude
            } else {
                prefs.remove(Keys.LAT)
                prefs.remove(Keys.LON)
            }
            prefs[Keys.LABEL] = label
        }
    }

    /** Preference keys and the pure decoder, kept together and testable. */
    object Keys {
        val MODE = stringPreferencesKey("location_mode")
        val LAT = doublePreferencesKey("fixed_lat")
        val LON = doublePreferencesKey("fixed_lon")
        val LABEL = stringPreferencesKey("fixed_label")

        /** Pure Preferences → model mapping. Unknown/absent values fall back to defaults. */
        fun decode(prefs: Preferences): WeatherSettings {
            val mode = prefs[MODE]
                ?.let { name -> LocationMode.entries.firstOrNull { it.name == name } }
                ?: WeatherSettings.DEFAULT.locationMode
            return WeatherSettings(
                locationMode = mode,
                fixedLatitude = prefs[LAT],
                fixedLongitude = prefs[LON],
                fixedLabel = prefs[LABEL] ?: WeatherSettings.DEFAULT.fixedLabel,
            )
        }
    }
}
