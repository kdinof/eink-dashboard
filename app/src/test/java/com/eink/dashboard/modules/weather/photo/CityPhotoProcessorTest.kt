package com.eink.dashboard.modules.weather.photo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The e-ink backdrop contract: only the panel's 16 gray levels, a pure-paper
 * left side for the clock, the photo showing on the right, and no crash on a
 * flat (single-colour) image.
 */
class CityPhotoProcessorTest {

    private val width = 200
    private val height = 40

    /** Horizontal red→dark-blue ramp repeated on every row. */
    private fun colourRamp(): IntArray = IntArray(width * height) { i ->
        val t = (i % width) / (width - 1f)
        val r = (220 * (1 - t)).toInt()
        val b = (60 + 80 * t).toInt()
        (0xFF shl 24) or (r shl 16) or (30 shl 8) or b
    }

    private fun IntArray.grays(): List<Int> = map { pixel ->
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        assertThat(pixel ushr 24).isEqualTo(0xFF)
        assertThat(g).isEqualTo(r)
        assertThat(b).isEqualTo(r)
        r
    }

    @Test fun outputUsesOnlyThePanels16GrayLevels() {
        val levels = CityPhotoProcessor.process(colourRamp(), width, height).grays().toSet()

        val allowed = (0 until CityPhotoProcessor.LEVELS).map { it * 255 / (CityPhotoProcessor.LEVELS - 1) }.toSet()
        assertThat(allowed).containsAtLeastElementsIn(levels)
    }

    @Test fun leftSideIsPurePaperAndRightSideShowsThePhoto() {
        val grays = CityPhotoProcessor.process(colourRamp(), width, height).grays()
        val paperEdge = (width * CityPhotoProcessor.FADE_START).toInt()
        val photoEdge = (width * CityPhotoProcessor.FADE_END).toInt() + 1

        for (y in 0 until height) {
            for (x in 0 until paperEdge) assertThat(grays[y * width + x]).isEqualTo(255)
        }
        val right = (0 until height).flatMap { y -> (photoEdge until width).map { grays[y * width + it] } }
        assertThat(right.average()).isLessThan(200.0)
    }

    @Test fun flatImageDoesNotDivideByZero() {
        val flat = IntArray(width * height) { 0xFF808080.toInt() }

        val grays = CityPhotoProcessor.process(flat, width, height).grays()

        assertThat(grays).hasSize(width * height)
    }

    @Test fun quantizeKeepsPaperWhiteAndInkBlackUnderEveryDitherCell() {
        for (y in 0 until 4) for (x in 0 until 4) {
            assertThat(CityPhotoProcessor.quantize(1f, x, y)).isEqualTo(255)
            assertThat(CityPhotoProcessor.quantize(0f, x, y)).isEqualTo(0)
        }
    }
}
