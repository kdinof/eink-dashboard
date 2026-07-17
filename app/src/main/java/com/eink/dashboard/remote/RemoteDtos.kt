package com.eink.dashboard.remote

import kotlinx.serialization.Serializable

@Serializable data class PairRequest(val pin: String, val clientName: String = "Browser")
@Serializable data class PairResponse(val token: String, val sessionId: String)
@Serializable data class ApiError(val error: String)
@Serializable data class PermissionResponse(val requestId: String, val status: String = "pending_on_reader")

@Serializable
data class RemoteConfig(
    val dashboard: DashboardConfig,
    val calendar: CalendarConfig,
    val todoist: TodoistConfig,
    val weather: WeatherConfig,
)

@Serializable data class ModuleConfig(val id: String, val title: String, val visible: Boolean)
@Serializable data class DashboardConfig(
    val orientation: String,
    val keepScreenOn: Boolean,
    val modules: List<ModuleConfig>,
)
@Serializable data class CalendarOption(val id: Long, val name: String, val accountName: String)
@Serializable data class CalendarConfig(
    val permissionGranted: Boolean,
    val permissionPending: Boolean,
    val range: String,
    val selectedCalendarIds: Set<Long>,
    val calendars: List<CalendarOption>,
    val source: String,
    val googleConnected: Boolean,
    val googleBrokerConfigured: Boolean,
)
@Serializable data class TodoistConfig(val view: String, val todoistTokenConfigured: Boolean)
@Serializable data class WeatherConfig(
    val locationPermissionGranted: Boolean,
    val permissionPending: Boolean,
    val locationMode: String,
    val fixedLatitude: Double? = null,
    val fixedLongitude: Double? = null,
    val fixedLabel: String,
)

@Serializable data class DashboardUpdate(
    val orientation: String,
    val keepScreenOn: Boolean,
    val visibleModuleIds: Set<String>,
)
@Serializable data class CalendarUpdate(
    val range: String,
    val selectedCalendarIds: Set<Long>,
    val source: String = "DEVICE",
)
@Serializable data class GoogleConnectResponse(val handoffId: String, val authorizationUrl: String)
@Serializable data class GoogleCompleteRequest(val handoffId: String)
@Serializable data class TodoistUpdate(val view: String)
@Serializable data class TodoistTokenUpdate(val token: String)
@Serializable data class WeatherUpdate(
    val locationMode: String,
    val fixedLatitude: Double? = null,
    val fixedLongitude: Double? = null,
    val fixedLabel: String = "",
)
@Serializable data class SessionDto(
    val id: String,
    val clientName: String,
    val createdAtEpochMs: Long,
    val lastUsedAtEpochMs: Long,
)
