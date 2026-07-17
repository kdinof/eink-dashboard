package com.eink.dashboard.modules.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.eink.dashboard.modules.weather.model.GeoPoint

/**
 * Opt-in device location — read **once, on demand, from last-known fixes only**.
 * The T05 card requires geolocation to be opt-in "without a permanent GPS": we never
 * request active location updates, we only read the coarse provider's cached fix, so
 * there is no background GPS session and no continuous listener.
 *
 * [lastKnown] returns `null` (never throws) when the permission is absent or no fix
 * is cached, so a missing permission degrades gracefully to the preset fallback in
 * [LocationResolver] rather than breaking the block.
 */
interface DeviceLocationSource {
    /** Latest cached coarse fix, or `null` if unavailable / not permitted. */
    fun lastKnown(): GeoPoint?
}

/** The `ACCESS_COARSE_LOCATION` runtime permission this module may request (opt-in). */
const val COARSE_LOCATION_PERMISSION: String = Manifest.permission.ACCESS_COARSE_LOCATION

/** Runtime coarse-location permission check, mockable in tests. */
fun interface LocationPermission {
    fun isGranted(): Boolean
}

/** Real permission check against the app context. */
class AndroidLocationPermission(private val context: Context) : LocationPermission {
    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, COARSE_LOCATION_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Reads the last-known coarse fix via [LocationManager]. Guarded by [permission];
 * with no grant it returns `null` without touching the system service. Coarse
 * providers only (network / passive) — the GPS provider is never engaged, keeping
 * the read cheap and power-free.
 */
class AndroidDeviceLocationSource(
    private val context: Context,
    private val permission: LocationPermission = AndroidLocationPermission(context),
) : DeviceLocationSource {

    override fun lastKnown(): GeoPoint? {
        if (!permission.isGranted()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        // Try coarse providers, most-useful first; never the active GPS provider.
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        return try {
            providers
                .filter { manager.isProviderEnabled(it) }
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (_: SecurityException) {
            null
        }
    }
}
