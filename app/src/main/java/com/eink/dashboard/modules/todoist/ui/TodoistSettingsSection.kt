package com.eink.dashboard.modules.todoist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.modules.todoist.TodoistModule
import com.eink.dashboard.modules.todoist.TodoistSettingsStore
import com.eink.dashboard.modules.todoist.model.TodoistView
import com.eink.dashboard.ui.ink.InkButton
import com.eink.dashboard.ui.ink.InkButtonVariant
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkLabel
import com.eink.dashboard.ui.ink.InkSegmented
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkTag
import com.eink.dashboard.ui.ink.InkTextField
import kotlinx.coroutines.launch

/**
 * Per-module settings surfaced in the shell's Settings screen (via `hasSettings`):
 * enter/replace the Todoist personal token (masked, verified on save), clear it, and
 * pick the Today / Upcoming view. The token is written straight to the Keystore-backed
 * store and never held in persisted UI state beyond the transient input field.
 */
@Composable
fun TodoistSettingsSection(
    module: TodoistModule,
    settingsStore: TodoistSettingsStore,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(
        initialValue = com.eink.dashboard.modules.todoist.TodoistSettings.DEFAULT,
    )
    val hasToken by module.hasToken.collectAsStateWithLifecycle()

    var tokenInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s3),
    ) {
        InkTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it; status = null },
            label = "Personal API token",
            placeholder = if (hasToken) "Configured — enter a new one to replace" else "Paste the token from Todoist settings",
            password = true,
            hint = status,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s2)) {
            InkButton(
                text = "Save & verify",
                icon = InkIcons.Check,
                onClick = {
                    val entered = tokenInput.trim()
                    if (entered.isEmpty()) {
                        status = "Enter a token first"
                        return@InkButton
                    }
                    scope.launch {
                        val error = module.saveAndVerifyToken(entered)
                        status = if (error == null) "Verified" else "Rejected — check the token"
                        // Never keep the raw token in UI state once handed to the store.
                        tokenInput = ""
                    }
                },
            )
            if (hasToken) {
                InkButton(
                    text = "Clear token",
                    icon = InkIcons.Trash,
                    variant = InkButtonVariant.Outline,
                    onClick = { scope.launch { module.clearToken(); status = "Cleared" } },
                )
            }
        }
        InkTag(if (hasToken) "Token configured" else "Token not set", solid = hasToken)

        InkLabel("View")
        InkSegmented(
            options = TodoistView.entries,
            selected = settings.view,
            onSelect = { view ->
                scope.launch {
                    settingsStore.setView(view)
                    module.refresh(com.eink.dashboard.dashboard.RefreshReason.SETTINGS_CHANGED)
                }
            },
            label = { it.label() },
            block = true,
        )
    }
}

private fun TodoistView.label(): String = when (this) {
    TodoistView.TODAY -> "Today"
    TodoistView.UPCOMING -> "Upcoming 7d"
}
