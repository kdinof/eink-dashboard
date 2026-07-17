package com.eink.dashboard.modules.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.weather.data.InMemoryWeatherCache
import com.eink.dashboard.modules.weather.data.LocationResolver
import com.eink.dashboard.modules.weather.data.WeatherError
import com.eink.dashboard.modules.weather.model.CurrentConditions
import com.eink.dashboard.modules.weather.model.DailyConditions
import com.eink.dashboard.modules.weather.model.GeoPoint
import com.eink.dashboard.modules.weather.model.LocationPresets
import com.eink.dashboard.modules.weather.model.WeatherSnapshot
import com.eink.dashboard.modules.weather.model.WmoCondition
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/**
 * The module's state machine over the frozen [ModuleState] contract: contract
 * identity, fresh/stale/error mapping, the location the resolver picks for a fetch,
 * and that a missing device fix does not break the preset. Uses fakes + a real
 * in-memory settings store — no device, no network.
 */
class WeatherModuleTest {

    private fun tempStore(): WeatherSettingsStore {
        val dir = Files.createTempDirectory("weather-settings").toFile()
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) { File(dir, "weather_settings.preferences_pb") }
        return WeatherSettingsStore(ds)
    }

    private fun snapshot(temp: Double, fetchedAt: Long) = WeatherSnapshot(
        location = LocationPresets.TASHKENT,
        timezone = "Asia/Tashkent",
        current = CurrentConditions(temp, temp, 40, WmoCondition.CLEAR, 5.0, true, 0.0),
        forecast = listOf(
            DailyConditions(
                LocalDate.of(2026, 7, 17), WmoCondition.CLEAR, temp + 8, temp - 8,
                null, null, 0.0, 0,
            ),
        ),
        fetchedAtEpochMs = fetchedAt,
    )

    private fun module(
        api: FakeWeatherApi,
        cache: InMemoryWeatherCache = InMemoryWeatherCache(),
        deviceFix: GeoPoint? = null,
    ): WeatherModule = WeatherModule(
        repo = WeatherRepository(api, cache),
        settingsStore = tempStore(),
        locationResolver = LocationResolver(FakeDeviceLocationSource(deviceFix)) { ZoneId.of("Asia/Tashkent") },
    )

    @Test
    fun contractIdentity_isStable() {
        val m = module(FakeWeatherApi(snapshot = snapshot(20.0, 1L)))
        assertThat(m.id).isEqualTo("weather")
        assertThat(m.title).isEqualTo("Weather")
        assertThat(m.refreshPolicy).isInstanceOf(RefreshPolicy.Periodic::class.java)
        assertThat(m.isDemo).isFalse()
        assertThat(m.hasSettings).isTrue()
    }

    @Test
    fun refreshPolicy_isNotEveryMinute() {
        // T05: weather must not refresh on the once-a-minute tick.
        val m = module(FakeWeatherApi(snapshot = snapshot(20.0, 1L)))
        assertThat(m.refreshPolicy).isNotEqualTo(RefreshPolicy.EveryMinute)
        val periodic = m.refreshPolicy as RefreshPolicy.Periodic
        assertThat(periodic.minInterval.inWholeMinutes).isAtLeast(2L)
    }

    @Test
    fun success_yieldsOk_andPopulatesSnapshot(): Unit = runBlocking {
        val m = module(FakeWeatherApi(snapshot = snapshot(28.0, fetchedAt = 900L)))
        m.refresh(RefreshReason.INITIAL)
        val state = m.state.value
        assertThat(state).isInstanceOf(ModuleState.Ok::class.java)
        assertThat((state as ModuleState.Ok).isStale).isFalse()
        assertThat(state.lastUpdatedEpochMs).isEqualTo(900L)
        assertThat(m.snapshot.value?.current?.temperatureC).isEqualTo(28.0)
    }

    @Test
    fun networkErrorWithCache_marksStale_keepsSnapshot(): Unit = runBlocking {
        val cache = InMemoryWeatherCache()
        val api = FakeWeatherApi(snapshot = snapshot(22.0, fetchedAt = 500L))
        val m = module(api, cache)
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Ok::class.java)

        api.error = WeatherError.Network
        m.refresh(RefreshReason.MINUTE_TICK)
        val state = m.state.value
        assertThat(state).isInstanceOf(ModuleState.Ok::class.java)
        assertThat((state as ModuleState.Ok).isStale).isTrue()
        assertThat(m.snapshot.value?.current?.temperatureC).isEqualTo(22.0)
    }

    @Test
    fun networkErrorWithNoCache_yieldsError(): Unit = runBlocking {
        val m = module(FakeWeatherApi(error = WeatherError.Network))
        m.refresh(RefreshReason.INITIAL)
        assertThat(m.state.value).isInstanceOf(ModuleState.Error::class.java)
        assertThat(m.snapshot.value).isNull()
    }

    @Test
    fun presetMode_fetchesTashkent_evenWithNoDeviceFix(): Unit = runBlocking {
        val api = FakeWeatherApi(snapshot = snapshot(30.0, 1L))
        val m = module(api, deviceFix = null) // default settings == preset
        m.refresh(RefreshReason.INITIAL)
        assertThat(api.lastLocation).isEqualTo(LocationPresets.TASHKENT)
        assertThat(m.state.value).isInstanceOf(ModuleState.Ok::class.java)
    }
}
