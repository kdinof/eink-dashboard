package com.eink.dashboard.modules.weather

import com.eink.dashboard.modules.weather.data.LocationResolver
import com.eink.dashboard.modules.weather.model.GeoPoint
import com.eink.dashboard.modules.weather.model.LocationPresets
import com.eink.dashboard.modules.weather.model.LocationSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.ZoneId

/**
 * The location resolution rule: the block is never left without a place. The preset
 * needs no permission; Fixed uses manual coordinates or falls back; Device uses a
 * last-known fix or falls back to the preset (T05: "a missing location permission
 * must not break the Tashkent preset").
 */
class LocationResolverTest {

    private val zone = ZoneId.of("Asia/Tashkent")

    private fun resolver(fix: GeoPoint? = null) =
        LocationResolver(FakeDeviceLocationSource(fix), zoneProvider = { zone })

    @Test
    fun preset_resolvesToTashkent_withNoDeviceRead() {
        val device = FakeDeviceLocationSource(GeoPoint(0.0, 0.0))
        val resolved = LocationResolver(device) { zone }
            .resolve(WeatherSettings(locationMode = LocationMode.PRESET_TASHKENT))
        assertThat(resolved).isEqualTo(LocationPresets.TASHKENT)
        assertThat(resolved.source).isEqualTo(LocationSource.PRESET)
    }

    @Test
    fun fixed_withValidCoordinates_usesThem() {
        val settings = WeatherSettings(
            locationMode = LocationMode.FIXED,
            fixedLatitude = 51.5, fixedLongitude = -0.12, fixedLabel = "London",
        )
        val resolved = resolver().resolve(settings)
        assertThat(resolved.source).isEqualTo(LocationSource.FIXED)
        assertThat(resolved.latitude).isEqualTo(51.5)
        assertThat(resolved.longitude).isEqualTo(-0.12)
        assertThat(resolved.label).isEqualTo("London")
    }

    @Test
    fun fixed_withInvalidCoordinates_fallsBackToPreset() {
        val settings = WeatherSettings(
            locationMode = LocationMode.FIXED,
            fixedLatitude = 999.0, fixedLongitude = 0.0,
        )
        assertThat(resolver().resolve(settings)).isEqualTo(LocationPresets.TASHKENT)
    }

    @Test
    fun fixed_withNoCoordinates_fallsBackToPreset() {
        val settings = WeatherSettings(locationMode = LocationMode.FIXED)
        assertThat(resolver().resolve(settings)).isEqualTo(LocationPresets.TASHKENT)
    }

    @Test
    fun device_withFix_usesIt() {
        val resolved = resolver(GeoPoint(41.0, 69.0))
            .resolve(WeatherSettings(locationMode = LocationMode.DEVICE))
        assertThat(resolved.source).isEqualTo(LocationSource.DEVICE)
        assertThat(resolved.latitude).isEqualTo(41.0)
        assertThat(resolved.longitude).isEqualTo(69.0)
    }

    @Test
    fun device_withoutFixOrPermission_fallsBackToPreset() {
        // FakeDeviceLocationSource returns null == permission absent / no cached fix.
        val resolved = resolver(fix = null)
            .resolve(WeatherSettings(locationMode = LocationMode.DEVICE))
        assertThat(resolved).isEqualTo(LocationPresets.TASHKENT)
    }
}
