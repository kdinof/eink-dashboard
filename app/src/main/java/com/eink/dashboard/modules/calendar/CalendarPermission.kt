package com.eink.dashboard.modules.calendar

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** The single runtime permission this module needs. `WRITE_CALENDAR` is never requested. */
const val READ_CALENDAR_PERMISSION: String = android.Manifest.permission.READ_CALENDAR

/**
 * Whether the app currently holds `READ_CALENDAR`.
 *
 * A one-method interface (not a direct `ContextCompat` call) so the module can be
 * unit-tested for the permission-denied path without a device — tests pass a fake
 * that returns `false`/`true`.
 */
fun interface CalendarPermission {
    fun isGranted(): Boolean
}

/** Real check against the running app's granted permissions. */
class AndroidCalendarPermission(private val context: Context) : CalendarPermission {
    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, READ_CALENDAR_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
}
