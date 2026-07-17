package com.eink.dashboard.modules.todoist

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eink.dashboard.modules.todoist.model.TodoistView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.todoistDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "todoist_settings")

/**
 * DataStore persistence for [TodoistSettings], kept entirely inside the Todoist
 * module (its own `todoist_settings` file). Holds only the chosen view — no secret.
 * The Preferences ↔ model translation ([Keys.decode]) is pure and unit-tested.
 */
class TodoistSettingsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.todoistDataStore)

    val settings: Flow<TodoistSettings> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map(Keys::decode)

    suspend fun current(): TodoistSettings = settings.first()

    suspend fun setView(view: TodoistView) {
        dataStore.edit { it[Keys.VIEW] = view.name }
    }

    object Keys {
        val VIEW = stringPreferencesKey("view")

        fun decode(prefs: Preferences): TodoistSettings {
            val view = prefs[VIEW]
                ?.let { name -> TodoistView.entries.firstOrNull { it.name == name } }
                ?: TodoistSettings.DEFAULT.view
            return TodoistSettings(view = view)
        }
    }
}
