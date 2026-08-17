package com.eink.dashboard.modules.weather

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.DataWidgetRefreshPolicy
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.weather.data.AndroidDeviceLocationSource
import com.eink.dashboard.modules.weather.data.DataStoreWeatherCache
import com.eink.dashboard.modules.weather.data.LocationResolver
import com.eink.dashboard.modules.weather.data.RetrofitWeatherApi
import com.eink.dashboard.modules.weather.model.WeatherSnapshot
import com.eink.dashboard.modules.weather.ui.WeatherContent
import com.eink.dashboard.modules.weather.ui.WeatherSettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Weather block (T05) over the official keyless Open-Meteo forecast API.
 *
 * The shell drives all cadence via [refreshPolicy]; this module starts no loop of
 * its own. On each [refresh] it resolves the configured location ([LocationResolver],
 * which always yields at least the Tashkent preset — no permission required), asks
 * [WeatherRepository] for a live forecast, and maps the outcome onto the frozen
 * [ModuleState] the shell renders chrome from. The rich body (current conditions +
 * 7-day forecast) is drawn in [Content]; location settings live in [SettingsContent].
 *
 * State mapping:
 * - live fetch succeeds → [ModuleState.Ok] (fresh);
 * - live fetch fails but a cached snapshot exists → [ModuleState.Ok] with `isStale`;
 * - live fetch fails with nothing cached → [ModuleState.Error].
 *
 * The refresh policy is [RefreshPolicy.Periodic] at 10 minutes: weather never
 * refreshes on the once-a-minute clock tick (T05: "do not refresh weather every
 * minute"), only on resume/manual/settings-change or every 10 minutes in foreground.
 */
class WeatherModule(
    private val repo: WeatherRepository,
    private val settingsStore: WeatherSettingsStore,
    private val locationResolver: LocationResolver,
) : DashboardModule {

    override val id: String = "weather"
    override val title: String = "Weather"
    override val refreshPolicy: RefreshPolicy = DataWidgetRefreshPolicy
    override val hasSettings: Boolean = true

    private val _state = MutableStateFlow<ModuleState>(ModuleState.Loading)
    override val state: StateFlow<ModuleState> = _state.asStateFlow()

    /** Latest snapshot for [Content]; null until the first load (live or cached). */
    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    override suspend fun refresh(reason: RefreshReason) {
        val settings = settingsStore.current()
        val location = locationResolver.resolve(settings)
        // Network + disk work off the caller thread; safe from either the
        // coordinator's background dispatcher or a settings-triggered reload.
        val result = withContext(Dispatchers.IO) { repo.load(location) }
        when (result) {
            is WeatherLoad.Fresh -> {
                _snapshot.value = result.snapshot
                _state.value = ModuleState.Ok(lastUpdatedEpochMs = result.snapshot.fetchedAtEpochMs)
            }
            is WeatherLoad.Stale -> {
                _snapshot.value = result.snapshot
                _state.value = ModuleState.Ok(
                    lastUpdatedEpochMs = result.snapshot.fetchedAtEpochMs,
                    isStale = true,
                )
            }
            is WeatherLoad.Failed -> {
                // Keep any snapshot we may already be showing; never embed a body.
                _state.value = if (_snapshot.value != null) {
                    ModuleState.Ok(
                        lastUpdatedEpochMs = _snapshot.value?.fetchedAtEpochMs,
                        isStale = true,
                    )
                } else {
                    ModuleState.Error(
                        message = "Weather unavailable",
                        lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs,
                    )
                }
            }
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        WeatherContent(module = this, modifier = modifier)
    }

    @Composable
    override fun SettingsContent(modifier: Modifier) {
        WeatherSettingsSection(module = this, settingsStore = settingsStore, modifier = modifier)
    }

    companion object {
        /** Wires the real Android implementations. Used by the composition root. */
        fun create(context: Context): WeatherModule {
            val app = context.applicationContext
            return WeatherModule(
                repo = WeatherRepository(
                    api = RetrofitWeatherApi.create(),
                    cache = DataStoreWeatherCache(app),
                ),
                settingsStore = WeatherSettingsStore(app),
                locationResolver = LocationResolver(AndroidDeviceLocationSource(app)),
            )
        }
    }
}
