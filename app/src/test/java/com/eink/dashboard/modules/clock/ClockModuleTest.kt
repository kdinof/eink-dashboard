package com.eink.dashboard.modules.clock

import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.core.time.TimeSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The clock's pure formatting and its state machine: minute granularity (no
 * seconds), date rollover across midnight, and the [RefreshPolicy.EveryMinute]
 * contract that binds it to the shell's shared ticker.
 */
class ClockModuleTest {

    private val tashkent = ZoneId.of("Asia/Tashkent")

    private fun epochAt(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, zone: ZoneId): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun face_showsHoursAndMinutes_noSeconds() {
        val ms = epochAt(2026, 7, 17, 8, 5, 42, tashkent)
        val face = ClockFormat.face(ms, tashkent)
        assertThat(face.time).isEqualTo("08:05") // 42 seconds are dropped
        assertThat(face.date).isEqualTo("Fri, 17 Jul")
    }

    @Test
    fun face_rendersInTheGivenZone() {
        // 20:30 UTC is 01:30 next day in Tashkent (UTC+5).
        val ms = Instant.parse("2026-07-17T20:30:00Z").toEpochMilli()
        val utc = ClockFormat.face(ms, ZoneOffset.UTC)
        val tas = ClockFormat.face(ms, tashkent)
        assertThat(utc.time).isEqualTo("20:30")
        assertThat(tas.time).isEqualTo("01:30")
        assertThat(tas.date).isEqualTo("Sat, 18 Jul") // rolled to the next day
    }

    @Test
    fun date_rollsOverAtMidnight() {
        val before = ClockFormat.face(epochAt(2026, 7, 17, 23, 59, 0, tashkent), tashkent)
        val after = ClockFormat.face(epochAt(2026, 7, 18, 0, 0, 0, tashkent), tashkent)
        assertThat(before.date).isEqualTo("Fri, 17 Jul")
        assertThat(after.date).isEqualTo("Sat, 18 Jul")
        assertThat(after.time).isEqualTo("00:00")
    }

    @Test
    fun contract_isEveryMinute_andNotDemo() {
        val m = ClockModule()
        assertThat(m.id).isEqualTo("clock")
        assertThat(m.refreshPolicy).isEqualTo(RefreshPolicy.EveryMinute)
        assertThat(m.isDemo).isFalse()
    }

    @Test
    fun refresh_setsOkState_andUpdatesFace(): Unit = runBlocking {
        val fixed = epochAt(2026, 7, 17, 8, 5, 0, tashkent)
        val m = ClockModule(timeSource = TimeSource { fixed }, zoneProvider = { tashkent })
        m.refresh(RefreshReason.MINUTE_TICK)
        assertThat(m.state.value).isInstanceOf(ModuleState.Ok::class.java)
        assertThat(m.state.value.lastUpdatedEpochMs).isEqualTo(fixed)
        assertThat(m.face.value.time).isEqualTo("08:05")
    }
}
