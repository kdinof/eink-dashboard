package com.eink.dashboard.modules.weather.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.eink.dashboard.modules.weather.model.ResolvedLocation
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** Backdrop photo for the weather location. Blocking; call it off the main thread. */
fun interface CityPhotos {
    /** The processed photo, or `null` when there is none (yet). Never throws. */
    fun load(location: ResolvedLocation): ImageBitmap?
}

/**
 * Disk-cached city photos: fetched from [source] at most once per place per
 * [PHOTO_TTL_MS], processed by [CityPhotoProcessor] and stored as a small gray
 * PNG. A place without a photo is remembered for [MISS_TTL_MS] so the 10-minute
 * weather refresh does not hit the network every time. On a failed refresh the
 * previous (stale) photo keeps showing.
 *
 * The same decoded image is handed out while its file is unchanged, so the
 * board does not recompose the card on every weather refresh.
 */
class CityPhotoRepository(
    private val source: CityPhotoSource,
    private val cacheDir: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : CityPhotos {

    private var memo: Memo? = null

    override fun load(location: ResolvedLocation): ImageBitmap? = try {
        loadOrFetch(location)
    } catch (_: Exception) {
        // A broken photo must never take the weather block down with it.
        null
    }

    private fun loadOrFetch(location: ResolvedLocation): ImageBitmap? {
        val key = cacheKey(location)
        val photo = File(cacheDir, "$key.png")
        val miss = File(cacheDir, "$key.none")
        val now = clock()
        if (photo.exists() && now - photo.lastModified() < PHOTO_TTL_MS) return decode(key, photo)
        if (miss.exists() && now - miss.lastModified() < MISS_TTL_MS) return decodeIfExists(key, photo)

        when (val found = source.lookup(location)) {
            is PhotoLookup.Found -> {
                val bitmap = render(found.bytes) ?: return decodeIfExists(key, photo)
                cacheDir.mkdirs()
                val tmp = File(cacheDir, "$key.tmp")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                if (!tmp.renameTo(photo)) {
                    tmp.delete()
                    return decodeIfExists(key, photo)
                }
                photo.setLastModified(now)
                miss.delete()
                return decode(key, photo)
            }
            PhotoLookup.NotFound -> {
                cacheDir.mkdirs()
                miss.writeText(location.label)
                miss.setLastModified(now)
                return decodeIfExists(key, photo)
            }
            PhotoLookup.Failed -> return decodeIfExists(key, photo)
        }
    }

    private fun decodeIfExists(key: String, file: File): ImageBitmap? = if (file.exists()) decode(key, file) else null

    private fun decode(key: String, file: File): ImageBitmap? {
        val stamp = file.lastModified()
        memo?.takeIf { it.key == key && it.stamp == stamp }?.let { return it.image }
        val image = BitmapFactory.decodeFile(file.path)?.asImageBitmap() ?: return null
        memo = Memo(key, stamp, image)
        return image
    }

    companion object {
        const val PHOTO_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val MISS_TTL_MS = 24L * 60 * 60 * 1000

        /**
         * Output size. Narrower than the "Now" card (≈1.7:1), so `ContentScale.Crop`
         * fits it to the card's width and only trims top/bottom — the baked fade
         * then lands exactly where [CityPhotoProcessor] put it.
         */
        const val WIDTH = 1200
        const val HEIGHT = 800

        fun create(context: Context): CityPhotoRepository = CityPhotoRepository(
            source = WikipediaCityPhotoSource.create(),
            cacheDir = File(context.applicationContext.filesDir, "city-photo"),
        )

        /** One file per place label and ~10 km cell, so small GPS drift reuses the cache. */
        internal fun cacheKey(location: ResolvedLocation): String {
            val label = location.label.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
            val lat = (location.latitude * 10).roundToInt()
            val lon = (location.longitude * 10).roundToInt()
            return "${label.ifEmpty { "place" }.take(40)}_${lat}_$lon"
        }

        /** Decodes, centre-crops to [WIDTH]×[HEIGHT] and runs [CityPhotoProcessor]. */
        private fun render(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= WIDTH && bounds.outHeight / (sample * 2) >= HEIGHT) sample *= 2
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null

            val targetRatio = WIDTH.toFloat() / HEIGHT
            val cropW: Int
            val cropH: Int
            if (source.width.toFloat() / source.height > targetRatio) {
                cropH = source.height
                cropW = (cropH * targetRatio).roundToInt()
            } else {
                cropW = source.width
                cropH = (cropW / targetRatio).roundToInt()
            }
            val cropped = Bitmap.createBitmap(source, (source.width - cropW) / 2, (source.height - cropH) / 2, cropW, cropH)
            val scaled = Bitmap.createScaledBitmap(cropped, WIDTH, HEIGHT, true)
            val pixels = IntArray(WIDTH * HEIGHT)
            scaled.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            // createBitmap/createScaledBitmap may hand back their input; recycling twice is a no-op.
            listOf(scaled, cropped, source).forEach(Bitmap::recycle)
            val gray = CityPhotoProcessor.process(pixels, WIDTH, HEIGHT)
            return Bitmap.createBitmap(gray, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        }
    }

    private class Memo(val key: String, val stamp: Long, val image: ImageBitmap)
}
