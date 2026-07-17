package com.eink.dashboard.modules.weather

import com.eink.dashboard.modules.weather.data.DeviceLocationSource
import com.eink.dashboard.modules.weather.data.WeatherApi
import com.eink.dashboard.modules.weather.data.WeatherError
import com.eink.dashboard.modules.weather.model.GeoPoint
import com.eink.dashboard.modules.weather.model.ResolvedLocation
import com.eink.dashboard.modules.weather.model.WeatherSnapshot

/**
 * In-memory [WeatherApi] for repository/module tests — no Retrofit, no network. It
 * returns [snapshot] on success or throws [error] if set, and records the last
 * location it was asked for so tests can assert which coordinates were fetched.
 */
class FakeWeatherApi(
    var snapshot: WeatherSnapshot? = null,
    var error: WeatherError? = null,
) : WeatherApi {
    var calls: Int = 0
        private set
    var lastLocation: ResolvedLocation? = null
        private set

    override suspend fun fetch(location: ResolvedLocation): WeatherSnapshot {
        calls++
        lastLocation = location
        error?.let { throw it }
        return snapshot ?: throw WeatherError.BadResponse
    }
}

/** Fake device fix, toggleable to simulate the permission/last-known-fix outcomes. */
class FakeDeviceLocationSource(private var fix: GeoPoint? = null) : DeviceLocationSource {
    override fun lastKnown(): GeoPoint? = fix

    fun setFix(point: GeoPoint?) {
        fix = point
    }
}
