package com.eink.dashboard.modules.weather.photo

import com.eink.dashboard.modules.weather.model.LocationPresets
import com.eink.dashboard.modules.weather.model.LocationSource
import com.eink.dashboard.modules.weather.model.ResolvedLocation
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.file.Files

/**
 * Cache policy of [CityPhotoRepository]: a place with no photo is not re-asked
 * on every 10-minute weather refresh, a network failure is not mistaken for
 * "no photo", and cache keys absorb GPS drift but not a different place.
 */
@RunWith(RobolectricTestRunner::class)
class CityPhotoRepositoryTest {

    private val dir = Files.createTempDirectory("city-photo").toFile()
    private var now = 1_000_000L

    private class CountingSource(var result: PhotoLookup) : CityPhotoSource {
        var calls = 0
        override fun lookup(location: ResolvedLocation): PhotoLookup {
            calls++
            return result
        }
    }

    private fun repo(source: CityPhotoSource) = CityPhotoRepository(source, dir) { now }

    @Test fun notFoundIsRememberedForADay() {
        val source = CountingSource(PhotoLookup.NotFound)
        val repo = repo(source)

        assertThat(repo.load(LocationPresets.TASHKENT)).isNull()
        now += 10 * 60 * 1000
        assertThat(repo.load(LocationPresets.TASHKENT)).isNull()
        assertThat(source.calls).isEqualTo(1)

        now += CityPhotoRepository.MISS_TTL_MS
        repo.load(LocationPresets.TASHKENT)
        assertThat(source.calls).isEqualTo(2)
    }

    @Test fun failureIsRetriedOnTheNextRefresh() {
        val source = CountingSource(PhotoLookup.Failed)
        val repo = repo(source)

        assertThat(repo.load(LocationPresets.TASHKENT)).isNull()
        repo.load(LocationPresets.TASHKENT)

        assertThat(source.calls).isEqualTo(2)
    }

    @Test fun cacheKeyIgnoresSmallDriftButNotAnotherPlace() {
        val here = ResolvedLocation("Current location", 41.301, 69.241, "Asia/Tashkent", LocationSource.DEVICE)
        val drifted = here.copy(latitude = 41.318, longitude = 69.229)
        val samarkand = here.copy(latitude = 39.654, longitude = 66.960)

        assertThat(CityPhotoRepository.cacheKey(drifted)).isEqualTo(CityPhotoRepository.cacheKey(here))
        assertThat(CityPhotoRepository.cacheKey(samarkand)).isNotEqualTo(CityPhotoRepository.cacheKey(here))
        assertThat(CityPhotoRepository.cacheKey(LocationPresets.TASHKENT)).isEqualTo("tashkent_413_692")
    }
}
