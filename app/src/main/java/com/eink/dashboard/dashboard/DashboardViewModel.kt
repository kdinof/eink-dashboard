package com.eink.dashboard.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eink.dashboard.BuildConfig
import com.eink.dashboard.modules.demo.demoModules
import com.eink.dashboard.settings.DashboardSettings
import com.eink.dashboard.settings.OrientationSetting
import com.eink.dashboard.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Top-level navigation destinations of the single Activity. */
enum class Screen { DASHBOARD, SETTINGS, DIAGNOSTICS }

/**
 * Holds the app graph for the single Activity: the module registry, the settings
 * store, and the foreground-only [RefreshCoordinator]. There is no DI framework
 * (see docs/adr/0001-architecture.md) — this ViewModel is the composition root.
 *
 * Demo modules are registered **only in debug builds** (`allowDemo = BuildConfig.DEBUG`);
 * a release build registers an empty registry until T03–T05 add real modules, so
 * sample data can never ship as if it were real.
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)

    val registry: DashboardModuleRegistry =
        DashboardModuleRegistry.builder(allowDemo = BuildConfig.DEBUG)
            .registerAll(if (BuildConfig.DEBUG) demoModules() else emptyList())
            .build()

    private val coordinator = RefreshCoordinator(registry, viewModelScope)

    /** Epoch millis of the latest tick — the header clock reads this. */
    val lastTick: StateFlow<Long> = coordinator.lastTickEpochMs

    /** Whether the refresh loop is running (foreground). For diagnostics. */
    val coordinatorRunning: StateFlow<Boolean> = coordinator.running

    val settings: StateFlow<DashboardSettings> = settingsStore.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardSettings.DEFAULT,
    )

    val hasDemoModules: Boolean = registry.all.any { it.isDemo }
    val buildType: String = if (BuildConfig.DEBUG) "debug" else "release"
    val versionName: String = BuildConfig.VERSION_NAME

    // ---- Lifecycle (called by the Activity) ----

    /** onResume: immediate refresh + start ticking. */
    fun onEnterForeground() = coordinator.start()

    /** onPause: stop the ticker; no background periodic work. */
    fun onEnterBackground() = coordinator.stop()

    fun refreshNow() = coordinator.requestManualRefresh()

    // ---- Settings mutations ----

    fun setOrientation(value: OrientationSetting) = viewModelScope.launch {
        settingsStore.setOrientation(value)
    }

    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch {
        settingsStore.setKeepScreenOn(value)
    }

    fun setModuleVisible(id: String, visible: Boolean) = viewModelScope.launch {
        settingsStore.setModuleVisible(id, visible)
        // A newly visible module should populate at once rather than wait a minute.
        coordinator.onSettingsChanged()
    }
}
