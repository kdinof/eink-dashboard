package com.eink.dashboard.settings

/**
 * User-controlled dashboard settings, persisted by [SettingsStore].
 *
 * @property orientation how the single Activity is locked (or follows the system)
 * @property keepScreenOn whether the panel is held awake while the dashboard is
 *   in the foreground — a wall-mounted e-ink dashboard usually wants this on
 * @property hiddenModuleIds ids of modules the user has hidden; everything not
 *   listed here is visible, so a newly added module shows up by default
 */
data class DashboardSettings(
    val orientation: OrientationSetting = OrientationSetting.SYSTEM,
    val keepScreenOn: Boolean = true,
    val hiddenModuleIds: Set<String> = emptySet(),
) {
    fun isModuleVisible(id: String): Boolean = id !in hiddenModuleIds

    /** Filter [orderedIds] (canonical order) down to the visible ones. */
    fun visibleAmong(orderedIds: List<String>): List<String> =
        orderedIds.filter(::isModuleVisible)

    companion object {
        val DEFAULT = DashboardSettings()
    }
}

/** Requested screen orientation. Maps to `ActivityInfo.screenOrientation` at the Activity. */
enum class OrientationSetting {
    /** Follow the device sensor / system setting. */
    SYSTEM,
    PORTRAIT,
    LANDSCAPE,
}
