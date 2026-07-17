package com.eink.dashboard.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The pure Preferences -> [DashboardSettings] mapping. This is the risky part of
 * the store (schema drift, missing keys, bad enum names); the DataStore wiring
 * itself is trivial and is not re-tested here.
 */
class SettingsDecodeTest {

    @Test
    fun emptyPreferences_decodeToDefaults() {
        assertThat(SettingsStore.Keys.decode(emptyPreferences())).isEqualTo(DashboardSettings.DEFAULT)
    }

    @Test
    fun fullPreferences_decodeAllFields() {
        val prefs = mutablePreferencesOf(
            SettingsStore.Keys.ORIENTATION to OrientationSetting.LANDSCAPE.name,
            SettingsStore.Keys.KEEP_SCREEN_ON to false,
            SettingsStore.Keys.HIDDEN_MODULES to setOf("weather", "todoist"),
        )
        val decoded = SettingsStore.Keys.decode(prefs)
        assertThat(decoded.orientation).isEqualTo(OrientationSetting.LANDSCAPE)
        assertThat(decoded.keepScreenOn).isFalse()
        assertThat(decoded.hiddenModuleIds).containsExactly("weather", "todoist")
    }

    @Test
    fun unknownOrientationName_fallsBackToDefault() {
        val prefs = mutablePreferencesOf(
            SettingsStore.Keys.ORIENTATION to "SIDEWAYS",
        )
        assertThat(SettingsStore.Keys.decode(prefs).orientation)
            .isEqualTo(DashboardSettings.DEFAULT.orientation)
    }
}
