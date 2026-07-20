package com.eink.dashboard.modules.taskforge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.dashboard.ui.EinkChip
import com.eink.dashboard.modules.taskforge.TaskForgeModule
import com.eink.dashboard.modules.taskforge.TaskForgeSettings
import com.eink.dashboard.modules.taskforge.TaskForgeSettingsStore
import com.eink.dashboard.modules.taskforge.model.TaskForgeView
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TaskForgeSettingsSection(
    module: TaskForgeModule,
    settingsStore: TaskForgeSettingsStore,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(initialValue = TaskForgeSettings.DEFAULT)
    val board by module.board.collectAsStateWithLifecycle()
    val lastRead by module.lastLocalRead.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { module.connectFile(it) } }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
        Text(
            text = settings.fileName?.let { "$it · ${if (settings.canWrite) "read/write" else "read-only"}" }
                ?: "No Markdown file selected",
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.InkMuted,
        )
        lastRead?.let {
            Text("Local file updated ${LOCAL_TIME.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}", style = MaterialTheme.typography.labelMedium, color = EinkPalette.Faint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
            EinkChip(
                label = if (settings.fileUri == null) "Choose TaskForge.md" else "Change file",
                selected = false,
                onClick = { picker.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream")) },
            )
            if (settings.fileUri != null) {
                EinkChip("Disconnect", false, onClick = { scope.launch { module.disconnectFile() } })
            }
        }

        Text("View", style = MaterialTheme.typography.titleMedium)
        TaskForgeView.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
                row.forEach { view ->
                    EinkChip(view.label(), settings.view == view, onClick = {
                        scope.launch { module.updateFilter(view, settings.selectedTags, settings.limit) }
                    })
                }
            }
        }

        Text("Task limit", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
            TaskForgeSettings.ALLOWED_LIMITS.sorted().forEach { limit ->
                EinkChip(limit.toString(), settings.limit == limit, onClick = {
                    scope.launch { module.updateFilter(settings.view, settings.selectedTags, limit) }
                })
            }
        }

        val tags = board?.availableTags.orEmpty().sorted()
        if (tags.isNotEmpty()) {
            Text("Tags (match any)", style = MaterialTheme.typography.titleMedium)
            tags.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.xs)) {
                    row.forEach { tag ->
                        EinkChip("#$tag", tag in settings.selectedTags, onClick = {
                            val updated = if (tag in settings.selectedTags) settings.selectedTags - tag else settings.selectedTags + tag
                            scope.launch { module.updateFilter(settings.view, updated, settings.limit) }
                        })
                    }
                }
            }
        }
    }
}

private fun TaskForgeView.label(): String = when (this) {
    TaskForgeView.TODAY -> "Today"
    TaskForgeView.TODAY_OVERDUE -> "Today + overdue"
    TaskForgeView.NEXT_7_DAYS -> "Next 7 days"
    TaskForgeView.ALL_OPEN -> "All open"
}

private val LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
