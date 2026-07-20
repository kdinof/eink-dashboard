package com.eink.dashboard.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eink.dashboard.EinkDashApp
import com.eink.dashboard.BuildConfig
import com.eink.dashboard.settings.DashboardSettings
import com.eink.dashboard.settings.OrientationSetting
import com.eink.dashboard.settings.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.eink.dashboard.remote.RemotePermission

/** Top-level navigation destinations of the single Activity. */
enum class Screen { DASHBOARD, SETTINGS, REMOTE, DIAGNOSTICS }

/**
 * Holds the app graph for the single Activity: the module registry, the settings
 * store, and the foreground-only [RefreshCoordinator]. There is no DI framework
 * (see docs/adr/0001-architecture.md) — this ViewModel is the composition root.
 *
 * Only real product modules are registered at runtime, in every build type. The
 * registry is still built with `allowDemo = BuildConfig.DEBUG` so the demo-safety
 * guard stays live, but no demo module is ever registered — the sample modules
 * survive purely as `RefreshCoordinator` test fixtures (see `RefreshCoordinatorTest`).
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = (app as EinkDashApp).graph
    private val settingsStore = graph.settingsStore

    val registry: DashboardModuleRegistry = graph.registry

    private val coordinator = graph.coordinator

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
        graph.remoteSettings.setOrientation(value)
    }

    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch {
        graph.remoteSettings.setKeepScreenOn(value)
    }

    fun setModuleVisible(id: String, visible: Boolean) = viewModelScope.launch {
        graph.remoteSettings.setModuleVisible(id, visible)
    }

    val remoteServer get() = graph.remoteServer
    val pairing get() = graph.pairing
    val permissions get() = graph.permissions
    val taskForgeModule get() = graph.taskForgeModule

    fun regeneratePairingPin() = graph.pairing.regeneratePin()
    fun revokeSession(id: String) = graph.pairing.revoke(id)
    fun revokeAllSessions() = graph.pairing.revokeAll()
    fun completeRemotePermission(id: String, permission: RemotePermission) = viewModelScope.launch {
        graph.remoteSettings.completePermission(id, permission)
    }

    fun completeTaskForgeFileSelection(id: String, uri: android.net.Uri?) = viewModelScope.launch {
        graph.remoteSettings.completeTaskForgeFileSelection(id, uri)
    }
}
