package com.eink.dashboard.modules.weather

import com.eink.dashboard.modules.weather.data.InMemoryWeatherCache
import com.eink.dashboard.modules.weather.data.WeatherError
import com.eink.dashboard.modules.weather.model.CurrentConditions
import com.eink.dashboard.modules.weather.model.DailyConditions
import com.eink.dashboard.modules.weather.model.LocationPresets
import com.eink.dashboard.modules.weather.model.WeatherSnapshot
import com.eink.dashboard.modules.weather.model.WmoCondition
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDate

/**
 * Repository orchestration over the API + cache seams: success caches and returns
 * Fresh, a failure with a cache returns Stale (T05: "offline cache" / "cache fallback"),
 * and a failure with nothing cached returns Failed.
 */
class WeatherRepositoryTest {

    private fun snapshot(temp: Double, fetchedAt: Long) = WeatherSnapshot(
        location = LocationPresets.TASHKENT,
        timezone = "Asia/Tashkent",
        current = CurrentConditions(
            temperatureC = temp, apparentTemperatureC = temp, humidityPercent = 40,
            condition = WmoCondition.CLEAR, windKmh = 5.0, isDay = true, precipitationMm = 0.0,
        ),
        forecast = listOf(
            DailyConditions(
                date = LocalDate.of(2026, 7, 17), condition = WmoCondition.CLEAR,
                temperatureMaxC = temp + 8, temperatureMinC = temp - 8,
                sunrise = null, sunset = null, precipitationSumMm = 0.0,
                precipitationProbabilityMaxPercent = 0,
            ),
        ),
        fetchedAtEpochMs = fetchedAt,
    )

    @Test
    fun success_returnsFresh_andWritesCache() = runBlocking {
        val cache = InMemoryWeatherCache()
        val api = FakeWeatherApi(snapshot = snapshot(20.0, fetchedAt = 100L))
        val repo = WeatherRepository(api, cache)

        val result = repo.load(LocationPresets.TASHKENT)

        assertThat(result).isInstanceOf(WeatherLoad.Fresh::class.java)
        assertThat((result as WeatherLoad.Fresh).snapshot.current.temperatureC).isEqualTo(20.0)
        // Persisted for the next offline launch.
        assertThat(cache.load()?.current?.temperatureC).isEqualTo(20.0)
        assertThat(api.lastLocation).isEqualTo(LocationPresets.TASHKENT)
    }

    @Test
    fun failureWithCache_returnsStale() = runBlocking {
        val cache = InMemoryWeatherCache()
        val warm = FakeWeatherApi(snapshot = snapshot(18.0, fetchedAt = 50L))
        WeatherRepository(warm, cache).load(LocationPresets.TASHKENT) // seed the cache

        val offline = FakeWeatherApi(error = WeatherError.Network)
        val result = WeatherRepository(offline, cache).load(LocationPresets.TASHKENT)

        assertThat(result).isInstanceOf(WeatherLoad.Stale::class.java)
        val stale = result as WeatherLoad.Stale
        assertThat(stale.snapshot.current.temperatureC).isEqualTo(18.0)
        assertThat(stale.error).isEqualTo(WeatherError.Network)
    }

    @Test
    fun failureWithoutCache_returnsFailed() = runBlocking {
        val result = WeatherRepository(
            FakeWeatherApi(error = WeatherError.Server(500)),
            InMemoryWeatherCache(),
        ).load(LocationPresets.TASHKENT)

        assertThat(result).isInstanceOf(WeatherLoad.Failed::class.java)
        assertThat((result as WeatherLoad.Failed).error).isEqualTo(WeatherError.Server(500))
    }

    @Test
    fun success_overwritesOlderCache() = runBlocking {
        val cache = InMemoryWeatherCache()
        WeatherRepository(FakeWeatherApi(snapshot(10.0, 1L)), cache).load(LocationPresets.TASHKENT)
        WeatherRepository(FakeWeatherApi(snapshot(25.0, 2L)), cache).load(LocationPresets.TASHKENT)
        assertThat(cache.load()?.current?.temperatureC).isEqualTo(25.0)
        assertThat(cache.load()?.fetchedAtEpochMs).isEqualTo(2L)
    }
}
