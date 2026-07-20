package com.eink.dashboard.modules.taskforge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eink.dashboard.modules.taskforge.model.TaskForgeView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.taskForgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "taskforge_settings")

class TaskForgeSettingsStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.taskForgeDataStore)

    val settings: Flow<TaskForgeSettings> = dataStore.data.catch { emit(emptyPreferences()) }.map(Keys::decode)
    suspend fun current(): TaskForgeSettings = settings.first()

    suspend fun setFilter(view: TaskForgeView, tags: Set<String>, limit: Int) {
        require(limit in TaskForgeSettings.ALLOWED_LIMITS)
        dataStore.edit {
            it[Keys.VIEW] = view.name
            it[Keys.TAGS] = tags
            it[Keys.LIMIT] = limit
        }
    }

    suspend fun setFile(uri: String, name: String, canWrite: Boolean) = dataStore.edit {
        it[Keys.FILE_URI] = uri
        it[Keys.FILE_NAME] = name
        it[Keys.CAN_WRITE] = canWrite
    }

    suspend fun clearFile() = dataStore.edit {
        it.remove(Keys.FILE_URI); it.remove(Keys.FILE_NAME); it.remove(Keys.CAN_WRITE)
    }

    object Keys {
        val VIEW = stringPreferencesKey("view")
        val TAGS = stringSetPreferencesKey("selected_tags")
        val LIMIT = intPreferencesKey("limit")
        val FILE_URI = stringPreferencesKey("file_uri")
        val FILE_NAME = stringPreferencesKey("file_name")
        val CAN_WRITE = booleanPreferencesKey("can_write")

        fun decode(prefs: Preferences): TaskForgeSettings = TaskForgeSettings(
            view = prefs[VIEW]?.let { raw -> TaskForgeView.entries.firstOrNull { it.name == raw } } ?: TaskForgeView.TODAY,
            selectedTags = prefs[TAGS].orEmpty(),
            limit = prefs[LIMIT]?.takeIf { it in TaskForgeSettings.ALLOWED_LIMITS } ?: 10,
            fileUri = prefs[FILE_URI],
            fileName = prefs[FILE_NAME],
            canWrite = prefs[CAN_WRITE] ?: false,
        )
    }
}
