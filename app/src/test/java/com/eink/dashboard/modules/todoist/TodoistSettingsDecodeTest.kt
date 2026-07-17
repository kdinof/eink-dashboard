package com.eink.dashboard.modules.todoist

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eink.dashboard.modules.todoist.model.TodoistView
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure Preferences → [TodoistSettings] mapping: defaults, known value, bad value. */
class TodoistSettingsDecodeTest {

    @Test
    fun emptyPrefs_yieldDefaultView() {
        val settings = TodoistSettingsStore.Keys.decode(mutablePreferencesOf())
        assertThat(settings.view).isEqualTo(TodoistView.TODAY)
    }

    @Test
    fun knownView_isDecoded() {
        val prefs = mutablePreferencesOf(
            TodoistSettingsStore.Keys.VIEW to TodoistView.UPCOMING.name,
        )
        assertThat(TodoistSettingsStore.Keys.decode(prefs).view).isEqualTo(TodoistView.UPCOMING)
    }

    @Test
    fun unknownView_fallsBackToDefault() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("view") to "NOT_A_VIEW",
        )
        assertThat(TodoistSettingsStore.Keys.decode(prefs).view).isEqualTo(TodoistView.TODAY)
    }
}
