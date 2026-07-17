package com.eink.dashboard.modules.weather

/**
 * How the Weather block decides which coordinates to fetch for.
 * - [PRESET_TASHKENT]: the shipped fixed preset — works with no permission at all.
 * - [FIXED]: user-entered latitude/longitude (still no location permission needed).
 * - [DEVICE]: opt-in last-known device fix, falling back to the preset if the
 *   permission is absent or no fix is cached.
 */
enum class LocationMode { PRESET_TASHKENT, FIXED, DEVICE }

/**
 * User-configurable Weather settings. Location only — units are fixed (°C / km/h).
 * A [FIXED] mode without valid coordinates simply falls back to the preset, so the
 * block is never left without a place to show.
 */
data class WeatherSettings(
    val locationMode: LocationMode = LocationMode.PRESET_TASHKENT,
    val fixedLatitude: Double? = null,
    val fixedLongitude: Double? = null,
    val fixedLabel: String = "",
) {
    /** True when [FIXED] mode has usable coordinates. */
    val hasValidFixed: Boolean
        get() = fixedLatitude != null && fixedLongitude != null &&
            fixedLatitude in -90.0..90.0 && fixedLongitude in -180.0..180.0

    companion object {
        val DEFAULT = WeatherSettings()
    }
}
