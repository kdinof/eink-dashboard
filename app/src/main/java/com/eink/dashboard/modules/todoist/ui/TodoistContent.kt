package com.eink.dashboard.modules.todoist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.modules.todoist.TodoistModule
import com.eink.dashboard.modules.todoist.model.TaskNode
import com.eink.dashboard.modules.todoist.model.TodoistBoard
import com.eink.dashboard.modules.todoist.model.TodoistDay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Todoist block body: day-grouped tasks with subtask indentation, each row
 * showing a completion box, priority mark, due time, content, project and labels.
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
        Text(
            text = "…",
            modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.xs),
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.Faint,
        )
        return
    }

    val today = LocalDate.now()
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm),
    ) {
        notice?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = EinkPalette.InkMuted,
                fontWeight = FontWeight.Medium,
            )
        }
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.xs),
    ) {
        Text(
            text = TodoistFormat.dayHeader(day, today),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (day.isOverdue) EinkPalette.Ink else EinkPalette.InkMuted,
        )
        day.roots.forEach { node -> TaskRows(node = node, depth = 0, board = board, onComplete = onComplete) }
    }
}

/** A task and its subtasks, indented by [depth]. */
@Composable
private fun TaskRows(node: TaskNode, depth: Int, board: TodoistBoard, onComplete: (String) -> Unit) {
    val task = node.task
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp),
        verticalAlignment = Alignment.Top,
    ) {
        CompleteBox(onClick = { onComplete(task.id) })
        Spacer(Modifier.width(EinkSpacing.sm))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                val glyph = TodoistFormat.priorityGlyph(task.priority)
                if (glyph.isNotEmpty()) {
                    Text(
                        text = glyph,
                        style = MaterialTheme.typography.bodySmall,
                        color = EinkPalette.Ink,
                        modifier = Modifier.padding(end = EinkSpacing.xs),
                    )
                }
                val time = TodoistFormat.timeLabel(task.due)
                if (time.isNotEmpty()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = EinkPalette.InkMuted,
                        modifier = Modifier.width(48.dp),
                    )
                    Spacer(Modifier.width(EinkSpacing.xs))
                }
                Text(
                    text = task.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EinkPalette.Ink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val meta = buildMeta(task.projectId?.let { board.projectNames[it] }, task.labels)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = EinkPalette.Faint,
                )
            }
        }
    }
    node.children.forEach { child ->
        TaskRows(node = child, depth = depth + 1, board = board, onComplete = onComplete)
    }
}

/** A bordered, tappable completion box. No check state — completion hides the task. */
@Composable
private fun CompleteBox(onClick: () -> Unit) {
    Text(
        text = "",
        modifier = Modifier
            .padding(top = 2.dp)
            .size(16.dp)
            .border(EinkSpacing.hairline, EinkPalette.Ink, RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
    )
}

/** "Project · label1 · label2", omitting blanks. */
private fun buildMeta(projectName: String?, labels: List<String>): String =
    (listOfNotNull(projectName?.takeIf { it.isNotBlank() }) + labels.map { "@$it" })
        .joinToString("  ·  ")
