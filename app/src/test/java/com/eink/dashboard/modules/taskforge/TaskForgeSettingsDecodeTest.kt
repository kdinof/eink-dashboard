package com.eink.dashboard.modules.taskforge

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.eink.dashboard.modules.taskforge.model.TaskForgeView
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskForgeSettingsDecodeTest {
    @Test fun defaultsAreTodayAndTenTasks() {
        val settings = TaskForgeSettingsStore.Keys.decode(mutablePreferencesOf())
        assertThat(settings.view).isEqualTo(TaskForgeView.TODAY)
        assertThat(settings.limit).isEqualTo(10)
        assertThat(settings.selectedTags).isEmpty()
    }

    @Test fun invalidPersistedValuesFallBackSafely() {
        val settings = TaskForgeSettingsStore.Keys.decode(mutablePreferencesOf(
            TaskForgeSettingsStore.Keys.VIEW to "UNKNOWN",
            TaskForgeSettingsStore.Keys.LIMIT to 999,
        ))
        assertThat(settings.view).isEqualTo(TaskForgeView.TODAY)
        assertThat(settings.limit).isEqualTo(10)
    }
}
