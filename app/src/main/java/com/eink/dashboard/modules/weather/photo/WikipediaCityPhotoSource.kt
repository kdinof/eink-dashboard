package com.eink.dashboard.modules.weather.photo

import com.eink.dashboard.modules.weather.model.ResolvedLocation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Outcome of looking up a photo for a place. */
sealed interface PhotoLookup {
    class Found(val bytes: ByteArray) : PhotoLookup

    /** The source answered, but has no usable photo for this place. */
    data object NotFound : PhotoLookup

    /** Network or server trouble — worth retrying later, not a verdict. */
    data object Failed : PhotoLookup
}

/** Finds a photo of the weather location. Blocking; call it off the main thread. */
fun interface CityPhotoSource {
    fun lookup(location: ResolvedLocation): PhotoLookup
}

/**
 * Photos from Wikipedia's page images (keyless MediaWiki API).
 *
 * 1. The article named like the location label ("Tashkent"), accepted only if the
 *    article's own coordinates lie within [MAX_TITLE_DISTANCE_KM] of the weather
 *    point. That rejects labels that are not places ("Home", "Custom", "Current
 *    location") and same-named places elsewhere.
 * 2. Otherwise the nearest geotagged article within [GEOSEARCH_RADIUS_M] that has
 *    a page image — a local landmark rather than the city skyline.
 *
 * Only the label and coordinates the weather request already uses are sent.
 */
class WikipediaCityPhotoSource(
    private val client: OkHttpClient,
    private val apiUrl: HttpUrl = API_URL.toHttpUrl(),
) : CityPhotoSource {

    override fun lookup(location: ResolvedLocation): PhotoLookup = try {
        val url = titleImage(location) ?: nearbyImage(location)
        if (url == null) PhotoLookup.NotFound else download(url)
    } catch (_: IOException) {
        PhotoLookup.Failed
    } catch (_: IllegalArgumentException) {
        // Malformed JSON or URL from the API — treat as a transient failure.
        PhotoLookup.Failed
    }

    private fun titleImage(location: ResolvedLocation): String? {
        val title = location.label.trim().ifEmpty { return null }
        val pages = query(
            "prop" to "pageimages|coordinates",
            "piprop" to "thumbnail",
            "pithumbsize" to THUMB_WIDTH.toString(),
            "redirects" to "1",
            "titles" to title,
        ).pages()
        val page = pages.firstOrNull() ?: return null
        val coordinates = page["coordinates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val lat = coordinates["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = coordinates["lon"]?.jsonPrimitive?.doubleOrNull ?: return null
        if (distanceKm(location.latitude, location.longitude, lat, lon) > MAX_TITLE_DISTANCE_KM) return null
        return page.thumbnail()
    }

    private fun nearbyImage(location: ResolvedLocation): String? {
        val nearby = query(
            "list" to "geosearch",
            "gscoord" to "${location.latitude}|${location.longitude}",
            "gsradius" to GEOSEARCH_RADIUS_M.toString(),
            "gslimit" to GEOSEARCH_LIMIT.toString(),
        )["query"]?.jsonObject?.get("geosearch")?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["title"]?.jsonPrimitive?.content }
        if (nearby.isEmpty()) return null
        val thumbs = query(
            "prop" to "pageimages",
            "piprop" to "thumbnail",
            "pithumbsize" to THUMB_WIDTH.toString(),
            "titles" to nearby.joinToString("|"),
        ).pages().mapNotNull { page ->
            val title = page["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            page.thumbnail()?.let { title to it }
        }.toMap()
        // Geosearch order is nearest-first; the pageimages answer is unordered.
        return nearby.firstNotNullOfOrNull { thumbs[it] }
    }

    private fun query(vararg params: Pair<String, String>): JsonObject {
        val url = apiUrl.newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        return get(url).let { json.parseToJsonElement(it.decodeToString()).jsonObject }
    }

    private fun download(url: String): PhotoLookup {
        val bytes = get(url.toHttpUrl())
        return if (bytes.isEmpty()) PhotoLookup.NotFound else PhotoLookup.Found(bytes)
    }

    private fun get(url: HttpUrl): ByteArray {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty body")
            if (body.contentLength() > MAX_BYTES) throw IOException("response too large")
            val bytes = body.bytes()
            if (bytes.size > MAX_BYTES) throw IOException("response too large")
            return bytes
        }
    }

    private fun JsonObject.pages(): List<JsonObject> =
        this["query"]?.jsonObject?.get("pages")?.jsonArray.orEmpty().map(JsonElement::jsonObject)

    private fun JsonObject.thumbnail(): String? =
        this["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content

    companion object {
        const val API_URL = "https://en.wikipedia.org/w/api.php"

        /** Wikimedia asks API clients to identify themselves. */
        const val USER_AGENT = "EinkDashboard/1.0 (https://github.com/kdinof/eink-dashboard)"

        const val THUMB_WIDTH = 1200
        const val MAX_TITLE_DISTANCE_KM = 50.0
        const val GEOSEARCH_RADIUS_M = 10_000
        const val GEOSEARCH_LIMIT = 10
        private const val MAX_BYTES = 8L * 1024 * 1024

        private val json = Json { ignoreUnknownKeys = true }

        fun create(): WikipediaCityPhotoSource = WikipediaCityPhotoSource(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build(),
        )

        /** Great-circle distance (haversine). */
        internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
        }

        private const val EARTH_RADIUS_KM = 6371.0
    }
}
