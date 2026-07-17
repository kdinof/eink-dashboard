package com.eink.dashboard.modules.todoist.model

import java.time.LocalDate

/**
 * Turns a flat list of [TodoistTask] (as returned by a filter query) into the
 * grouped, hierarchical [TodoistDay] list the block renders. Pure and deterministic
 * — no Android, no clock beyond the `today` passed in — so it is exercised directly
 * with fixtures (subtask nesting, orphan subtasks, overdue bucket, priority order).
 *
 * Rules:
 * - **Subtask hierarchy.** A task is a child of another only if its `parentId` is
 *   present in the same result set. A subtask whose parent is *not* in the window
 *   (e.g. the parent isn't due today) is treated as a root so it never disappears.
 * - **Locally-completed tasks are dropped** from the render — optimistic completion
 *   hides them immediately; the parent still shows if it has other work.
 * - **Bucketing by root due date.** Roots dated before [today] collapse into one
 *   "Overdue" bucket; the rest group by their due date; undated roots sort last.
 * - **Ordering.** Buckets: Overdue → chronological dates → undated. Within a bucket
 *   roots sort by priority (P1 first), then due time, then Todoist `order`, then id.
 */
object TaskTreeBuilder {

    fun build(tasks: List<TodoistTask>, today: LocalDate): List<TodoistDay> {
        val visible = tasks.filterNot { it.locallyCompleted }
        val presentIds = visible.mapTo(HashSet()) { it.id }

        val childrenByParent: Map<String, List<TodoistTask>> = visible
            .filter { it.parentId != null && it.parentId in presentIds }
            .groupBy { it.parentId!! }

        // A root is any visible task whose parent is absent from the window.
        val roots = visible.filter { it.parentId == null || it.parentId !in presentIds }

        fun nodeFor(task: TodoistTask): TaskNode {
            val kids = childrenByParent[task.id].orEmpty()
                .sortedWith(taskOrder)
                .map(::nodeFor)
            return TaskNode(task, kids)
        }

        val overdue = mutableListOf<TodoistTask>()
        val dated = LinkedHashMap<LocalDate, MutableList<TodoistTask>>()
        val undated = mutableListOf<TodoistTask>()

        for (root in roots) {
            val date = root.due?.date
            when {
                date == null -> undated += root
                date.isBefore(today) -> overdue += root
                else -> dated.getOrPut(date) { mutableListOf() } += root
            }
        }

        val days = mutableListOf<TodoistDay>()
        if (overdue.isNotEmpty()) {
            days += TodoistDay(
                date = null,
                isOverdue = true,
                roots = overdue.sortedWith(taskOrder).map(::nodeFor),
            )
        }
        dated.keys.sorted().forEach { date ->
            days += TodoistDay(
                date = date,
                isOverdue = false,
                roots = dated.getValue(date).sortedWith(taskOrder).map(::nodeFor),
            )
        }
        if (undated.isNotEmpty()) {
            days += TodoistDay(
                date = null,
                isOverdue = false,
                roots = undated.sortedWith(taskOrder).map(::nodeFor),
            )
        }
        return days
    }

    /** Priority (P1 first) → due time → Todoist order → id, for a stable sort. */
    private val taskOrder: Comparator<TodoistTask> =
        compareByDescending<TodoistTask> { it.priority }
            .thenBy { it.due?.at?.toString() ?: "￿" }
            .thenBy { it.order }
            .thenBy { it.id }
}
