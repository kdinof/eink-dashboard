package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.data.DueDto
import com.eink.dashboard.modules.todoist.data.TaskDto
import com.eink.dashboard.modules.todoist.data.toDomain
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The tolerant DTO → domain due-date parsing (date-only, floating time, offset time). */
class TodoistDtoTest {

    @Test
    fun dateOnly_parsesToDate_withNoTime() {
        val due = DueDto(date = "2026-07-17", isRecurring = false, string = "Jul 17").toDomain()
        assertThat(due.date.toString()).isEqualTo("2026-07-17")
        assertThat(due.at).isNull()
    }

    @Test
    fun floatingDatetime_parsesToLocalTime() {
        val due = DueDto(datetime = "2026-07-17T13:30:00", string = "").toDomain()
        assertThat(due.date.toString()).isEqualTo("2026-07-17")
        assertThat(due.at?.toLocalTime().toString()).isEqualTo("13:30")
    }

    @Test
    fun offsetDatetime_isConvertedToLocalDateTime() {
        val due = DueDto(datetime = "2026-07-17T13:30:00+05:00", string = "").toDomain()
        assertThat(due.date.toString()).isEqualTo("2026-07-17")
        assertThat(due.at?.toLocalTime().toString()).isEqualTo("13:30")
    }

    @Test
    fun task_mapsChildOrderAndLabels() {
        val task = TaskDto(
            id = "7",
            content = "Do it",
            projectId = "P",
            labels = listOf("home", "urgent"),
            priority = 3,
            childOrder = 5,
        ).toDomain()
        assertThat(task.order).isEqualTo(5)
        assertThat(task.labels).containsExactly("home", "urgent")
        assertThat(task.priorityLabel).isEqualTo("P2")
    }
}
