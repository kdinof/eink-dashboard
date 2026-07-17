package com.eink.dashboard.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.eink.dashboard.dashboard.DashboardModuleRegistry
import com.eink.dashboard.dashboard.RefreshCoordinator
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.calendar.CalendarModule
import com.eink.dashboard.modules.calendar.CalendarSettingsStore
import com.eink.dashboard.modules.calendar.CalendarSourceMode
import com.eink.dashboard.modules.calendar.model.CalendarRangeMode
import com.eink.dashboard.modules.todoist.TodoistModule
import com.eink.dashboard.modules.todoist.TodoistSettingsStore
import com.eink.dashboard.modules.todoist.model.TodoistView
import com.eink.dashboard.modules.weather.LocationMode
import com.eink.dashboard.modules.weather.WeatherModule
import com.eink.dashboard.modules.weather.WeatherSettingsStore
import com.eink.dashboard.settings.OrientationSetting
import com.eink.dashboard.settings.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared, validated settings facade used by both local UI callbacks and HTTP routes. */
class RemoteSettingsService(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val registry: DashboardModuleRegistry,
    private val coordinator: RefreshCoordinator,
    private val calendarModule: CalendarModule,
    private val todoistModule: TodoistModule,
    private val weatherModule: WeatherModule,
    val permissions: PermissionCoordinator,
) {
    private val calendarStore = CalendarSettingsStore(context)
    private val todoistStore = TodoistSettingsStore(context)
    private val weatherStore = WeatherSettingsStore(context)

    suspend fun snapshot(): RemoteConfig {
        val dashboard = settingsStore.settings.first()
        val calendar = calendarStore.settings.first()
        val todoist = todoistStore.settings.first()
        val weather = weatherStore.settings.first()
        val pending = permissions.pending.value?.permission
        val calendars = calendarModule.calendars.value
        return RemoteConfig(
            dashboard = DashboardConfig(
                orientation = dashboard.orientation.name,
                keepScreenOn = dashboard.keepScreenOn,
                modules = registry.all.map { ModuleConfig(it.id, it.title, dashboard.isModuleVisible(it.id)) },
            ),
            calendar = CalendarConfig(
                permissionGranted = calendarModule.permissionGranted.value,
                permissionPending = pending == RemotePermission.CALENDAR,
                range = calendar.range.name,
                selectedCalendarIds = calendars.map { it.id }.filter(calendar::isSelected).toSet(),
                calendars = calendars.map { CalendarOption(it.id, it.displayName, it.accountName) },
                source = calendar.source.name,
                googleConnected = calendarModule.googleConnected,
                googleBrokerConfigured = calendarModule.googleBrokerConfigured,
            ),
            todoist = TodoistConfig(todoist.view.name, todoistModule.hasToken.value),
            weather = WeatherConfig(
                locationPermissionGranted = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION),
                permissionPending = pending == RemotePermission.LOCATION,
                locationMode = weather.locationMode.name,
                fixedLatitude = weather.fixedLatitude,
                fixedLongitude = weather.fixedLongitude,
                fixedLabel = weather.fixedLabel,
            ),
        )
    }

    suspend fun updateDashboard(value: DashboardUpdate) {
        val orientation = enumValue<OrientationSetting>(value.orientation)
        val known = registry.ids.toSet()
        require(value.visibleModuleIds.all { it in known }) { "Unknown module" }
        settingsStore.setOrientation(orientation)
        settingsStore.setKeepScreenOn(value.keepScreenOn)
        registry.ids.forEach { settingsStore.setModuleVisible(it, it in value.visibleModuleIds) }
        coordinator.onSettingsChanged()
    }

    suspend fun setOrientation(value: OrientationSetting) = settingsStore.setOrientation(value)
    suspend fun setKeepScreenOn(value: Boolean) = settingsStore.setKeepScreenOn(value)
    suspend fun setModuleVisible(id: String, visible: Boolean) {
        require(id in registry.ids) { "Unknown module" }
        settingsStore.setModuleVisible(id, visible)
        coordinator.onSettingsChanged()
    }

    suspend fun updateCalendar(value: CalendarUpdate) {
        val range = enumValue<CalendarRangeMode>(value.range)
        val source = enumValue<CalendarSourceMode>(value.source)
        val available = calendarModule.calendars.value.map { it.id }.toSet()
        require(value.selectedCalendarIds.all { it in available }) { "Unknown calendar" }
        calendarStore.setRange(range)
        calendarStore.setSource(source)
        calendarStore.setSelectedCalendars(available, value.selectedCalendarIds)
        calendarModule.refresh(RefreshReason.SETTINGS_CHANGED)
    }

    suspend fun beginGoogleOAuth(returnUrl: String): GoogleConnectResponse = withContext(Dispatchers.IO) {
        val started = calendarModule.googleAuth?.begin(returnUrl)
            ?: error("Google OAuth is unavailable")
        GoogleConnectResponse(started.handoffId, started.authorizationUrl)
    }

    suspend fun completeGoogleOAuth(handoffId: String) = withContext(Dispatchers.IO) {
        calendarModule.googleAuth?.complete(handoffId) ?: error("Google OAuth is unavailable")
        calendarStore.setSource(CalendarSourceMode.GOOGLE)
        calendarModule.refresh(RefreshReason.SETTINGS_CHANGED)
    }

    suspend fun disconnectGoogle() = withContext(Dispatchers.IO) {
        calendarModule.googleAuth?.disconnect()
        calendarStore.setSource(CalendarSourceMode.DEVICE)
        calendarModule.refresh(RefreshReason.SETTINGS_CHANGED)
    }

    suspend fun updateTodoist(value: TodoistUpdate) {
        todoistStore.setView(enumValue<TodoistView>(value.view))
        todoistModule.refresh(RefreshReason.SETTINGS_CHANGED)
    }

    suspend fun updateTodoistToken(token: String): Boolean {
        require(token.isNotBlank() && token.length <= 512) { "Invalid token" }
        return todoistModule.saveAndVerifyToken(token.trim()) == null
    }

    suspend fun clearTodoistToken() = todoistModule.clearToken()

    suspend fun updateWeather(value: WeatherUpdate) {
        val mode = enumValue<LocationMode>(value.locationMode)
        if (mode == LocationMode.FIXED) {
            require(value.fixedLatitude != null && value.fixedLatitude in -90.0..90.0) { "Invalid latitude" }
            require(value.fixedLongitude != null && value.fixedLongitude in -180.0..180.0) { "Invalid longitude" }
        }
        weatherStore.setFixedLocation(value.fixedLatitude, value.fixedLongitude, value.fixedLabel.trim().take(80))
        weatherStore.setLocationMode(mode)
        weatherModule.refresh(RefreshReason.SETTINGS_CHANGED)
    }

    fun requestPermission(permission: RemotePermission): PendingPermissionRequest? = permissions.request(permission)

    suspend fun completePermission(id: String, permission: RemotePermission) {
        permissions.complete(id)
        when (permission) {
            RemotePermission.CALENDAR -> {
                calendarModule.refreshPermissionFlag()
                calendarModule.refresh(RefreshReason.SETTINGS_CHANGED)
            }
            RemotePermission.LOCATION -> weatherModule.refresh(RefreshReason.SETTINGS_CHANGED)
        }
    }


    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private inline fun <reified T : Enum<T>> enumValue(raw: String): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: throw IllegalArgumentException("Invalid value")
}
