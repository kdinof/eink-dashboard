package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.model.CalendarInfo
import com.eink.dashboard.modules.calendar.model.CalendarMarkers
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Grayscale calendar markers must let calendars be told apart without colour
 * (§8.1): distinct shape/shade combos, deterministic, and stable to input order.
 */
class CalendarMarkerTest {

    private fun cal(id: Long, name: String) = CalendarInfo(id, name, "acct@example.com")

    @Test
    fun twoCalendars_getDistinctMarkers() {
        val markers = CalendarMarkers.assign(listOf(cal(1, "Work"), cal(2, "Personal")))
        val a = markers.getValue(1)
        val b = markers.getValue(2)
        assertThat(a).isNotEqualTo(b)
        // Shape is the primary distinguisher; the two must differ in shape here.
        assertThat(a.shape).isNotEqualTo(b.shape)
    }

    @Test
    fun manyCalendars_allMarkersUniqueUntilCombosExhausted() {
        val calendars = (1L..12L).map { cal(it, "Cal$it") }
        val markers = CalendarMarkers.assign(calendars)
        // 5 shapes × 3 shades = 15 combos ⇒ 12 calendars are all distinct.
        assertThat(markers.values.toSet()).hasSize(12)
    }

    @Test
    fun assignment_isDeterministic_andOrderIndependent() {
        val a = CalendarMarkers.assign(listOf(cal(1, "Work"), cal(2, "Personal")))
        val b = CalendarMarkers.assign(listOf(cal(2, "Personal"), cal(1, "Work")))
        // Canonical order (by name) ⇒ identical assignment regardless of input order.
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun shade_isChromaFree_inZeroToOne() {
        val markers = CalendarMarkers.assign((1L..6L).map { cal(it, "Cal$it") })
        markers.values.forEach { assertThat(it.shade).isIn(com.google.common.collect.Range.closed(0.0f, 1.0f)) }
    }
}
