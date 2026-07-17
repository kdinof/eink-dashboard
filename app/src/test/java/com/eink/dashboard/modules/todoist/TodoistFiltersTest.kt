package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.model.TodoistFilters
import com.eink.dashboard.modules.todoist.model.TodoistView
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The view → Todoist filter-query mapping is the one place the filter syntax lives. */
class TodoistFiltersTest {

    @Test
    fun today_isOverdueOrToday() {
        assertThat(TodoistFilters.queryFor(TodoistView.TODAY)).isEqualTo("overdue | today")
    }

    @Test
    fun upcoming_isNextSevenDays() {
        assertThat(TodoistFilters.queryFor(TodoistView.UPCOMING)).isEqualTo("7 days")
    }
}
