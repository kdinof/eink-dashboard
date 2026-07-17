package com.eink.dashboard.modules.clock

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The rendered clock: [time] like "08:05" (no seconds) and [date] like "Fri, 17 Jul". */
data class ClockFace(val time: String, val date: String)

/**
 * Pure epoch-millis → [ClockFace] formatting, split out of [ClockModule] so time and
 * date rendering (including the day/date rollover) is unit-tested with a fixed zone
 * and no Android runtime. Seconds are never shown — the whole UI updates on the
 * minute boundary only.
 */
object ClockFormat {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val DATE = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

    fun face(epochMs: Long, zone: ZoneId): ClockFace {
        val zoned = Instant.ofEpochMilli(epochMs).atZone(zone)
        return ClockFace(time = TIME.format(zoned), date = DATE.format(zoned))
    }
}
