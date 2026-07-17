package com.eink.dashboard.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class RemotePermission { CALENDAR, LOCATION }

data class PendingPermissionRequest(val id: String, val permission: RemotePermission)

/** Bridges an HTTP request to the Activity-owned runtime-permission launcher. */
class PermissionCoordinator {
    private val _pending = MutableStateFlow<PendingPermissionRequest?>(null)
    val pending: StateFlow<PendingPermissionRequest?> = _pending.asStateFlow()

    @Synchronized
    fun request(permission: RemotePermission): PendingPermissionRequest? {
        if (_pending.value != null) return null
        return PendingPermissionRequest(UUID.randomUUID().toString(), permission).also { _pending.value = it }
    }

    @Synchronized
    fun complete(id: String) {
        if (_pending.value?.id == id) _pending.value = null
    }

}
