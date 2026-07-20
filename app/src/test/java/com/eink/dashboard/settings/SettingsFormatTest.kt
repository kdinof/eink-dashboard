package com.eink.dashboard.settings

import com.eink.dashboard.settings.ui.SettingsFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SettingsFormatTest {
    @Test fun formatsLastUpdateInRequestedLocalZone() {
        val epochMs = Instant.parse("2026-03-20T09:30:00Z").toEpochMilli()
        assertThat(SettingsFormat.lastUpdated(epochMs, ZoneId.of("Asia/Tashkent")))
            .isEqualTo("Last updated 2026-03-20 14:30")
    }

    @Test fun reportsWhenModuleHasNeverUpdated() {
        assertThat(SettingsFormat.lastUpdated(null)).isEqualTo("Not updated yet")
    }
}
