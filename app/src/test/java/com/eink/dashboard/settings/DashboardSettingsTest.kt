package com.eink.dashboard.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure visibility logic used by the shell to pick which blocks to draw. */
class DashboardSettingsTest {

    private val order = listOf("calendar", "todoist", "weather")

    @Test
    fun default_allModulesVisible() {
        val settings = DashboardSettings.DEFAULT
        assertThat(settings.visibleAmong(order)).isEqualTo(order)
        order.forEach { assertThat(settings.isModuleVisible(it)).isTrue() }
    }

    @Test
    fun hiddenModule_isFilteredOut_orderPreserved() {
        val settings = DashboardSettings(hiddenModuleIds = setOf("todoist"))
        assertThat(settings.isModuleVisible("todoist")).isFalse()
        assertThat(settings.visibleAmong(order)).containsExactly("calendar", "weather").inOrder()
    }

    @Test
    fun unknownVisibleModule_defaultsVisible() {
        // A module not in the hidden set is visible even if never seen before.
        assertThat(DashboardSettings.DEFAULT.isModuleVisible("newly.added")).isTrue()
    }
}
