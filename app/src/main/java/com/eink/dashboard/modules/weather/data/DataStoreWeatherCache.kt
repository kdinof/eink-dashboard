package com.eink.dashboard.modules.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eink.dashboard.modules.weather.model.WeatherSnapshot
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.weatherCacheStore: DataStore<Preferences> by
    preferencesDataStore(name = "weather_cache")

/**
 * DataStore-backed [WeatherCache]: the latest snapshot survives process death so a
 * cold launch offline still shows the last known weather. Persisted as one JSON
 * string ([StoredSnapshot]); a decode failure (schema drift, corruption) is treated
 * as "no cache" rather than crashing.
 */
class DataStoreWeatherCache(private val dataStore: DataStore<Preferences>) : WeatherCache {

    constructor(context: Context) : this(context.applicationContext.weatherCacheStore)

    override suspend fun save(snapshot: WeatherSnapshot) {
        val encoded = json.encodeToString(StoredSnapshot.serializer(), snapshot.toStored())
        dataStore.edit { it[KEY] = encoded }
    }

    override suspend fun load(): WeatherSnapshot? = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[KEY]?.let(::decode) }
        .first()

    private fun decode(raw: String): WeatherSnapshot? = try {
        json.decodeFromString(StoredSnapshot.serializer(), raw).toDomain()
    } catch (_: Exception) {
        null
    }

    private companion object {
        val KEY = stringPreferencesKey("snapshot_json")
        val json = Json { ignoreUnknownKeys = true }
    }
}
