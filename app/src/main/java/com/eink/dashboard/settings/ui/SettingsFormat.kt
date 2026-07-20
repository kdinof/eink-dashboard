package com.eink.dashboard.settings.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SettingsFormat {
    private val LAST_UPDATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun lastUpdated(epochMs: Long?, zoneId: ZoneId = ZoneId.systemDefault()): String =
        epochMs?.let { "Last updated ${LAST_UPDATED.format(Instant.ofEpochMilli(it).atZone(zoneId))}" }
            ?: "Not updated yet"
}
