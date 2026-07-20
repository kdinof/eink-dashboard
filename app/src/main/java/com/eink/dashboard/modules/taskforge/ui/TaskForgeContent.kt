package com.eink.dashboard.modules.taskforge.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.eink.dashboard.modules.taskforge.TaskForgeModule
import com.eink.dashboard.modules.taskforge.model.TaskForgeTask
import kotlinx.coroutines.launch

@Composable
fun TaskForgeContent(module: TaskForgeModule, modifier: Modifier = Modifier) {
    val board by module.board.collectAsStateWithLifecycle()
    val notice by module.notice.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val current = board

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EinkSpacing.xs)) {
        notice?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = EinkPalette.InkMuted)
        }
        if (current == null || current.isEmpty) {
            Text("No matching tasks", style = MaterialTheme.typography.bodyMedium, color = EinkPalette.Faint)
            return@Column
        }
        current.tasks.forEach { task ->
            TaskRow(task = task, onComplete = { scope.launch { module.complete(task) } })
        }
        if (current.hiddenCount > 0) {
            Text(
                "+ ${current.hiddenCount} more",
                style = MaterialTheme.typography.labelMedium,
                color = EinkPalette.InkMuted,
                modifier = Modifier.padding(top = EinkSpacing.xs),
            )
        }
    }
}

@Composable
private fun TaskRow(task: TaskForgeTask, onComplete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = (task.indentLevel * 12).dp),
        verticalAlignment = Alignment.Top,
    ) {
        val boxModifier = Modifier.padding(top = 2.dp).size(16.dp)
            .border(EinkSpacing.hairline, if (task.canComplete) EinkPalette.Ink else EinkPalette.Faint, RoundedCornerShape(3.dp))
        Text(
            text = if (task.isRecurring) "↻" else "",
            style = MaterialTheme.typography.labelMedium,
            color = EinkPalette.InkMuted,
            modifier = if (task.canComplete) boxModifier.clickable(onClick = onComplete) else boxModifier,
        )
        Spacer(Modifier.width(EinkSpacing.sm))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                if (task.priority.glyph.isNotEmpty()) {
                    Text(task.priority.glyph, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(EinkSpacing.xs))
                }
                Text(task.title, style = MaterialTheme.typography.bodyMedium, color = EinkPalette.Ink)
            }
            val meta = buildList {
                task.due?.let { add(it.toString()) }
                task.subsection?.let(::add) ?: task.section?.let(::add)
                addAll(task.tags.map { "#$it" })
            }.joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelMedium, color = EinkPalette.Faint)
            }
            if (task.isRecurring) {
                Text(
                    "Repeats ${task.recurrence} · complete in TaskForge",
                    style = MaterialTheme.typography.labelMedium,
                    color = EinkPalette.InkMuted,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
