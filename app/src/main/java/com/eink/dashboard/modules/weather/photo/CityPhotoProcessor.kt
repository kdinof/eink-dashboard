package com.eink.dashboard.modules.weather.photo

import kotlin.math.roundToInt

/**
 * Turns a colour city photo into the backdrop of the board's "Now" card, once,
 * when it is cached — never per frame.
 *
 * - **Grayscale + auto-levels**: luma is stretched between its 2nd and 98th
 *   percentile, then shadows are lifted so the photo stays a soft backdrop and
 *   black text on top of it still reads.
 * - **Baked fade to paper**: the left part of the image is pure white and blends
 *   into the photo between [FADE_START] and [FADE_END] (fractions of the width).
 *   The clock sits on that white area. Baking the fade in (instead of a Compose
 *   gradient) lets it be dithered with the photo: a live gradient bands visibly
 *   on a 16-level panel.
 * - **16 gray levels, ordered dither**: output is quantised to the panel's 16
 *   levels with a 4×4 Bayer matrix, so the controller does not have to guess.
 *
 * Pure Kotlin over ARGB ints so it is unit-testable without Android bitmaps.
 */
object CityPhotoProcessor {

    /** Where the photo starts to show through the paper, as a fraction of the width. */
    const val FADE_START = 0.46f

    /** Where the photo is fully visible. */
    const val FADE_END = 0.66f

    /** Darkest output level after the shadow lift (0 = black, 1 = white). */
    private const val SHADOW_LIFT = 0.25f

    const val LEVELS = 16

    private val BAYER_4 = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5,
    )

    /** Returns opaque gray ARGB pixels, same size as [argb]. */
    fun process(argb: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0 && argb.size == width * height) { "pixel buffer does not match $width×$height" }
        val luma = FloatArray(argb.size) { luma(argb[it]) }
        val (low, high) = percentiles(luma, 0.02f, 0.98f)
        val span = (high - low).coerceAtLeast(MIN_SPAN)
        return IntArray(argb.size) { i ->
            val x = i % width
            val y = i / width
            val stretched = ((luma[i] - low) / span).coerceIn(0f, 1f)
            val photo = SHADOW_LIFT + (1f - SHADOW_LIFT) * stretched
            val reveal = smoothstep(FADE_START, FADE_END, (x + 0.5f) / width)
            gray(quantize(1f + (photo - 1f) * reveal, x, y))
        }
    }

    /** Ordered-dithered quantisation of [value] (0..1) to one of [LEVELS] gray values. */
    internal fun quantize(value: Float, x: Int, y: Int): Int {
        val threshold = (BAYER_4[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f - 0.5f
        val level = (value * (LEVELS - 1) + threshold).roundToInt().coerceIn(0, LEVELS - 1)
        return level * 255 / (LEVELS - 1)
    }

    private fun luma(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    private fun percentiles(values: FloatArray, lowFraction: Float, highFraction: Float): Pair<Float, Float> {
        val histogram = IntArray(256)
        values.forEach { histogram[(it * 255f).roundToInt().coerceIn(0, 255)]++ }
        fun at(fraction: Float): Float {
            val target = (values.size * fraction).toInt()
            var seen = 0
            for (bin in histogram.indices) {
                seen += histogram[bin]
                if (seen > target) return bin / 255f
            }
            return 1f
        }
        return at(lowFraction) to at(highFraction)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun gray(level: Int): Int = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    private const val MIN_SPAN = 1f / 255f
}
