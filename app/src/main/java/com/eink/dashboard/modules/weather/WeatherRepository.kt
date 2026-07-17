package com.eink.dashboard.modules.weather

import com.eink.dashboard.modules.weather.data.WeatherApi
import com.eink.dashboard.modules.weather.data.WeatherCache
import com.eink.dashboard.modules.weather.data.WeatherError
import com.eink.dashboard.modules.weather.model.ResolvedLocation
import com.eink.dashboard.modules.weather.model.WeatherSnapshot

/**
 * Orchestrates a weather load: fetch the live forecast, persist it on success, and
 * fall back to the last cached snapshot when the network fails (T05: "cache the last
 * successful response"). The module above maps the [WeatherLoad] outcome onto the
 * frozen [com.eink.dashboard.dashboard.ModuleState] the shell renders.
 *
 * The repository is pure of Android and of Retrofit — it depends only on the
 * [WeatherApi] seam and the [WeatherCache], so it is unit-tested with an in-memory
 * fake and cache. No response body or coordinate is ever logged.
 */
class WeatherRepository(
    private val api: WeatherApi,
    private val cache: WeatherCache,
) {
    /**
     * Fetch [location]. On success the snapshot is cached and returned [Fresh]. On a
     * transport/payload failure the last cached snapshot (if any) is returned [Stale]
     * with the error; if nothing is cached the load is [Failed].
     */
    suspend fun load(location: ResolvedLocation): WeatherLoad {
        return try {
            val snapshot = api.fetch(location)
            cache.save(snapshot)
            WeatherLoad.Fresh(snapshot)
        } catch (e: WeatherError) {
            when (val cached = cache.load()) {
                null -> WeatherLoad.Failed(e)
                else -> WeatherLoad.Stale(cached, e)
            }
        }
    }

    /** The last cached snapshot, or null — used to seed the block on a cold start. */
    suspend fun cached(): WeatherSnapshot? = cache.load()
}

/** Result of a [WeatherRepository.load]. */
sealed interface WeatherLoad {
    /** A live forecast was fetched (and cached). */
    data class Fresh(val snapshot: WeatherSnapshot) : WeatherLoad

    /** The live fetch failed but a previously cached snapshot is shown instead. */
    data class Stale(val snapshot: WeatherSnapshot, val error: WeatherError) : WeatherLoad

    /** The live fetch failed and there is nothing cached to show. */
    data class Failed(val error: WeatherError) : WeatherLoad
}
