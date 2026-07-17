package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.model.TaskTreeBuilder
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** Pure grouping/hierarchy logic: buckets, subtask nesting, ordering, optimistic drop. */
class TaskTreeBuilderTest {

    private val today = LocalDate.of(2026, 7, 17)

    @Test
    fun subtask_nestsUnderParentWhenBothPresent() {
        val parent = Fixtures.task("p", date = today)
        val child = Fixtures.task("c", parentId = "p", date = today)
        val days = TaskTreeBuilder.build(listOf(parent, child), today)

        val root = days.single().roots.single()
        assertThat(root.task.id).isEqualTo("p")
        assertThat(root.children.map { it.task.id }).containsExactly("c")
    }

    @Test
    fun orphanSubtask_isPromotedToRoot() {
        // The child's parent isn't in the window (e.g. not due today) → show it anyway.
        val child = Fixtures.task("c", parentId = "absent", date = today)
        val days = TaskTreeBuilder.build(listOf(child), today)
        assertThat(days.single().roots.map { it.task.id }).containsExactly("c")
    }

    @Test
    fun overdueTasks_collapseIntoOneOverdueBucketFirst() {
        val overdue = Fixtures.task("o", date = today.minusDays(2))
        val todayTask = Fixtures.task("t", date = today)
        val days = TaskTreeBuilder.build(listOf(todayTask, overdue), today)

        assertThat(days.first().isOverdue).isTrue()
        assertThat(days.first().roots.single().task.id).isEqualTo("o")
        assertThat(days[1].date).isEqualTo(today)
    }

    @Test
    fun withinBucket_sortsByPriorityThenTime() {
        val p4 = Fixtures.task("low", priority = 1, at = today.atTime(LocalTime.of(8, 0)))
        val p1 = Fixtures.task("high", priority = 4, at = today.atTime(LocalTime.of(18, 0)))
        val p1Early = Fixtures.task("high2", priority = 4, at = today.atTime(LocalTime.of(9, 0)))
        val days = TaskTreeBuilder.build(listOf(p4, p1, p1Early), today)

        val ids = days.single().roots.map { it.task.id }
        assertThat(ids).containsExactly("high2", "high", "low").inOrder()
    }

    @Test
    fun undatedTasks_sortLast() {
        val dated = Fixtures.task("d", date = today)
        val undated = Fixtures.task("u")
        val days = TaskTreeBuilder.build(listOf(undated, dated), today)

        assertThat(days.first().date).isEqualTo(today)
        assertThat(days.last().date).isNull()
        assertThat(days.last().roots.single().task.id).isEqualTo("u")
    }

    @Test
    fun locallyCompletedTask_isDropped() {
        val done = Fixtures.task("x", date = today).copy(locallyCompleted = true)
        val days = TaskTreeBuilder.build(listOf(done), today)
        assertThat(days).isEmpty()
    }
}
