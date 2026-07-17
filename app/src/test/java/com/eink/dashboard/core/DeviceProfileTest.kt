package com.eink.dashboard.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Unit tests for [DeviceProfile]. These lock the confirmed T00 device constants
 * and the px↔dp conversion so a later accidental change is caught by CI.
 */
class DeviceProfileTest {

    @Test
    fun confirmedBuildConstraintsMatchAudit() {
        assertThat(DeviceProfile.MIN_SDK).isEqualTo(30)
        assertThat(DeviceProfile.PRIMARY_ABI).isEqualTo("arm64-v8a")
        assertThat(DeviceProfile.SCREEN_WIDTH_PX).isEqualTo(1404)
        assertThat(DeviceProfile.SCREEN_HEIGHT_PX).isEqualTo(1872)
        assertThat(DeviceProfile.DENSITY_DPI).isEqualTo(240)
        assertThat(DeviceProfile.IS_GRAYSCALE).isTrue()
    }

    @Test
    fun pxToDpUsesStandardBaselineDensity() {
        // At 160 dpi, 1 px == 1 dp by definition.
        assertThat(DeviceProfile.pxToDp(160, 160)).isEqualTo(160)
    }

    @Test
    fun screenDimensionsConvertToExpectedDp() {
        // 1404 px @ 240 dpi -> 1404 * 160 / 240 = 936 dp
        assertThat(DeviceProfile.screenWidthDp).isEqualTo(936)
        // 1872 px @ 240 dpi -> 1872 * 160 / 240 = 1248 dp
        assertThat(DeviceProfile.screenHeightDp).isEqualTo(1248)
    }

    @Test
    fun densityFactorMatchesDpiOverBaseline() {
        assertThat(DeviceProfile.densityFactor).isEqualTo(1.5f)
    }

    @Test
    fun pxToDpRejectsNonPositiveDensity() {
        assertFailsWith<IllegalArgumentException> { DeviceProfile.pxToDp(100, 0) }
    }
}
