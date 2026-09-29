package com.eink.dashboard.modules.weather.photo

import com.eink.dashboard.modules.weather.model.LocationPresets
import com.eink.dashboard.modules.weather.model.LocationSource
import com.eink.dashboard.modules.weather.model.ResolvedLocation
import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Wikipedia lookup against MockWebServer — **no real network**: the named
 * article wins only when it is geographically the weather place, otherwise the
 * nearest landmark with a page image; failures are reported as retryable.
 */
class WikipediaCityPhotoSourceTest {

    private lateinit var server: MockWebServer
    private val photoBytes = byteArrayOf(1, 2, 3, 4)

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun source() = WikipediaCityPhotoSource(
        client = OkHttpClient.Builder().build(),
        apiUrl = server.url("/w/api.php"),
    )

    private fun thumb(name: String) = server.url("/thumb/$name.jpg").toString()

    /** Routes API calls by their distinguishing query parameter. */
    private fun serve(
        title: String? = null,
        geosearch: String? = null,
        nearbyImages: String? = null,
        apiCode: Int = 200,
    ) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (url.encodedPath.startsWith("/thumb/")) return MockResponse().setBody(Buffer().write(photoBytes))
                if (apiCode != 200) return MockResponse().setResponseCode(apiCode)
                val body = when {
                    url.queryParameter("list") == "geosearch" -> geosearch
                    url.queryParameter("prop") == "pageimages|coordinates" -> title
                    else -> nearbyImages
                } ?: """{"batchcomplete":true,"query":{"pages":[]}}"""
                return MockResponse().setBody(body)
            }
        }
    }

    private fun titlePage(lat: Double, lon: Double, withThumb: Boolean = true) = """
        {"query":{"pages":[{"title":"Tashkent",
          ${if (withThumb) """"thumbnail":{"source":"${thumb("skyline")}"},""" else ""}
          "coordinates":[{"lat":$lat,"lon":$lon}]}]}}
    """.trimIndent()

    private val nearbyTitles = """
        {"query":{"geosearch":[{"title":"Metro station"},{"title":"Old madrassah"},{"title":"Palace"}]}}
    """.trimIndent()

    // Unordered on purpose: the nearest page with an image must still win.
    private fun nearbyThumbs() = """
        {"query":{"pages":[
          {"title":"Palace","thumbnail":{"source":"${thumb("palace")}"}},
          {"title":"Metro station"},
          {"title":"Old madrassah","thumbnail":{"source":"${thumb("madrassah")}"}}]}}
    """.trimIndent()

    private fun lastThumbPath(): String {
        var last = ""
        while (true) {
            val request = server.takeRequest(0, TimeUnit.SECONDS) ?: return last
            if (request.path!!.startsWith("/thumb/")) last = request.path!!
        }
    }

    @Test fun namedArticleAtTheWeatherPointIsUsed() {
        serve(title = titlePage(41.3111, 69.2797))

        val result = source().lookup(LocationPresets.TASHKENT)

        assertThat((result as PhotoLookup.Found).bytes).isEqualTo(photoBytes)
        assertThat(lastThumbPath()).isEqualTo("/thumb/skyline.jpg")
    }

    @Test fun sameNamedPlaceFarAwayFallsBackToNearbyLandmark() {
        // A "Tashkent" article whose coordinates are in another country.
        serve(title = titlePage(55.75, 37.62), geosearch = nearbyTitles, nearbyImages = nearbyThumbs())

        val result = source().lookup(LocationPresets.TASHKENT)

        assertThat(result).isInstanceOf(PhotoLookup.Found::class.java)
        assertThat(lastThumbPath()).isEqualTo("/thumb/madrassah.jpg")
    }

    @Test fun labelThatIsNotAPlaceUsesCoordinates() {
        val home = ResolvedLocation("Current location", 41.3, 69.24, "Asia/Tashkent", LocationSource.DEVICE)
        serve(geosearch = nearbyTitles, nearbyImages = nearbyThumbs())

        val result = source().lookup(home)

        assertThat(result).isInstanceOf(PhotoLookup.Found::class.java)
        assertThat(lastThumbPath()).isEqualTo("/thumb/madrassah.jpg")
    }

    @Test fun nothingNearbyIsNotFound() {
        serve(title = titlePage(41.3111, 69.2797, withThumb = false), geosearch = """{"query":{"geosearch":[]}}""")

        assertThat(source().lookup(LocationPresets.TASHKENT)).isEqualTo(PhotoLookup.NotFound)
    }

    @Test fun serverErrorIsRetryableFailure() {
        serve(apiCode = 503)

        assertThat(source().lookup(LocationPresets.TASHKENT)).isEqualTo(PhotoLookup.Failed)
    }

    @Test fun requestsIdentifyTheApp() {
        serve(title = titlePage(41.3111, 69.2797))

        source().lookup(LocationPresets.TASHKENT)

        assertThat(server.takeRequest().getHeader("User-Agent")).isEqualTo(WikipediaCityPhotoSource.USER_AGENT)
    }

    @Test fun distanceIsHaversineKilometres() {
        // Tashkent → Samarkand is ~270 km.
        assertThat(WikipediaCityPhotoSource.distanceKm(41.2995, 69.2401, 39.6542, 66.9597)).isWithin(15.0).of(270.0)
    }
}
