package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.model.CalendarRangeMode
import com.eink.dashboard.modules.calendar.model.DayRange
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Timezone-safe range math anchored on the device's local date. All assertions use
 * `Asia/Tashkent` (UTC+5) so they are independent of the machine running the test
 * and pin the required "переход даты" (midnight rollover) behaviour.
 */
class DayRangeTest {

    @Test
    fun today_spansOneLocalDay() {
        val now = localMs(TASHKENT, 2026, 7, 17, 9, 0)
        val range = DayRange.of(CalendarRangeMode.TODAY, now, TASHKENT)
        assertThat(range.startDate).isEqualTo(LocalDate.of(2026, 7, 17))
        assertThat(range.endExclusive).isEqualTo(LocalDate.of(2026, 7, 18))
    }

    @Test
    fun todayTomorrow_spansTwoDays() {
        val now = localMs(TASHKENT, 2026, 7, 17, 9, 0)
        val range = DayRange.of(CalendarRangeMode.TODAY_TOMORROW, now, TASHKENT)
        assertThat(range.startDate).isEqualTo(LocalDate.of(2026, 7, 17))
        assertThat(range.endExclusive).isEqualTo(LocalDate.of(2026, 7, 19))
    }

    @Test
    fun week_spansSevenDays() {
        val now = localMs(TASHKENT, 2026, 7, 17, 9, 0)
        val range = DayRange.of(CalendarRangeMode.WEEK, now, TASHKENT)
        assertThat(range.startDate).isEqualTo(LocalDate.of(2026, 7, 17))
        assertThat(range.endExclusive).isEqualTo(LocalDate.of(2026, 7, 24))
    }

    @Test
    fun dateTransition_justBeforeAndAfterLocalMidnight() {
        // 23:59 on the 17th is still "today = 17th"…
        val beforeMidnight = localMs(TASHKENT, 2026, 7, 17, 23, 59)
        assertThat(DayRange.of(CalendarRangeMode.TODAY, beforeMidnight, TASHKENT).startDate)
            .isEqualTo(LocalDate.of(2026, 7, 17))
        // …and 00:01 on the 18th has rolled over to the 18th.
        val afterMidnight = localMs(TASHKENT, 2026, 7, 18, 0, 1)
        assertThat(DayRange.of(CalendarRangeMode.TODAY, afterMidnight, TASHKENT).startDate)
            .isEqualTo(LocalDate.of(2026, 7, 18))
    }

    @Test
    fun contains_isHalfOpen() {
        val range = DayRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 19))
        assertThat(range.contains(LocalDate.of(2026, 7, 17))).isTrue()
        assertThat(range.contains(LocalDate.of(2026, 7, 18))).isTrue()
        assertThat(range.contains(LocalDate.of(2026, 7, 19))).isFalse() // end exclusive
        assertThat(range.contains(LocalDate.of(2026, 7, 16))).isFalse()
    }

    @Test
    fun queryBounds_areLocalMidnights() {
        val range = DayRange(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 18))
        assertThat(range.queryStartMs(TASHKENT)).isEqualTo(localMs(TASHKENT, 2026, 7, 17, 0, 0))
        assertThat(range.queryEndMs(TASHKENT)).isEqualTo(localMs(TASHKENT, 2026, 7, 18, 0, 0))
    }
}
