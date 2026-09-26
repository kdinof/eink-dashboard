package com.eink.dashboard.modules.taskforge.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.modules.taskforge.TaskForgeModule
import com.eink.dashboard.modules.taskforge.model.TaskForgePriority
import com.eink.dashboard.modules.taskforge.model.TaskForgeTask
import com.eink.dashboard.ui.ink.InkAlert
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkIcon
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkRadius
import com.eink.dashboard.ui.ink.InkRuledList
import com.eink.dashboard.ui.ink.InkSize
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkStroke
import com.eink.dashboard.ui.ink.InkTaskRow
import com.eink.dashboard.ui.ink.InkType
import kotlinx.coroutines.launch

/**
 * The TaskForge block body: `.ink-check--task` rows on a ruled list. Recurring
 * tasks show a grey repeat mark instead of a checkbox — they complete in
 * TaskForge itself, never from the reader.
 */
@Composable
fun TaskForgeContent(module: TaskForgeModule, modifier: Modifier = Modifier) {
    val board by module.board.collectAsStateWithLifecycle()
    val notice by module.notice.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val current = board

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(InkSpace.s2)) {
        notice?.let { InkAlert(title = it, icon = InkIcons.Info, outline = true) }
        if (current == null || current.isEmpty) {
            Text("No matching tasks", style = InkType.small, color = InkColors.Ink3)
            return@Column
        }
        InkRuledList(current.tasks) { task ->
            TaskRow(task = task, onComplete = { scope.launch { module.complete(task) } })
        }
        if (current.hiddenCount > 0) {
            Text("+ ${current.hiddenCount} more", style = InkType.meta, color = InkColors.Ink2)
        }
    }
}

@Composable
private fun TaskRow(task: TaskForgeTask, onComplete: () -> Unit) {
    val meta = buildList {
        task.priority.glyph.takeIf { it.isNotEmpty() }?.let(::add)
        task.due?.let { add(it.toString()) }
        (task.subsection ?: task.section)?.let(::add)
        addAll(task.tags.map { "#$it" })
        if (task.isRecurring) add("Repeats ${task.recurrence} · complete in TaskForge")
    }.joinToString(" · ")
    InkTaskRow(
        title = task.title,
        sub = meta.ifEmpty { null },
        strong = task.priority.rank >= TaskForgePriority.HIGH.rank,
        enabled = task.canComplete,
        onCheck = if (task.canComplete) onComplete else null,
        leading = if (task.isRecurring) ({ RecurringMark() }) else null,
        modifier = Modifier.padding(start = (task.indentLevel * 16).dp, top = InkSpace.s1, bottom = InkSpace.s1),
    )
}

@Composable
private fun RecurringMark() {
    Box(
        modifier = Modifier
            .size(InkSize.controlXs)
            .border(InkStroke.regular, InkColors.Ink4, RoundedCornerShape(InkRadius.xs)),
        contentAlignment = Alignment.Center,
    ) {
        InkIcon(InkIcons.Refresh, size = InkSize.iconSm, tint = InkColors.Ink3)
    }
}
