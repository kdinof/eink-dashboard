package com.eink.dashboard.modules.taskforge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.modules.taskforge.TaskForgeModule
import com.eink.dashboard.modules.taskforge.TaskForgeSettings
import com.eink.dashboard.modules.taskforge.TaskForgeSettingsStore
import com.eink.dashboard.modules.taskforge.model.TaskForgeView
import com.eink.dashboard.ui.ink.InkButton
import com.eink.dashboard.ui.ink.InkButtonVariant
import com.eink.dashboard.ui.ink.InkChip
import com.eink.dashboard.ui.ink.InkChips
import com.eink.dashboard.ui.ink.InkHint
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkLabel
import com.eink.dashboard.ui.ink.InkMenu
import com.eink.dashboard.ui.ink.InkMenuItem
import com.eink.dashboard.ui.ink.InkSegmented
import com.eink.dashboard.ui.ink.InkSpace
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
    val picker = rememberLauncherForActivityResult(OpenWritableDocument()) { uri ->
        uri?.let { scope.launch { module.connectFile(it) } }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(InkSpace.s3)) {
        InkMenu {
            InkMenuItem(
                label = settings.fileName ?: "No Markdown file selected",
                icon = InkIcons.Note,
                value = if (settings.fileName == null) null else if (settings.canWrite) "read/write" else "read-only",
                divider = false,
            )
        }
        lastRead?.let {
            InkHint("Local file updated ${LOCAL_TIME.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s2)) {
            InkButton(
                text = if (settings.fileUri == null) "Choose TaskForge.md" else "Change file",
                icon = InkIcons.Upload,
                onClick = { picker.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream")) },
            )
            if (settings.fileUri != null) {
                InkButton(
                    text = "Disconnect",
                    icon = InkIcons.Close,
                    variant = InkButtonVariant.Outline,
                    onClick = { scope.launch { module.disconnectFile() } },
                )
            }
        }

        InkLabel("View")
        InkChips {
            TaskForgeView.entries.forEach { view ->
                InkChip(view.label(), settings.view == view, outline = true, onClick = {
                    scope.launch { module.updateFilter(view, settings.selectedTags, settings.limit) }
                })
            }
        }

        InkLabel("Task limit")
        InkSegmented(
            options = TaskForgeSettings.ALLOWED_LIMITS.sorted(),
            selected = settings.limit,
            onSelect = { limit -> scope.launch { module.updateFilter(settings.view, settings.selectedTags, limit) } },
            label = { it.toString() },
        )

        val tags = board?.availableTags.orEmpty().sorted()
        if (tags.isNotEmpty()) {
            InkLabel("Tags", meta = "match any")
            InkChips {
                tags.forEach { tag ->
                    InkChip("#$tag", tag in settings.selectedTags, outline = true, onClick = {
                        val updated = if (tag in settings.selectedTags) settings.selectedTags - tag else settings.selectedTags + tag
                        scope.launch { module.updateFilter(settings.view, updated, settings.limit) }
                    })
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
