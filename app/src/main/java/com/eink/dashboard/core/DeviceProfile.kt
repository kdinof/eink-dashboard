package com.eink.dashboard.core

import kotlin.math.roundToInt

/**
 * Confirmed target-device constraints, sourced from the T00 device audit
 * (`DEVICE_AUDIT.md` / `reports/T00_device_audit.md`).
 *
 * These are compile-time constants so the foundation, tests and later layout
 * work (T02) all reference a single source of truth instead of hard-coding
 * pixel numbers. Values marked "confirmed" in the audit only.
 */
object DeviceProfile {
    /** `ro.product.model` — Meebook M103 (OEM Haoqing, SoC Rockchip RK3566). */
    const val MODEL: String = "Meebook M103"

    /** Android 11 / API 30 → minSdk. */
    const val MIN_SDK: Int = 30

    /** Primary ABI; userspace is 64-bit (zygote64_32). */
    const val PRIMARY_ABI: String = "arm64-v8a"

    /** Physical resolution from `wm size` (portrait). */
    const val SCREEN_WIDTH_PX: Int = 1404
    const val SCREEN_HEIGHT_PX: Int = 1872

    /** Logical density from `wm density`. */
    const val DENSITY_DPI: Int = 240

    /** The panel is grayscale e-ink; the whole UI is chroma-free. */
    const val IS_GRAYSCALE: Boolean = true

    /** Baseline density factor (dpi / 160) used for px↔dp math. */
    val densityFactor: Float get() = DENSITY_DPI / 160f

    /** Logical width in dp (~936dp on the M103). */
    val screenWidthDp: Int get() = pxToDp(SCREEN_WIDTH_PX, DENSITY_DPI)

    /** Logical height in dp (~1248dp on the M103). */
    val screenHeightDp: Int get() = pxToDp(SCREEN_HEIGHT_PX, DENSITY_DPI)

    /**
     * Convert physical pixels to density-independent pixels for a given dpi.
     * Pure function — unit-tested without an Android runtime.
     */
    fun pxToDp(px: Int, densityDpi: Int): Int {
        require(densityDpi > 0) { "densityDpi must be positive, was $densityDpi" }
        return (px * 160f / densityDpi).roundToInt()
    }
}
