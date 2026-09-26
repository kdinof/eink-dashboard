package com.eink.dashboard.modules.todoist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.modules.todoist.TodoistModule
import com.eink.dashboard.modules.todoist.model.TaskNode
import com.eink.dashboard.modules.todoist.model.TodoistBoard
import com.eink.dashboard.modules.todoist.model.TodoistDay
import com.eink.dashboard.ui.ink.InkAlert
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkDivider
import com.eink.dashboard.ui.ink.InkEyebrow
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkLoading
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkTaskRow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Todoist block body: day-grouped `.ink-check--task` rows with subtask
 * indentation. Each row shows a completion checkbox, the task (bold for
 * priority 1–2) and a grey meta line (priority mark, due time, project, labels).
 * Pure grayscale, no animation, no self-scheduled timers — the shell owns refresh.
 * Loading/empty/error chrome is drawn by the shell from [com.eink.dashboard.dashboard.ModuleState].
 */
@Composable
fun TodoistContent(module: TodoistModule, modifier: Modifier = Modifier) {
    val board by module.board.collectAsStateWithLifecycle()
    val notice by module.notice.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val current = board
    if (current == null || current.isEmpty) {
        InkLoading(modifier = modifier)
        return
    }

    val today = LocalDate.now()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s3),
    ) {
        notice?.let { text -> InkAlert(title = text, icon = InkIcons.Info, outline = true) }
        current.days.forEach { day ->
            DayGroup(
                day = day,
                today = today,
                board = current,
                onComplete = { id -> scope.launch { module.complete(id) } },
            )
        }
    }
}

@Composable
private fun DayGroup(
    day: TodoistDay,
    today: LocalDate,
    board: TodoistBoard,
    onComplete: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InkEyebrow(
            text = TodoistFormat.dayHeader(day, today),
            color = if (day.isOverdue) InkColors.Ink else InkColors.Ink3,
            modifier = Modifier.padding(bottom = InkSpace.s1),
        )
        var first = true
        day.roots.forEach { node ->
            TaskRows(node = node, depth = 0, board = board, onComplete = onComplete, divider = !first)
            first = false
        }
    }
}

/** A task and its subtasks, indented by [depth], separated by grey notebook rules. */
@Composable
private fun TaskRows(
    node: TaskNode,
    depth: Int,
    board: TodoistBoard,
    onComplete: (String) -> Unit,
    divider: Boolean,
) {
    val task = node.task
    if (divider) InkDivider(color = InkColors.Ink3)
    val meta = listOf(
        TodoistFormat.priorityGlyph(task.priority),
        TodoistFormat.timeLabel(task.due),
        buildMeta(task.projectId?.let { board.projectNames[it] }, task.labels),
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    InkTaskRow(
        title = task.content,
        sub = meta.ifEmpty { null },
        strong = task.priority >= 3,
        onCheck = { onComplete(task.id) },
        modifier = Modifier.padding(start = (depth * 24).dp, top = InkSpace.s1, bottom = InkSpace.s1),
    )
    node.children.forEach { child ->
        TaskRows(node = child, depth = depth + 1, board = board, onComplete = onComplete, divider = true)
    }
}

/** "Project · @label1 · @label2", omitting blanks. */
private fun buildMeta(projectName: String?, labels: List<String>): String =
    (listOfNotNull(projectName?.takeIf { it.isNotBlank() }) + labels.map { "@$it" })
        .joinToString(" · ")
