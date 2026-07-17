package com.eink.dashboard.modules.weather.data

import com.eink.dashboard.modules.weather.LocationMode
import com.eink.dashboard.modules.weather.WeatherSettings
import com.eink.dashboard.modules.weather.model.LocationPresets
import com.eink.dashboard.modules.weather.model.LocationSource
import com.eink.dashboard.modules.weather.model.ResolvedLocation
import java.time.ZoneId

/**
 * Turns the user's [WeatherSettings] into the concrete [ResolvedLocation] to fetch
 * for. The overriding rule from the T05 card is that the block is **never** left
 * without a place: the shipped Tashkent preset works with no permission at all, and
 * every other mode falls back to it when it cannot produce usable coordinates.
 *
 * - [LocationMode.PRESET_TASHKENT] → the fixed Tashkent preset.
 * - [LocationMode.FIXED] → the user-entered lat/long (no location permission needed),
 *   or the preset if those coordinates are missing/out of range.
 * - [LocationMode.DEVICE] → the opt-in last-known coarse fix, or the preset when the
 *   permission is absent / no fix is cached (see [DeviceLocationSource.lastKnown]).
 *
 * The [ResolvedLocation.timezone] set here is only a hint; the API is always asked
 * for `timezone=auto` and the response's zone is authoritative in the snapshot.
 */
class LocationResolver(
    private val deviceLocation: DeviceLocationSource,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun resolve(settings: WeatherSettings): ResolvedLocation = when (settings.locationMode) {
        LocationMode.PRESET_TASHKENT -> LocationPresets.TASHKENT

        LocationMode.FIXED ->
            if (settings.hasValidFixed) {
                ResolvedLocation(
                    label = settings.fixedLabel.ifBlank { "Custom" },
                    latitude = settings.fixedLatitude!!,
                    longitude = settings.fixedLongitude!!,
                    timezone = zoneProvider().id,
                    source = LocationSource.FIXED,
                )
            } else {
                LocationPresets.TASHKENT
            }

        LocationMode.DEVICE ->
            deviceLocation.lastKnown()?.let { fix ->
                ResolvedLocation(
                    label = "Current location",
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    timezone = zoneProvider().id,
                    source = LocationSource.DEVICE,
                )
            } ?: LocationPresets.TASHKENT
    }
}
