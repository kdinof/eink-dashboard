package com.eink.dashboard.app

import android.app.Application
import com.eink.dashboard.BuildConfig
import com.eink.dashboard.dashboard.DashboardModuleRegistry
import com.eink.dashboard.dashboard.RefreshCoordinator
import com.eink.dashboard.modules.battery.BatteryModule
import com.eink.dashboard.modules.calendar.CalendarModule
import com.eink.dashboard.modules.clock.ClockModule
import com.eink.dashboard.modules.todoist.TodoistModule
import com.eink.dashboard.modules.taskforge.TaskForgeModule
import com.eink.dashboard.modules.weather.WeatherModule
import com.eink.dashboard.remote.PairingManager
import com.eink.dashboard.remote.PermissionCoordinator
import com.eink.dashboard.remote.RemoteSettingsService
import com.eink.dashboard.remote.RemoteWebServer
import com.eink.dashboard.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** One application-scoped object graph shared by Compose and the local web API. */
class AppGraph(app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsStore = SettingsStore(app)
    val calendarModule = CalendarModule.create(app)
    val todoistModule = TodoistModule.create(app)
    val taskForgeModule = TaskForgeModule.create(app)
    val weatherModule = WeatherModule.create(app)

    val registry: DashboardModuleRegistry =
        DashboardModuleRegistry.builder(allowDemo = BuildConfig.DEBUG)
            .register(calendarModule)
            .register(todoistModule)
            .register(taskForgeModule)
            .register(ClockModule())
            .register(weatherModule)
            .register(BatteryModule.create(app))
            .build()

    val coordinator = RefreshCoordinator(registry, scope)
    val permissions = PermissionCoordinator()
    val pairing = PairingManager(app)
    val remoteSettings = RemoteSettingsService(
        context = app,
        settingsStore = settingsStore,
        registry = registry,
        coordinator = coordinator,
        calendarModule = calendarModule,
        todoistModule = todoistModule,
        taskForgeModule = taskForgeModule,
        weatherModule = weatherModule,
        permissions = permissions,
    )
    val remoteServer = RemoteWebServer(app, remoteSettings, pairing)
}
