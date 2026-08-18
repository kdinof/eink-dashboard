package com.eink.dashboard.modules.taskforge.model

import java.time.LocalDate

enum class TaskForgeView { TODAY, TODAY_OVERDUE, NEXT_7_DAYS, ALL_OPEN }

enum class TaskForgeStatus(val marker: Char) {
    TODO(' '), IN_PROGRESS('/'), DEFERRED('>'), ON_HOLD('!'), DONE('x'), CANCELLED('-'), OTHER('?');

    val isOpen: Boolean get() = this !in setOf(DONE, CANCELLED)

    companion object {
        fun from(marker: Char): TaskForgeStatus = when (marker) {
            ' ' -> TODO
            '/' -> IN_PROGRESS
            '>' -> DEFERRED
            '!' -> ON_HOLD
            'x', 'X' -> DONE
            '-' -> CANCELLED
            else -> OTHER
        }
    }
}

enum class TaskForgePriority(val rank: Int, val glyph: String) {
    HIGHEST(5, "🔺"), HIGH(4, "⏫"), MEDIUM(3, "🔼"), NORMAL(2, ""), LOW(1, "🔽"), LOWEST(0, "⏬");
}

/** Stable-enough optimistic locator. No TaskForge-internal identifier is required. */
data class TaskSourceRef(
    val lineHash: String,
    val occurrence: Int,
    val checkboxByteOffset: Long,
)

data class TaskForgeTask(
    val title: String,
    val status: TaskForgeStatus,
    val due: LocalDate?,
    val priority: TaskForgePriority,
    val tags: Set<String>,
    val section: String?,
    val subsection: String?,
    val recurrence: String?,
    val indentLevel: Int,
    val sourceOrder: Int,
    val source: TaskSourceRef,
) {
    val isRecurring: Boolean get() = recurrence != null

    /**
     * Completable from the dashboard: any open status whose checkbox marker we
     * know exactly (so the write can verify the byte before flipping it).
     * [TaskForgeStatus.OTHER] is excluded — its marker is a placeholder, not
     * the actual character in the file.
     */
    val canComplete: Boolean get() = status.isOpen && status != TaskForgeStatus.OTHER && !isRecurring
}

data class TaskForgeBoard(
    val view: TaskForgeView,
    val tasks: List<TaskForgeTask>,
    val totalMatches: Int,
    val availableTags: Set<String>,
) {
    val hiddenCount: Int get() = (totalMatches - tasks.size).coerceAtLeast(0)
    val isEmpty: Boolean get() = tasks.isEmpty()
}
