package com.eink.dashboard.core.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Minimal wall-clock formatting shared by the shell. Seconds are never shown —
 * the whole UI updates on the minute boundary only (see [MinuteTicker]).
 */
object TimeFormat {
    private val HH_MM = DateTimeFormatter.ofPattern("HH:mm")

    /** Epoch millis → "HH:mm" in the device time zone. */
    fun clock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        HH_MM.format(Instant.ofEpochMilli(epochMs).atZone(zone))
}
